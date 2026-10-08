package dev.theone.schematic.modules;

import dev.theone.schematic.util.Mc;
import dev.theone.schematic.TheOneAddon;
import dev.theone.schematic.build.BuildTarget;
import dev.theone.schematic.build.BuildTarget.Block3;
import dev.theone.schematic.build.PlacementPlanner;
import dev.theone.schematic.build.StateMatch;
import dev.theone.schematic.format.Schematic;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.BlockItem;
import net.minecraft.item.HoeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class SchematicBuilder extends Module {
    public enum OriginMode {Player, Custom}

    public enum Rotation {
        None, CW90, CW180, CCW90;

        BlockRotation get() {
            return switch (this) {
                case None -> Mc.ROT_NONE;
                case CW90 -> Mc.ROT_CW_90;
                case CW180 -> Mc.ROT_180;
                case CCW90 -> Mc.ROT_CCW_90;
            };
        }
    }

    public enum Mirror {
        None, LeftRight, FrontBack;

        BlockMirror get() {
            return switch (this) {
                case None -> Mc.MIRROR_NONE;
                case LeftRight -> Mc.MIRROR_LEFT_RIGHT;
                case FrontBack -> Mc.MIRROR_FRONT_BACK;
            };
        }
    }

    public enum Order {BottomUp, Nearest}

    private static final int ROTATION_PRIORITY = 50;
    private static final int PENDING_TICKS = 8;
    private static final int MAX_FAILURES = 3;
    private static final int IGNORE_TICKS = 200;
    private static final int MAX_RENDER = 4096;
    private static final int IDLE_TICKS_BEFORE_MOVING = 4;

    private final SettingGroup sgSchematic = settings.getDefaultGroup();
    private final SettingGroup sgPlace = settings.createGroup("Placing");
    private final SettingGroup sgMove = settings.createGroup("Movement");
    private final SettingGroup sgRender = settings.createGroup("Render");

    // Schematic

    private final Setting<String> file = sgSchematic.add(new StringSetting.Builder()
        .name("file")
        .description("Schematic in .minecraft/schematics (.litematic, .schem or .nbt). Empty = newest file.")
        .defaultValue("")
        .build()
    );

    private final Setting<OriginMode> originMode = sgSchematic.add(new EnumSetting.Builder<OriginMode>()
        .name("origin")
        .description("Player: anchor the schematic's corner at your feet when enabled (then locked into the coordinates below). Custom: use the coordinates below.")
        .defaultValue(OriginMode.Player)
        .build()
    );

    private final Setting<Integer> originX = sgSchematic.add(new IntSetting.Builder()
        .name("x").description("Origin X.").defaultValue(0).noSlider()
        .visible(() -> originMode.get() == OriginMode.Custom).build());

    private final Setting<Integer> originY = sgSchematic.add(new IntSetting.Builder()
        .name("y").description("Origin Y.").defaultValue(64).noSlider()
        .visible(() -> originMode.get() == OriginMode.Custom).build());

    private final Setting<Integer> originZ = sgSchematic.add(new IntSetting.Builder()
        .name("z").description("Origin Z.").defaultValue(0).noSlider()
        .visible(() -> originMode.get() == OriginMode.Custom).build());

    private final Setting<Rotation> rotation = sgSchematic.add(new EnumSetting.Builder<Rotation>()
        .name("rotation").description("Rotates the schematic around its corner.").defaultValue(Rotation.None).build());

    private final Setting<Mirror> mirror = sgSchematic.add(new EnumSetting.Builder<Mirror>()
        .name("mirror").description("Mirrors the schematic.").defaultValue(Mirror.None).build());

    private final Setting<Boolean> disableWhenDone = sgSchematic.add(new BoolSetting.Builder()
        .name("disable-when-done").description("Turns the module off once every block is built.").defaultValue(true).build());

    // Placing

    private final Setting<Integer> blocksPerTick = sgPlace.add(new IntSetting.Builder()
        .name("blocks-per-tick").description("Maximum placements per tick.").defaultValue(3).range(1, 12).sliderRange(1, 12).build());

    private final Setting<Integer> delay = sgPlace.add(new IntSetting.Builder()
        .name("delay").description("Ticks to wait between placement rounds.").defaultValue(0).range(0, 20).sliderRange(0, 20).build());

    private final Setting<Double> range = sgPlace.add(new DoubleSetting.Builder()
        .name("range").description("Placement reach. Capped to your real block reach.").defaultValue(4.5).range(1, 6).sliderRange(1, 6).build());

    private final Setting<Order> order = sgPlace.add(new EnumSetting.Builder<Order>()
        .name("order").description("BottomUp finishes each layer before the next; Nearest grabs the closest block.").defaultValue(Order.BottomUp).build());

    private final Setting<Boolean> rotate = sgPlace.add(new BoolSetting.Builder()
        .name("rotate").description("Look at every block placed. Directional blocks always rotate.").defaultValue(true).build());

    private final Setting<Boolean> airPlace = sgPlace.add(new BoolSetting.Builder()
        .name("air-place").description("Place blocks with no solid neighbour. Most servers reject this.").defaultValue(false).build());

    private final Setting<Boolean> tillFarmland = sgPlace.add(new BoolSetting.Builder()
        .name("till-farmland").description("Builds farmland by placing dirt and using a hoe on it.").defaultValue(true).build());

    private final Setting<Boolean> placeFluids = sgPlace.add(new BoolSetting.Builder()
        .name("place-fluids").description("Places water/lava sources with buckets once every solid block is done.").defaultValue(true).build());

    private final Setting<Boolean> breakWrong = sgPlace.add(new BoolSetting.Builder()
        .name("break-wrong-blocks").description("Mines blocks that do not match the schematic.").defaultValue(false).build());

    private final Setting<Integer> hotbarSlot = sgPlace.add(new IntSetting.Builder()
        .name("hotbar-slot").description("Hotbar slot used for items pulled from the inventory when no slot is free.").defaultValue(9).range(1, 9).sliderRange(1, 9).build());

    // Movement

    private final Setting<Boolean> autoWalk = sgMove.add(new BoolSetting.Builder()
        .name("auto-walk").description("Walks to the next unfinished part of the build.").defaultValue(true).build());

    private final Setting<Boolean> autoPillar = sgMove.add(new BoolSetting.Builder()
        .name("pillar-up").description("Jumps and places under yourself to reach layers above your reach.").defaultValue(true).build());

    private final Setting<String> scaffoldBlocks = sgMove.add(new StringSetting.Builder()
        .name("scaffold-blocks").description("Comma separated items used to pillar where the schematic has no block.")
        .defaultValue("cobblestone,cobbled_deepslate,dirt,netherrack,stone").build());

    // Render

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render").description("Shows missing and wrong blocks.").defaultValue(true).build());

    private final Setting<Integer> renderRange = sgRender.add(new IntSetting.Builder()
        .name("render-range").description("Only render blocks this close.").defaultValue(24).range(4, 96).sliderRange(4, 96)
        .visible(render::get).build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode").description("How boxes are drawn.").defaultValue(ShapeMode.Both).visible(render::get).build());

    private final Setting<SettingColor> missingColor = sgRender.add(new ColorSetting.Builder()
        .name("missing-color").description("Blocks still to place.").defaultValue(new SettingColor(0, 224, 255, 40)).visible(render::get).build());

    private final Setting<SettingColor> wrongColor = sgRender.add(new ColorSetting.Builder()
        .name("wrong-color").description("Blocks that do not match.").defaultValue(new SettingColor(255, 70, 90, 70)).visible(render::get).build());

    // State

    private BuildTarget target;
    private int tick;
    private int delayTimer;
    private int remaining;
    private int solidRemaining;
    private final Map<Item, Integer> invCounts = new HashMap<>();
    private boolean hasHoe;
    private final Map<Long, Integer> pending = new HashMap<>();
    private final Map<Long, Integer> failures = new HashMap<>();
    private final Map<Long, Integer> ignoredUntil = new HashMap<>();
    private final Set<Long> scaffold = new LinkedHashSet<>();
    private final Set<Item> warnedMissing = new LinkedHashSet<>();
    private final List<BlockPos> renderMissing = new ArrayList<>();
    private final List<BlockPos> renderWrong = new ArrayList<>();
    private Map<Item, Integer> lastShortage = Map.of();

    // movement
    private boolean movingKeys;
    private int lastActionTick;
    private Vec3d stuckCheckPos;
    private int stuckTicks;
    private long goalKey = Long.MIN_VALUE;

    public SchematicBuilder() {
        super(TheOneAddon.CATEGORY, "schematic-builder",
            "Builds a schematic for you: orientation-aware placing, auto walking, pillaring up, farmland and water.");
    }

    // ---------------------------------------------------------------------------------------------
    // lifecycle

    @Override
    public void onActivate() {
        resetState();
        lastActionTick = 0;
        if (mc.player == null || mc.world == null) {
            error("%s", "Join a world first.");
            toggle();
            return;
        }
        Path path;
        Schematic schematic;
        try {
            path = resolveFile();
            schematic = Schematic.load(path);
        } catch (IOException e) {
            error("%s", "Could not load schematic: " + e.getMessage());
            toggle();
            return;
        }

        if (originMode.get() == OriginMode.Player) {
            BlockPos feet = mc.player.getBlockPos();
            originX.set(feet.getX());
            originY.set(feet.getY());
            originZ.set(feet.getZ());
            originMode.set(OriginMode.Custom);
            info("%s", "Origin locked at " + feet.getX() + " " + feet.getY() + " " + feet.getZ() + ". Set origin to Player to re-anchor.");
        }
        BlockPos origin = new BlockPos(originX.get(), originY.get(), originZ.get());
        target = BuildTarget.create(schematic, origin, rotation.get().get(), mirror.get().get());

        info("%s", "Loaded " + path.getFileName() + ": " + schematic.sizeX + "x" + schematic.sizeY + "x" + schematic.sizeZ
            + ", " + target.blocks.size() + " blocks to build.");
        if (!target.unknown.isEmpty()) warning("%s", "Unknown blocks for this version (skipped): " + target.unknown);
        if (!target.skipped.isEmpty()) warning("%s", "Blocks with no item (skipped): " + target.skipped);
        recount();
        refreshInventory();
        reportMaterials(true);
    }

    @Override
    public void onDeactivate() {
        releaseKeys();
        if (!scaffold.isEmpty()) {
            info("%s", "Scaffold blocks left in the build area: " + scaffold.size() + ". They are outlined in red while the module runs.");
        }
        target = null;
        renderMissing.clear();
        renderWrong.clear();
    }

    private void resetState() {
        tick = 0;
        delayTimer = 0;
        remaining = 0;
        solidRemaining = 0;
        pending.clear();
        failures.clear();
        ignoredUntil.clear();
        scaffold.clear();
        warnedMissing.clear();
        renderMissing.clear();
        renderWrong.clear();
        lastShortage = Map.of();
        stuckCheckPos = null;
        stuckTicks = 0;
        goalKey = Long.MIN_VALUE;
    }

    @Override
    public String getInfoString() {
        if (target == null) return null;
        return (target.blocks.size() - remaining) + "/" + target.blocks.size();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        if (isActive()) toggle();
    }

    private Path resolveFile() throws IOException {
        Path dir = FabricLoader.getInstance().getGameDir().resolve("schematics");
        Files.createDirectories(dir);
        String name = file.get().trim();
        if (name.isEmpty()) {
            try (Stream<Path> files = Files.list(dir)) {
                return files.filter(SchematicBuilder::isSchematicFile)
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .orElseThrow(() -> new IOException("no schematics in " + dir));
            }
        }
        Path direct = dir.resolve(name).normalize();
        if (!direct.startsWith(dir)) throw new IOException("file must be inside the schematics folder");
        if (Files.isRegularFile(direct)) return direct;
        for (String ext : new String[]{".litematic", ".schem", ".nbt", ".schematic"}) {
            Path p = dir.resolve(name + ext);
            if (Files.isRegularFile(p)) return p;
        }
        throw new IOException(name + " not found in " + dir);
    }

    private static boolean isSchematicFile(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.endsWith(".litematic") || n.endsWith(".schem") || n.endsWith(".nbt") || n.endsWith(".schematic");
    }

    // ---------------------------------------------------------------------------------------------
    // main loop

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (target == null || mc.player == null || mc.world == null) return;
        tick++;
        refreshInventory();
        pending.values().removeIf(t -> tick - t > PENDING_TICKS);
        ignoredUntil.values().removeIf(t -> t <= tick);

        if (tick % 20 == 0) {
            recount();
            if (remaining == 0) {
                info("%s", "Build complete: " + target.blocks.size() + " blocks.");
                if (disableWhenDone.get()) {
                    toggle();
                    return;
                }
            }
            if (tick % 200 == 0) reportMaterials(false);
        }

        double reach = Math.min(range.get(), mc.player.getBlockInteractionRange());
        Vec3d eye = mc.player.getEyePos();
        double scanRadius = Math.max(reach + 1.5, renderRange.get());

        // Collect what is around us in one pass.
        List<Block3> inReach = new ArrayList<>();
        renderMissing.clear();
        renderWrong.clear();
        boolean solidsLeft = solidRemaining > 0;
        for (Block3 b : target.blocks) {
            double d2 = eye.squaredDistanceTo(Vec3d.ofCenter(b.pos));
            if (d2 > scanRadius * scanRadius) continue;
            BlockState world = mc.world.getBlockState(b.pos);
            if (StateMatch.matches(world, b.state)) continue;
            if (isProgress(world, b) || world.isReplaceable()) {
                if (renderMissing.size() < MAX_RENDER) renderMissing.add(b.pos);
            } else if (renderWrong.size() < MAX_RENDER) {
                renderWrong.add(b.pos);
            }
            if (d2 <= (reach + 1) * (reach + 1) && !isIgnored(b)) inReach.add(b);
        }
        for (long key : scaffold) if (renderWrong.size() < MAX_RENDER) renderWrong.add(BlockPos.fromLong(key));

        if (breakWrong.get() && breakOneWrong(inReach)) return;

        if (delayTimer > 0) {
            delayTimer--;
        } else {
            int placed = placeRound(inReach, reach, solidsLeft);
            if (placed > 0) {
                delayTimer = delay.get();
                stuckTicks = 0;
                lastActionTick = tick;
                releaseKeys();
                return;
            }
        }
        // Nothing done for a few ticks and nothing waiting on the server: go somewhere useful.
        if (tick - lastActionTick > IDLE_TICKS_BEFORE_MOVING && pending.isEmpty()) move(reach, eye);
        else if (tick - lastActionTick <= IDLE_TICKS_BEFORE_MOVING) releaseKeys();
    }

    private boolean done(Block3 b) {
        return StateMatch.matches(mc.world.getBlockState(b.pos), b.state);
    }

    private boolean isIgnored(Block3 b) {
        return ignoredUntil.containsKey(b.key);
    }

    /** States on the way to the target that must not be treated as wrong blocks. */
    private boolean isProgress(BlockState world, Block3 b) {
        if (b.kind == BuildTarget.Kind.FARMLAND) return isTillable(world);
        if (StateMatch.isDoubleSlab(b.state)) return world.getBlock() == b.state.getBlock();
        return false;
    }

    private static boolean isTillable(BlockState s) {
        return s.getBlock() == Mc.DIRT || s.getBlock() == Mc.GRASS_BLOCK || s.getBlock() == Mc.DIRT_PATH;
    }

    /**
     * Counts what is left. {@link #solidRemaining} only counts solid blocks the builder can still
     * make (it has the item and the spot is free), so fluids are not held back forever by a block
     * you have none of or by a wrong block in the way.
     */
    private void recount() {
        int r = 0, solid = 0;
        for (Block3 b : target.blocks) {
            BlockState world = mc.world.getBlockState(b.pos);
            if (StateMatch.matches(world, b.state)) continue;
            r++;
            if (b.kind != BuildTarget.Kind.FLUID && hasMaterial(b) && (world.isReplaceable() || isProgress(world, b))) solid++;
        }
        remaining = r;
        solidRemaining = solid;
    }

    private void refreshInventory() {
        invCounts.clear();
        hasHoe = false;
        for (int i = 0; i < 36; i++) countStack(mc.player.getInventory().getStack(i));
        countStack(mc.player.getOffHandStack());
    }

    private void countStack(ItemStack s) {
        if (s.isEmpty()) return;
        invCounts.merge(s.getItem(), s.getCount(), Integer::sum);
        if (s.getItem() instanceof HoeItem) hasHoe = true;
    }

    // ---------------------------------------------------------------------------------------------
    // placing

    private int placeRound(List<Block3> inReach, double reach, boolean solidsLeft) {
        Vec3d eye = mc.player.getEyePos();
        if (order.get() == Order.Nearest) {
            inReach.sort(Comparator.comparingDouble(b -> eye.squaredDistanceTo(Vec3d.ofCenter(b.pos))));
        } else {
            inReach.sort(Comparator.<Block3>comparingInt(b -> b.pos.getY())
                .thenComparingDouble(b -> eye.squaredDistanceTo(Vec3d.ofCenter(b.pos))));
        }

        int placed = 0;
        for (Block3 b : inReach) {
            if (placed >= blocksPerTick.get()) break;
            if (pending.containsKey(b.key)) continue;
            BlockState world = mc.world.getBlockState(b.pos);
            if (StateMatch.matches(world, b.state)) continue;

            Action result = switch (b.kind) {
                case PLACE -> tryPlace(b, world, reach);
                case FARMLAND -> tryFarmland(b, world, reach);
                case FLUID -> solidsLeft || !placeFluids.get() ? Action.SKIP : tryFluid(b, world, reach);
            };
            if (result == Action.DONE) {
                pending.put(b.key, tick);
                placed++;
            } else if (result == Action.INVENTORY) {
                // An inventory move takes the rest of this tick.
                return Math.max(placed, 1);
            } else if (result == Action.FAILED) {
                fail(b);
            }
        }
        return placed;
    }

    private enum Action {DONE, SKIP, FAILED, INVENTORY}

    private void fail(Block3 b) {
        int f = failures.merge(b.key, 1, Integer::sum);
        if (f >= MAX_FAILURES) {
            failures.remove(b.key);
            ignoredUntil.put(b.key, tick + IGNORE_TICKS);
        }
    }

    private Action tryPlace(Block3 b, BlockState world, double reach) {
        BlockState want = b.state;
        boolean slabCompletion = false;
        if (StateMatch.isDoubleSlab(want)) {
            if (world.getBlock() == want.getBlock()) slabCompletion = true;
            else want = StateMatch.singleSlab(want);
        }
        if (!slabCompletion && !world.isReplaceable()) return Action.SKIP;

        Slot slot = obtain(stack -> stack.getItem() == b.item);
        if (slot == null) return missing(b.item);
        if (slot.movedToHotbar) return Action.INVENTORY;

        if (slabCompletion) {
            // Click into the existing slab from the free side; vanilla merges it into a double slab.
            boolean bottom = !"top".equals(StateMatch.slabType(world));
            Direction face = bottom ? Mc.UP : Mc.DOWN;
            Vec3d hit = new Vec3d(b.pos.getX() + 0.5, b.pos.getY() + 0.5, b.pos.getZ() + 0.5);
            if (mc.player.getEyePos().squaredDistanceTo(hit) > reach * reach) return Action.SKIP;
            interact(new BlockHitResult(hit, face, b.pos, false), slot, true, hit);
            return Action.DONE;
        }

        if (!mc.world.canPlace(want, b.pos, ShapeContext.absent())) return Action.SKIP;
        return placeWith(b.pos, want, slot, reach);
    }

    private Action tryFarmland(Block3 b, BlockState world, double reach) {
        if (!tillFarmland.get()) return Action.SKIP;
        if (isTillable(world)) {
            if (!mc.world.getBlockState(Mc.above(b.pos)).isAir()) return Action.SKIP;
            Slot hoe = obtain(stack -> stack.getItem() instanceof HoeItem);
            if (hoe == null) return missing(Mc.WOODEN_HOE);
            if (hoe.movedToHotbar) return Action.INVENTORY;
            Vec3d hit = new Vec3d(b.pos.getX() + 0.5, b.pos.getY() + 1.0, b.pos.getZ() + 0.5);
            if (mc.player.getEyePos().squaredDistanceTo(hit) > reach * reach) return Action.SKIP;
            interact(new BlockHitResult(hit, Mc.UP, b.pos, false), hoe, true, hit);
            return Action.DONE;
        }
        if (!world.isReplaceable()) return Action.SKIP;
        Slot dirt = obtain(stack -> stack.getItem() == b.item);
        if (dirt == null) return missing(b.item);
        if (dirt.movedToHotbar) return Action.INVENTORY;
        BlockState dirtState = Mc.DIRT.getDefaultState();
        if (!mc.world.canPlace(dirtState, b.pos, ShapeContext.absent())) return Action.SKIP;
        return placeWith(b.pos, dirtState, dirt, reach);
    }

    private Action placeWith(BlockPos pos, BlockState state, Slot slot, double reach) {
        PlacementPlanner.Result r = PlacementPlanner.plan(mc, pos, state, slot.stack, reach, airPlace.get());
        return switch (r.status()) {
            case OK -> {
                interact(r.plan(), slot);
                yield Action.DONE;
            }
            case NO_MATCH -> Action.FAILED;
            case NO_SUPPORT, OUT_OF_RANGE -> Action.SKIP;
        };
    }

    private Action tryFluid(Block3 b, BlockState world, double reach) {
        if (!world.isReplaceable()) return Action.SKIP;
        // Aim at the face of a solid neighbour that touches the target: the bucket's ray hits that
        // face and the fluid goes into the target position next to it. Prefer the floor.
        Direction[] order = {Mc.DOWN, Mc.NORTH, Mc.SOUTH, Mc.EAST, Mc.WEST};
        Vec3d eye = mc.player.getEyePos();
        for (Direction d : order) {
            BlockPos n = Mc.offset(b.pos, d);
            BlockState ns = mc.world.getBlockState(n);
            if (ns.isAir() || ns.isReplaceable() || BlockUtils.isClickable(ns.getBlock())) continue;
            Vec3d aim = new Vec3d(b.pos.getX() + 0.5 + d.getOffsetX() * 0.5, b.pos.getY() + 0.5 + d.getOffsetY() * 0.5,
                b.pos.getZ() + 0.5 + d.getOffsetZ() * 0.5);
            if (eye.squaredDistanceTo(aim) > reach * reach) continue;
            // The ray must enter the target cell before reaching the face, so the eye has to be on the target's side.
            double side = (eye.x - aim.x) * -d.getOffsetX() + (eye.y - aim.y) * -d.getOffsetY() + (eye.z - aim.z) * -d.getOffsetZ();
            if (side <= 0.05) continue;

            Slot bucket = obtain(stack -> stack.getItem() == b.item);
            if (bucket == null) return missing(b.item);
            if (bucket.movedToHotbar) return Action.INVENTORY;
            float yaw = (float) PlacementPlanner.yawTo(eye, aim);
            float pitch = (float) PlacementPlanner.pitchTo(eye, aim);
            Rotations.rotate(yaw, pitch, ROTATION_PRIORITY, true, () -> {
                if (!bucket.offhand) InvUtils.swap(bucket.slot, true);
                mc.interactionManager.interactItem(mc.player, bucket.offhand ? Mc.OFF_HAND : Mc.MAIN_HAND);
                if (!bucket.offhand) InvUtils.swapBack();
            });
            return Action.DONE;
        }
        return Action.FAILED;
    }

    private void interact(PlacementPlanner.Plan plan, Slot slot) {
        if (plan.needsRotation() || rotate.get()) {
            Rotations.rotate(plan.yaw(), plan.pitch(), ROTATION_PRIORITY, true, () -> click(plan.hit(), slot));
        } else {
            click(plan.hit(), slot);
        }
    }

    private void interact(BlockHitResult hit, Slot slot, boolean alwaysRotate, Vec3d lookAt) {
        if (alwaysRotate || rotate.get()) {
            Vec3d eye = mc.player.getEyePos();
            Rotations.rotate(PlacementPlanner.yawTo(eye, lookAt), PlacementPlanner.pitchTo(eye, lookAt), ROTATION_PRIORITY, true,
                () -> click(hit, slot));
        } else {
            click(hit, slot);
        }
    }

    private void click(BlockHitResult hit, Slot slot) {
        if (slot.offhand) {
            BlockUtils.interact(hit, Mc.OFF_HAND, true);
            return;
        }
        InvUtils.swap(slot.slot, true);
        BlockUtils.interact(hit, Mc.MAIN_HAND, true);
        InvUtils.swapBack();
    }

    private boolean breakOneWrong(List<Block3> inReach) {
        for (Block3 b : inReach) {
            BlockState world = mc.world.getBlockState(b.pos);
            if (world.isReplaceable() || StateMatch.matches(world, b.state) || isProgress(world, b)) continue;
            if (!BlockUtils.canBreak(b.pos, world)) continue;
            if (BlockUtils.breakBlock(b.pos, true)) {
                releaseKeys();
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // inventory

    private static final class Slot {
        final int slot;
        final boolean offhand;
        final boolean movedToHotbar;
        final ItemStack stack;

        Slot(int slot, boolean offhand, boolean movedToHotbar, ItemStack stack) {
            this.slot = slot;
            this.offhand = offhand;
            this.movedToHotbar = movedToHotbar;
            this.stack = stack;
        }
    }

    /** Finds an item in the hotbar/offhand, or moves it there from the inventory. Null when missing. */
    private Slot obtain(Predicate<ItemStack> wanted) {
        FindItemResult hotbar = InvUtils.findInHotbar(wanted);
        if (hotbar.found()) {
            if (hotbar.isOffhand()) return new Slot(-1, true, false, mc.player.getOffHandStack());
            return new Slot(hotbar.slot(), false, false, mc.player.getInventory().getStack(hotbar.slot()));
        }
        FindItemResult any = InvUtils.find(wanted);
        if (!any.found() || !any.isMain()) return null;
        int dest = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isEmpty()) {
                dest = i;
                break;
            }
        }
        if (dest < 0) dest = hotbarSlot.get() - 1;
        InvUtils.move().from(any.slot()).toHotbar(dest);
        return new Slot(dest, false, true, ItemStack.EMPTY);
    }

    private Action missing(Item item) {
        if (warnedMissing.add(item)) warning("%s", "Out of " + itemName(item) + ".");
        return Action.SKIP;
    }

    private static String itemName(Item item) {
        Identifier id = Registries.ITEM.getId(item);
        return id.getPath();
    }

    private int countInInventory(Item item) {
        return invCounts.getOrDefault(item, 0);
    }

    /** Prints what is still needed versus what the inventory holds. */
    private void reportMaterials(boolean always) {
        Map<Item, Integer> needed = new LinkedHashMap<>();
        for (Block3 b : target.blocks) {
            if (done(b)) continue;
            needed.merge(b.item, 1, Integer::sum);
        }
        Map<Item, Integer> shortage = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> e : needed.entrySet()) {
            int have = countInInventory(e.getKey());
            if (have < e.getValue()) shortage.put(e.getKey(), e.getValue() - have);
        }
        if (!always && shortage.equals(lastShortage)) return;
        lastShortage = shortage;
        warnedMissing.removeIf(item -> countInInventory(item) > 0);
        if (shortage.isEmpty()) {
            if (always) info("%s", "All materials are in your inventory.");
            return;
        }
        StringBuilder sb = new StringBuilder("Still need: ");
        int shown = 0;
        for (Map.Entry<Item, Integer> e : shortage.entrySet()) {
            if (shown++ == 12) {
                sb.append("and ").append(shortage.size() - 12).append(" more");
                break;
            }
            sb.append(e.getValue()).append("x ").append(itemName(e.getKey())).append(", ");
        }
        info("%s", sb.toString());
    }

    private Item[] scaffoldItems() {
        List<Item> items = new ArrayList<>();
        for (String s : scaffoldBlocks.get().split(",")) {
            Identifier id = Identifier.tryParse(s.trim().toLowerCase(Locale.ROOT));
            if (id != null && Registries.ITEM.containsId(id)) items.add(Registries.ITEM.get(id));
        }
        return items.toArray(new Item[0]);
    }

    // ---------------------------------------------------------------------------------------------
    // movement

    private boolean hasMaterial(Block3 b) {
        if (b.kind == BuildTarget.Kind.FARMLAND && isTillable(mc.world.getBlockState(b.pos))) {
            return hasHoe;
        }
        return countInInventory(b.item) > 0;
    }

    private void move(double reach, Vec3d eye) {
        if (!autoWalk.get() && !autoPillar.get()) return;
        Block3 goal = pickGoal(eye);
        if (goal == null) {
            releaseKeys();
            return;
        }

        Vec3d feet = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        double dx = goal.pos.getX() + 0.5 - feet.x;
        double dz = goal.pos.getZ() + 0.5 - feet.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double above = goal.pos.getY() + 0.5 - eye.y;

        // Too high to reach from the ground nearby: build a pillar under ourselves.
        if (autoPillar.get() && horizontal <= 2.5 && above > reach - 1.0) {
            pillar(reach);
            return;
        }
        if (!autoWalk.get()) {
            releaseKeys();
            return;
        }

        // Standing in the target's cell blocks the placement: step back out of it.
        boolean tooClose = horizontal < 1.2 && Math.abs(goal.pos.getY() - mc.player.getBlockPos().getY()) <= 1;
        if (!tooClose && horizontal <= Math.max(1.5, reach - 1.5)) {
            // In position but still idle (e.g. a mob stands in the spot): give up on it for a while.
            releaseKeys();
            checkStuck(goal);
            return;
        }
        float yaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90f;
        if (tooClose) yaw += 180f;
        mc.player.setYaw(yaw);
        mc.options.forwardKey.setPressed(true);
        // Step up ledges and keep momentum when bumping into a block.
        mc.options.jumpKey.setPressed(mc.player.horizontalCollision && mc.player.isOnGround());
        movingKeys = true;
        checkStuck(goal);
    }

    private void pillar(double reach) {
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(true);
        movingKeys = true;
        if (mc.player.isOnGround()) return;

        BlockPos below = Mc.below(mc.player.getBlockPos());
        if (!mc.world.getBlockState(below).isReplaceable()) return;
        if (pending.containsKey(below.asLong())) return;

        // Use the schematic's own block when the pillar runs through the build; scaffold otherwise.
        Block3 inBuild = target.byPos.get(below.asLong());
        BlockState state;
        Slot slot;
        boolean isScaffold;
        if (inBuild != null && inBuild.kind == BuildTarget.Kind.PLACE && !StateMatch.isOrientable(inBuild.state)
            && (slot = obtain(stack -> stack.getItem() == inBuild.item)) != null) {
            state = inBuild.state;
            isScaffold = false;
        } else {
            Item[] items = scaffoldItems();
            slot = obtain(stack -> {
                for (Item i : items) if (stack.getItem() == i) return true;
                return false;
            });
            if (slot == null) {
                if (warnedMissing.add(Mc.COBBLESTONE)) warning("%s", "No scaffold blocks to pillar with.");
                return;
            }
            state = slot.movedToHotbar || !(slot.stack.getItem() instanceof BlockItem bi) ? null : bi.getBlock().getDefaultState();
            isScaffold = true;
        }
        if (slot.movedToHotbar || state == null) return;

        PlacementPlanner.Result r = PlacementPlanner.plan(mc, below, state, slot.stack, reach, false);
        if (r.plan() == null) return;
        interact(r.plan(), slot);
        pending.put(below.asLong(), tick);
        if (isScaffold && (inBuild == null)) scaffold.add(below.asLong());
    }

    private Block3 pickGoal(Vec3d eye) {
        Block3 best = null;
        double bestScore = Double.MAX_VALUE;
        int lowestY = Integer.MAX_VALUE;
        // Bottom-up: work on the lowest unfinished layer that we can actually make progress on.
        for (Block3 b : target.blocks) {
            if (b.pos.getY() > lowestY) break;
            if (isIgnored(b) || !hasMaterial(b) || !actionable(b)) continue;
            if (b.kind == BuildTarget.Kind.FLUID && !placeFluids.get()) continue;
            if (b.kind == BuildTarget.Kind.FARMLAND && !tillFarmland.get()) continue;
            lowestY = Math.min(lowestY, b.pos.getY());
            double score = eye.squaredDistanceTo(Vec3d.ofCenter(b.pos));
            if (score < bestScore) {
                bestScore = score;
                best = b;
            }
        }
        // Fluids are sorted after every solid block, so if only fluids remain the loop above breaks
        // early on their first layer; scan them separately.
        if (best == null) {
            for (Block3 b : target.blocks) {
                if (b.kind != BuildTarget.Kind.FLUID || !placeFluids.get() || isIgnored(b) || !hasMaterial(b) || !actionable(b)) continue;
                double score = eye.squaredDistanceTo(Vec3d.ofCenter(b.pos));
                if (score < bestScore) {
                    bestScore = score;
                    best = b;
                }
            }
        }
        return best;
    }

    /** Not built yet, and something can be done about it right now (free spot with support, or progress state). */
    private boolean actionable(Block3 b) {
        BlockState world = mc.world.getBlockState(b.pos);
        if (StateMatch.matches(world, b.state)) return false;
        if (isProgress(world, b)) return true;
        if (!world.isReplaceable()) return breakWrong.get();
        return airPlace.get() || PlacementPlanner.hasSupport(mc, b.pos);
    }

    private void checkStuck(Block3 goal) {
        Vec3d now = new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ());
        if (goal.key != goalKey) {
            goalKey = goal.key;
            stuckCheckPos = now;
            stuckTicks = 0;
            return;
        }
        if (++stuckTicks < 60) return;
        if (stuckCheckPos != null && stuckCheckPos.squaredDistanceTo(now) < 0.25) {
            // Could not get closer for 3 seconds: try another part of the build for a while.
            ignoredUntil.put(goal.key, tick + IGNORE_TICKS);
            releaseKeys();
        }
        stuckCheckPos = now;
        stuckTicks = 0;
    }

    private void releaseKeys() {
        if (!movingKeys || mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
        movingKeys = false;
    }

    // ---------------------------------------------------------------------------------------------
    // render

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || target == null) return;
        for (BlockPos pos : renderMissing) event.renderer.box(pos, missingColor.get(), missingColor.get(), shapeMode.get(), 0);
        for (BlockPos pos : renderWrong) event.renderer.box(pos, wrongColor.get(), wrongColor.get(), shapeMode.get(), 0);
    }
}
