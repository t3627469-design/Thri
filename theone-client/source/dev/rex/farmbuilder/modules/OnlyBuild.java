package dev.rex.farmbuilder.modules;

import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.pathing.goals.GoalNear;
import baritone.api.schematic.IStaticSchematic;
import baritone.api.schematic.format.ISchematicFormat;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent.Post;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.class_1661;
import net.minecraft.class_1792;
import net.minecraft.class_1799;
import net.minecraft.class_1802;
import net.minecraft.class_239;
import net.minecraft.class_2350;
import net.minecraft.class_2338;
import net.minecraft.class_2680;
import net.minecraft.class_2741;
import net.minecraft.class_2742;
import net.minecraft.class_2756;
import net.minecraft.class_3965;

/**
 * Only Build: places a schematic with what is already in the inventory. It never shops, orders or opens chests.
 * Blocks whose material is missing are skipped; when nothing else can be placed it stops and lists what is missing.
 */
public class OnlyBuild extends Module {
   private static final int MAX_ATTEMPTS = 6;
   private static final String BUNDLED = "sweet_berries.litematic";
   private static final File PLACE_FILE = new File(MeteorClient.FOLDER, "human-builder-place.txt");
   private static final File JOB_FILE = new File(MeteorClient.FOLDER, "only-build-job.txt");
   private static final long JOB_MAX_AGE_MS = 20L * 3600L * 1000L;

   private enum Phase { BUILD, HOTBAR }

   private final SettingGroup sg = this.settings.getDefaultGroup();
   private final Setting<String> fileName = this.sg.add(new StringSetting.Builder().name("file-name")
      .description("Schematic inside the .minecraft/schematics folder. Empty = the built-in Sweet Berries farm.")
      .defaultValue("").build());
   private final Setting<Boolean> atLooking = this.sg.add(new BoolSetting.Builder().name("origin-at-looking")
      .description("Anchor the schematic's corner on top of the block you are aiming at when you turn the module on. Off uses the block you stand on.")
      .defaultValue(true).build());
   private final Setting<Integer> speed = this.sg.add(new IntSetting.Builder().name("speed")
      .description("How fast it aims and places: 1 careful, 3 normal, 5 as fast as it goes.").defaultValue(4).range(1, 5).sliderRange(1, 5).build());
   private final Setting<Double> reach = this.sg.add(new DoubleSetting.Builder().name("reach")
      .description("How far from your eyes a block may be placed.").defaultValue(4.0).range(2.0, 4.5).sliderRange(2.5, 4.5).build());
   private final Setting<Boolean> stayOn = this.sg.add(new BoolSetting.Builder().name("stay-on-when-out")
      .description("When nothing more can be placed with what you carry, keep running and wait for materials instead of turning itself off.").defaultValue(true).build());
   private final Setting<Boolean> creativeSupply = this.sg.add(new BoolSetting.Builder().name("creative-supply")
      .description("In creative mode, give yourself any material the build needs, automatically, as it needs it. Does nothing in survival.").defaultValue(true).build());

   private final Setting<Boolean> pillarUp = this.sg.add(new BoolSetting.Builder().name("pillar-up")
      .description("When a layer is too high to reach and there is nothing to stand on, build a pillar of spare blocks (cobblestone, dirt...) beside the build, and break it again once the layer is done.").defaultValue(true).build());

   private final SettingGroup sgPanel = this.settings.createGroup("Dashboard");
   private final Setting<Boolean> useLitematica = this.sgPanel.add(new BoolSetting.Builder().name("litematica-ghost")
      .description("Show the build as Litematica ghost blocks (needs the Litematica and MaLiLib mods). Off or missing: a simple green outline.").defaultValue(true).build());
   private final Setting<Boolean> showPanel = this.sgPanel.add(new BoolSetting.Builder().name("show-dashboard")
      .description("Draw the TheOne status dashboard on screen while this module is on.").defaultValue(true).build());
   private final Setting<Boolean> compactPanel = this.sgPanel.add(new BoolSetting.Builder().name("compact-dashboard")
      .description("Only the header, one activity line and the progress bar.").defaultValue(false).build());
   private final Setting<Integer> panelX = this.sgPanel.add(new IntSetting.Builder().name("dashboard-x")
      .description("Distance from the left edge of the screen.").defaultValue(6).range(0, 2000).sliderRange(0, 600).build());
   private final Setting<Integer> panelY = this.sgPanel.add(new IntSetting.Builder().name("dashboard-y")
      .description("Distance from the top edge of the screen.").defaultValue(6).range(0, 2000).sliderRange(0, 400).build());

   private final HudPanel hud = new HudPanel();
   private HudPanel.Model hudModel;
   private int hudTick = -100;

   private final Random random = new Random();
   private final Map<class_2338, class_2680> wanted = new HashMap<>();
   private final Map<class_2338, Integer> attempts = new HashMap<>();
   private final Map<class_2338, Integer> attemptTick = new HashMap<>();
   private int noneTicks;
   private int pendLayer = Integer.MIN_VALUE;
   private int pendSince;
   private class_2338 walkGoal;
   private final Reposition reposition = new Reposition();
   private class_2338 standSpot;
   private final Pillar pillar = new Pillar();
   private class_1792 pillarItem;
   private java.util.HashSet<Long> footprint;
   private class_2338 pillarGoal;
   private class_2338 standFor;
   private int climbTicks;
   private int walkTicks;
   private final Map<class_1792, Integer> required = new LinkedHashMap<>();
   private final Map<class_1792, Integer> held = new HashMap<>();
   private Phase phase = Phase.BUILD;
   private class_2338 origin;
   private File schematicFile;
   private boolean ghostHinted;
   private boolean failed;
   private int total;
   private int skipped;
   private int ticks;
   private int wait;
   private int delay;
   private int countTick = -100;
   private final Approach approach = new Approach();
   private boolean halted;
   private boolean haltError;
   private String haltMessage = "";
   private int errors;
   private int lastError;
   private boolean creativeBroken;
   private boolean creativeNow;
   private boolean creativeAnnounced;
   private String doing = "";
   private long lastWalkInfo;
   private int hotbarFrom = -1;
   private int hotbarTo = -1;
   private final Planner planner = new Planner(this.wanted);
   private int shownLayer = Integer.MIN_VALUE;
   private class_2338 aimTarget;
   private int aimTicks;
   private class_2338 stuckAt;
   private int stuckTicks;
   private int asideTries;
   private boolean started;
   private boolean ghostOwned;
   private int hotbarStep;
   private int missingTypes;
   private long lastNag;
   private long startedAt;

   public OnlyBuild() {
      super(dev.rex.stealth.Spooky.category(), "only-build",
         "TheOne Client: builds the schematic using only what is already in your inventory. No shopping, no chests.");
   }

   // ---------------------------------------------------------------- start / stop

   @Override
   public void onActivate() {
      this.failed = false;
      this.halted = false;
      this.haltError = false;
      this.haltMessage = "";
      this.errors = 0;
      this.phase = Phase.BUILD;
      this.wanted.clear();
      this.attempts.clear();
      this.attemptTick.clear();
      this.required.clear();
      this.held.clear();
      this.wait = 0;
      this.delay = 0;
      this.skipped = 0;
      this.countTick = -100;
      this.planner.clear();
      this.shownLayer = Integer.MIN_VALUE;
      this.stuckAt = null;
      this.stuckTicks = 0;
      this.asideTries = 0;
      this.climbTicks = 0;
      this.walkGoal = null;
      this.standSpot = null;
      this.standFor = null;
      this.pillar.reset();
      this.footprint = null;
      this.pillarGoal = null;
      this.reposition.reset();
      this.doing = "";
      this.creativeBroken = false;
      this.creativeNow = false;
      this.creativeAnnounced = false;
      this.approach.reset();
      this.startedAt = System.currentTimeMillis();
      if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
         // Not in a world yet: Meteor calls onActivate again when you join, so just wait.
         this.halted = true;
         this.haltMessage = "Join a world to start.";
         return;
      }
      try {
         // Whichever builder was started last wins: switch the others off instead of refusing to start.
         this.switchOff(FarmBuilder.class, "farm-builder");
         this.switchOff(HumanBuilder.class, "human-builder");
         Approach.lockSettings();
         this.started = true;
         this.load();
         this.saveJob();
         if (this.planner.isEmpty()) {
            this.halt("Nothing to build: the schematic has no placeable blocks.", true);
            return;
         }
         this.total = this.planner.size();
         this.showGhost();
         this.info("Building %d blocks with what you carry.", this.total);
         this.materialCheck();
         IBaritone b = baritone();
         if (b != null) b.getPathingBehavior().cancelEverything();
      } catch (Throwable t) {
         this.fail(t);
      }
   }

   /** Turns another builder off if it is running, so this one can start. */
   private void switchOff(Class<? extends Module> other, String label) {
      try {
         Module m = (Module) Modules.get().get(other);
         if (m != null && m.isActive()) {
            m.toggle();
            this.info("Turned %s off so only-build can run.", label);
         }
      } catch (Throwable t) {
         System.out.println("[OnlyBuild] couldn't switch " + label + " off: " + t);
      }
   }

   /**
    * Stops building but leaves the module switched ON, with the reason shown on the dashboard and in chat,
    * so it never just silently turns itself off.
    */
   private void halt(String message, boolean error) {
      if (this.halted) return;
      this.halted = true;
      this.haltError = error;
      this.haltMessage = message;
      this.doing = "";
      this.approach.stop();
      Approach.unlockSettings();
      System.out.println("[OnlyBuild] " + (error ? "stopped (error): " : "stopped: ") + message);
      try {
         IBaritone b = baritone();
         if (b != null) b.getPathingBehavior().cancelEverything();
      } catch (Throwable ignored) {
      }
      if (error) this.error("%s", message);
      else this.info("%s", message);
   }

   @Override
   public void onDeactivate() {
      this.approach.stop();
      Approach.unlockSettings();
      if (!this.started) return;
      this.started = false;
      try {
         IBaritone b = baritone();
         if (b != null) b.getPathingBehavior().cancelEverything();
      } catch (Throwable ignored) {
      }
      this.hideGhost();
   }

   private void fail(Throwable t) {
      this.failed = true;
      System.out.println("[OnlyBuild] failed: " + t);
      t.printStackTrace();
      this.halt("Error: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getClass().getSimpleName() + ": " + t.getMessage()), true);
   }

   // ---------------------------------------------------------------- schematic

   private void load() throws Exception {
      File dir = new File(this.mc.field_1697, "schematics");
      Files.createDirectories(dir.toPath());
      String name = this.fileName.get().trim();
      File file;
      if (name.isEmpty() || name.equalsIgnoreCase(BUNDLED)) {
         file = FarmFiles.resolve(dir, "farm-builder-" + BUNDLED);
         if (!file.isFile()) {
            try (InputStream in = OnlyBuild.class.getResourceAsStream("/farms/" + BUNDLED)) {
               if (in == null) throw new IllegalStateException("The built-in farm is missing from the jar.");
               Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
         }
      } else {
         file = FarmFiles.resolve(dir, name);
         if (!file.isFile()) throw new IllegalStateException("No file at " + file.getPath());
      }
      this.schematicFile = file;
      Optional<ISchematicFormat> format = BaritoneAPI.getProvider().getSchematicSystem().getByFile(file);
      if (format.isEmpty()) throw new IllegalStateException("Baritone can't read " + file.getName() + ".");
      IStaticSchematic s;
      try (InputStream in = new FileInputStream(file)) {
         s = format.get().parse(in);
      }
      if (s == null || s.widthX() <= 0 || s.heightY() <= 0 || s.lengthZ() <= 0
         || (long) s.widthX() * s.heightY() * s.lengthZ() > 4000000L)
         throw new IllegalStateException("Schematic is empty or larger than 4 million blocks.");

      this.origin = this.chooseOrigin();
      this.planner.clear();
      this.wanted.clear();
      for (int y = 0; y < s.heightY(); y++) {
         for (int z = 0; z < s.lengthZ(); z++) {
            for (int x = 0; x < s.widthX(); x++) {
               class_2680 want = s.getDirect(x, y, z);
               if (!placeable(want)) continue;
               class_2338 pos = this.origin.method_10069(x, y, z);
               this.wanted.put(pos, want);
               this.planner.add(pos);
            }
         }
      }
   }

   private String currentDim() {
      return this.mc.field_1687 == null ? null : this.mc.field_1687.method_27983().method_29177().toString();
   }

   private String currentServer() {
      net.minecraft.class_642 server = this.mc.method_1558();
      return server == null || server.field_3761 == null ? "" : server.field_3761.trim().toLowerCase(Locale.ROOT);
   }

   private FarmLocation readSaved(File file) {
      try {
         if (!file.isFile()) return null;
         FarmLocation spot = FarmLocation.read(file.toPath());
         return spot.matches(this.currentDim(), this.currentServer()) ? spot : null;
      } catch (Exception e) {
         return null;
      }
   }

   /** Remembers the corner so a rejoin carries on at the same spot instead of re-aiming. */
   private void saveJob() {
      try {
         if (this.origin != null) new FarmLocation(this.origin, this.currentDim(), this.currentServer()).save(JOB_FILE.toPath());
      } catch (Exception ignored) {
      }
   }

   private void clearJob() {
      if (JOB_FILE.exists()) JOB_FILE.delete();
   }

   private class_2338 chooseOrigin() {
      FarmLocation job = JOB_FILE.isFile() && System.currentTimeMillis() - JOB_FILE.lastModified() < JOB_MAX_AGE_MS ? this.readSaved(JOB_FILE) : null;
      if (job != null) return job.origin();
      FarmLocation saved = this.readSaved(PLACE_FILE);
      if (saved != null) return saved.origin();
      if (this.atLooking.get()) {
         class_239 hit = this.mc.field_1724.method_5745(6.0, 1.0F, false);
         if (hit instanceof class_3965 block && hit.method_17783() == class_239.class_240.field_1332)
            return block.method_17777().method_10084();
      }
      return this.mc.field_1724.method_24515();
   }

   private static boolean placeable(class_2680 s) {
      if (s == null || s.method_26215() || !s.method_26227().method_15769()) return false;
      if (s.method_28498(class_2741.field_12533) && s.method_11654(class_2741.field_12533) == class_2756.field_12609) return false;
      if (s.method_28498(class_2741.field_12483) && s.method_11654(class_2741.field_12483) == class_2742.field_12560) return false;
      return s.method_26204().method_8389() != class_1802.field_8162;
   }

   private static String name(class_1792 item) {
      return item.method_63680().getString();
   }

   private static String where(class_2338 p) {
      return p.method_10263() + " " + p.method_10264() + " " + p.method_10260();
   }

   // ---------------------------------------------------------------- ghost blocks

   private void showGhost() {
      if (!this.useLitematica.get() || this.schematicFile == null || this.origin == null) {
         HumanBuilderPreview.suppress = false;
         return;
      }
      try {
         String problem = LitematicaBridge.show(this.schematicFile, this.origin);
         if (problem == null) {
            this.ghostOwned = true;
            this.shownLayer = Integer.MIN_VALUE;
            int low = this.planner.lowestLayerY();
            if (low != Integer.MIN_VALUE) {
               this.shownLayer = low;
               try {
                  LitematicaBridge.showLayer(low);
               } catch (Throwable ignored) {
               }
            }
            HumanBuilderPreview.suppress = true;
            this.info("Showing the build with Litematica ghost blocks.");
         } else {
            HumanBuilderPreview.suppress = false;
            this.warning("%s. Using the simple outline.", problem);
         }
      } catch (Throwable t) {
         HumanBuilderPreview.suppress = false;
         if (!this.ghostHinted) {
            this.ghostHinted = true;
            this.info("Put Litematica and MaLiLib in your mods folder to see the build as real ghost blocks. Using the simple outline.");
         }
      }
   }

   private void hideGhost() {
      if (!this.ghostOwned) return;
      this.ghostOwned = false;
      HumanBuilderPreview.suppress = false;
      try {
         LitematicaBridge.hide();
      } catch (Throwable ignored) {
      }
   }

   // ---------------------------------------------------------------- inventory

   /** Items and counts the player carries right now, refreshed at most every ten ticks. */
   private Map<class_1792, Integer> inventory() {
      return this.inventory(false);
   }

   private Map<class_1792, Integer> inventory(boolean force) {
      if (!force && this.ticks - this.countTick < 10) return this.held;
      this.countTick = this.ticks;
      this.held.clear();
      class_1661 inv = this.mc.field_1724.method_31548();
      for (int i = 0; i < 36; i++) {
         class_1799 st = inv.method_5438(i);
         if (!st.method_7960()) this.held.merge(st.method_7909(), st.method_7947(), Integer::sum);
      }
      return this.held;
   }

   /** Prints how much of the build the inventory covers and what is missing. */
   private void materialCheck() {
      this.required.clear();
      this.required.putAll(this.planner.remainingItems());
      Map<class_1792, Integer> have = this.inventory(true);
      StringBuilder missing = new StringBuilder();
      int covered = 0;
      int needed = 0;
      this.missingTypes = 0;
      for (Map.Entry<class_1792, Integer> e : this.required.entrySet()) {
         int h = have.getOrDefault(e.getKey(), 0);
         needed += e.getValue();
         covered += Math.min(h, e.getValue());
         if (h < e.getValue()) {
            this.missingTypes++;
            if (this.missingTypes <= 8) missing.append(e.getValue() - h).append("x ").append(name(e.getKey())).append(", ");
         }
      }
      if (needed == 0) return;
      this.info("Your inventory covers %d of %d blocks (%d%%).", covered, needed, covered * 100 / needed);
      if (missing.length() > 0)
         this.warning("Missing: %s%s", missing.substring(0, missing.length() - 2), this.missingTypes > 8 ? " and " + (this.missingTypes - 8) + " more kinds" : "");
   }

   // ---------------------------------------------------------------- tick

   @EventHandler
   private void onTick(Post event) {
      if (this.failed || this.halted) return;
      try {
         this.tick();
      } catch (Throwable t) {
         if (this.ticks - this.lastError > 6000) this.errors = 0;
         this.lastError = this.ticks;
         System.out.println("[OnlyBuild] hit " + t);
         t.printStackTrace();
         if (++this.errors >= 5) {
            this.fail(t);
            return;
         }
         this.warning("Hit a snag (%s). Retrying.", t.getClass().getSimpleName());
         this.phase = Phase.BUILD;
         this.wait = 40;
      }
   }

   private void tick() {
      if (this.mc.field_1724 == null || this.mc.field_1687 == null) return;
      this.ticks++;
      if (this.ticks % 40 == 0) Approach.lockSettings();
      if (this.ticks % 20 == 0 && Modules.get().isActive(FarmBuilder.class)) {
         this.halt("farm-builder was started; stopped so they don't fight over Baritone. Turn only-build off and on to start again.", false);
         return;
      }
      if (this.wait > 0) {
         this.wait--;
         return;
      }
      if (this.phase == Phase.HOTBAR) this.tickHotbar();
      else this.tickBuild();
   }

   private void pause(int n) {
      this.wait = n + this.random.nextInt(3);
   }

   private void tickHotbar() {
      switch (this.hotbarStep) {
         case 0 -> {
            if (!this.mc.field_1724.field_7498.method_34255().method_7960()) return;
            if (Look.openInventoryIfClosed()) {
               this.hotbarStep = 1;
               this.pause(4);
            }
         }
         case 1 -> {
            Look.clickSwapToHotbar(this.hotbarFrom, this.hotbarTo >= 0 ? this.hotbarTo : this.mc.field_1724.method_31548().method_67532());
            this.hotbarStep = 2;
            this.pause(4);
         }
         default -> {
            Look.closeScreen();
            this.hotbarFrom = -1;
            this.countTick = -100;
            this.phase = Phase.BUILD;
            this.pause(3);
         }
      }
   }

   private void tickBuild() {
      if (this.mc.field_1755 != null) return;
      if (this.ticks % 5 == 0) this.planner.sweep();
      if (this.planner.isEmpty()) {
         if (this.pillarHandled(true)) return;
         this.finish(true);
         return;
      }
      if (this.pillarHandled(false)) return;
      if (this.delay > 0) this.delay--;

      Map<class_1792, Integer> have = this.inventory();
      boolean supply = this.creativeSupply.get() && !this.creativeBroken && this.creative();
      if (supply && !this.creativeAnnounced) {
         this.creativeAnnounced = true;
         this.info("Creative mode detected: I'll give myself the materials as the build needs them.");
      }
      this.creativeNow = supply;
      Predicate<class_1792> available = item -> supply || have.getOrDefault(item, 0) > 0;
      class_1661 inv = this.mc.field_1724.method_31548();
      class_1792 inHand = inv.method_5438(inv.method_67532()).method_7909();
      Set<class_1792> bar = new HashSet<>();
      for (int i = 0; i < 9; i++) bar.add(inv.method_5438(i).method_7909());
      double bring = supply ? 6.0 : 30.0;
      ToDoubleFunction<class_1792> swap = it -> it == inHand ? -4.0 : bar.contains(it) ? 0.0 : bring;
      IBaritone b = baritone();
      Planner.Choice choice = this.planner.choose(available, swap, this.reach.get());

      if (choice.kind == Planner.Kind.NONE) {
         this.noneTicks++;
         this.approach.stop();
         this.walkGoal = null;
         if (b != null && b.getCustomGoalProcess().isActive()) b.getPathingBehavior().cancelEverything();
         switch (choice.why) {
            case DONE -> this.finish(true);
            case MATERIALS -> {
               if (this.stayOn.get()) {
                  long now = System.currentTimeMillis();
                  if (now - this.lastNag > 30000L) {
                     this.lastNag = now;
                     this.warning("Waiting for materials. %d blocks need items you don't have.", this.planner.size());
                  }
                  this.doing = "Waiting for materials";
                  Look.idleLook(this.random);
               } else if (this.noneTicks > 20) {
                  this.finish(false);
               }
            }
            default -> {
               this.doing = "Looking for a way to place the rest";
               Look.idleLook(this.random);
            }
         }
         return;
      }
      this.noneTicks = 0;
      this.syncLayer();
      int activeY = this.planner.activeLayerY();
      if (this.pillar.mode() == Pillar.Mode.HOLD && activeY != Integer.MIN_VALUE && activeY != this.pillar.layer()) {
         // the layer the pillar was for is done (or a lower one needs doing): come down
         this.approach.stop();
         this.pillar.beginDown();
         return;
      }
      if (choice.kind == Planner.Kind.WALK) {
         this.goToward(b, choice.pos, choice.underUs);
         return;
      }

      class_2338 target = choice.pos;
      this.stuckAt = null;
      this.stuckTicks = 0;
      this.walkGoal = null;
      this.climbTicks = 0;
      this.reposition.reset();
      this.doing = "";
      this.approach.stop();
      if (b != null && b.getCustomGoalProcess().isActive()) b.getPathingBehavior().cancelEverything();

      class_1792 item = this.wanted.get(target).method_26204().method_8389();
      if (!this.hold(item)) return;

      int[] profile = Planner.speedProfile(this.speed.get());
      boolean aimed = Look.lookAt(choice.placement.hit(), (float) profile[2]);
      for (int i = 1; i < profile[3] && !aimed; i++) aimed = Look.lookAt(choice.placement.hit(), (float) profile[2]);
      // a block that is lined up but never gets clicked (something in the way, server refusing): rest it, then drop it
      if (!target.equals(this.aimTarget)) {
         this.aimTarget = target;
         this.aimTicks = 0;
      } else if (++this.aimTicks > 100) {
         this.aimTicks = 0;
         this.planner.stuck(target);
         return;
      }
      if (aimed && this.delay <= 0 && Look.crosshairOn(choice.placement.against(), choice.placement.side()) && Look.useCrosshairBlock()) {
         // count a refused placement as an attempt at most every half second, so a mob in the way for a moment doesn't use them up
         Integer last = this.attemptTick.get(target);
         if (last == null || this.ticks - last >= 10) {
            this.attemptTick.put(target, this.ticks);
            if (this.attempts.merge(target, 1, Integer::sum) >= MAX_ATTEMPTS) {
               this.planner.drop(target);
               this.skipped++;
            }
         }
         this.planner.placed(target);
         this.aimTicks = 0;
         this.asideTries = 0;
         this.countTick = -100;
         this.delay = profile[0] + (profile[1] > profile[0] ? this.random.nextInt(profile[1] - profile[0] + 1) : 0);
      }
   }

   /**
    * Shows only the layer being built in Litematica and moves up when it is done. Going back down to a lower
    * layer (a hanging block that just got its support) waits a moment so the view doesn't flicker.
    */
   private void syncLayer() {
      int y = this.planner.activeLayerY();
      if (y == Integer.MIN_VALUE || !this.ghostOwned) return;
      if (y == this.shownLayer) {
         this.pendLayer = Integer.MIN_VALUE;
         return;
      }
      if (this.shownLayer != Integer.MIN_VALUE && y < this.shownLayer) {
         if (y != this.pendLayer) {
            this.pendLayer = y;
            this.pendSince = this.ticks;
            return;
         }
         if (this.ticks - this.pendSince < 60) return;
      }
      this.pendLayer = Integer.MIN_VALUE;
      this.shownLayer = y;
      try {
         LitematicaBridge.showLayer(y);
      } catch (Throwable ignored) {
      }
   }

   /** The blocks of the layer being built, for the fallback outline. */
   private List<class_2338> previewList() {
      return this.planner.view();
   }

   /**
    * Drives the pillar: walking to its foot, building it up, taking it down. True when it used this tick.
    * With {@code finishing} the build is done and only the descent is left.
    */
   private boolean pillarHandled(boolean finishing) {
      Pillar.Mode m = this.pillar.mode();
      if (finishing && m != Pillar.Mode.OFF && m != Pillar.Mode.DOWN) {
         this.approach.stop();
         this.pillar.beginDown();
         m = this.pillar.mode();
      }
      if (m == Pillar.Mode.OFF || m == Pillar.Mode.HOLD) return false;
      this.noneTicks = 0;
      IBaritone b = baritone();
      if (b != null && b.getCustomGoalProcess().isActive() && m != Pillar.Mode.WALK) b.getPathingBehavior().cancelEverything();
      switch (m) {
         case WALK -> this.doing = this.pillar.tickWalk(this.approach);
         case UP -> {
            this.doing = "Building a pillar";
            class_1792 spare = this.pillarItem;
            this.pillar.tickUp(this.pillarGoal, this.reach.get(), () -> this.hold(spare));
         }
         default -> {
            this.doing = "Taking the pillar down";
            if (this.pillar.tickDown(this.approach)) {
               this.doing = "";
               return false;
            }
         }
      }
      if (this.pillar.takeFailed()) {
         this.warning("The pillar didn't work out (no spare blocks, or I slid off). Coming down and resting that block.");
         if (this.pillarGoal != null) this.planner.stuck(this.pillarGoal);
      }
      return true;
   }

   /** Starts a pillar for a block with nothing to stand on near it. False when there are no spare blocks or no ground for one. */
   private boolean startPillar(class_2338 goal) {
      if (!this.pillarUp.get() || this.pillar.mode() != Pillar.Mode.OFF) return false;
      class_1792 spare = this.creativeNow ? Pillar.creativeItem() : Pillar.pickItem(this.inventory(true), this.planner.remainingItems().keySet());
      if (spare == null) {
         long now = System.currentTimeMillis();
         if (now - this.lastNag > 30000L) {
            this.lastNag = now;
            this.warning("Can't reach the block at %d %d %d and I have no spare blocks to pillar with. Carry some cobblestone or dirt.", goal.method_10263(), goal.method_10264(), goal.method_10260());
         }
         return false;
      }
      if (this.footprint == null) {
         this.footprint = new java.util.HashSet<>();
         for (class_2338 p : this.wanted.keySet()) this.footprint.add(Pillar.key(p.method_10263(), p.method_10260()));
      }
      class_2338 base = Pillar.pickColumn(goal, this.reach.get(), this.footprint);
      if (base == null) return false;
      this.pillarItem = spare;
      this.pillarGoal = goal;
      this.pillar.start(base, spare, goal.method_10264());
      this.standFor = null;
      this.climbTicks = 0;
      this.info("Building a pillar to reach layer %d.", this.planner.layerNumber());
      return true;
   }

   /** Distance from the player's eyes to the middle of the block. */
   private double eyeDistance(class_2338 p) {
      var eye = this.mc.field_1724.method_33571();
      double dx = p.method_10263() + 0.5 - eye.field_1352;
      double dy = p.method_10264() + 0.5 - eye.field_1351;
      double dz = p.method_10260() + 0.5 - eye.field_1350;
      return Math.sqrt(dx * dx + dy * dy + dz * dz);
   }

   /**
    * Out of reach: walk to a cell from which the block can be placed (on the layer below once it is built, so higher
    * layers work without flying); in reach but unplaceable: the stuck logic.
    */
   private void goToward(IBaritone b, class_2338 goal, boolean underUs) {
      if (this.pillar.mode() == Pillar.Mode.HOLD) {
         // the pillar can't be walked off and back onto: take it down, then work out how to reach this block
         this.approach.stop();
         this.pillar.beginDown();
         return;
      }
      if (!underUs && this.eyeDistance(goal) > this.reach.get() - 0.2) {
         this.stuckAt = null;
         this.stuckTicks = 0;
         this.reposition.reset();
         if (!goal.equals(this.standFor) || !Stand.stillGood(this.standSpot)) {
            this.standFor = goal;
            this.standSpot = Stand.find(goal, this.reach.get(), this.planner);
            this.walkGoal = null;
            this.walkTicks = 0;
         }
         if (this.standSpot == null) {
            // nothing solid to stand on within reach of it yet: pillar up beside the build, or wait for the layer below
            if (++this.climbTicks > 10 && this.startPillar(goal)) return;
            if (this.climbTicks > 30) {
               this.climbTicks = 0;
               this.standFor = null;
               this.approach.reset();
               this.warning("Nowhere to stand to reach the block at %d %d %d yet. Resting it until the layer below is built.", goal.method_10263(), goal.method_10264(), goal.method_10260());
               this.planner.stuck(goal);
            }
            this.doing = "Looking for a place to stand";
            return;
         }
         this.climbTicks = 0;
         // a spot that can't be walked to (no path, across a gap) is given up on after a minute and a half
         if (goal.equals(this.walkGoal)) {
            if (++this.walkTicks > 1800) {
               this.walkTicks = 0;
               this.walkGoal = null;
               this.standFor = null;
               this.approach.reset();
               this.planner.stuck(goal);
               return;
            }
         } else {
            this.walkGoal = goal;
            this.walkTicks = 0;
         }
         int layer = this.planner.layerNumber();
         this.doing = this.approach.walk(this.standSpot, 0);
         long now = System.currentTimeMillis();
         if (now - this.lastWalkInfo > 20000L) {
            this.lastWalkInfo = now;
            this.info("Moving to layer %d: %s", layer, this.doing);
         }
         if (this.approach.takeSwitched()) this.warning("Baritone isn't getting closer, so I'm walking to the build by hand.");
         return;
      }
      this.approach.stop();
      this.doing = "Placing blocks";
      this.walkOrGiveUp(b, goal, underUs);
   }

   /**
    * The block is close and supported but can't be clicked from here: Reposition gives Baritone one move at a time to
    * another side of it, and after three moves that didn't help the block is rested.
    */
   private void walkOrGiveUp(IBaritone b, class_2338 goal, boolean underUs) {
      if (this.reposition.tick(b, goal, underUs, null)) this.planner.stuck(goal);
   }

   /** Ends the run: everything placed, or everything that can be placed with the materials at hand. */
   private void finish(boolean complete) {
      int skippedNow = this.skipped + this.planner.skipped + this.planner.blocked;
      int done = Math.max(0, this.total - this.planner.size() - skippedNow);
      if (complete) {
         this.clearJob();
         int skippedAll = this.skipped + this.planner.skipped + this.planner.blocked;
         this.halt("Done: " + (this.total - skippedAll) + " blocks placed, " + skippedAll + " skipped"
            + (this.planner.blocked > 0 ? " (" + this.planner.blocked + " had something else in the way)" : "") + ".", false);
         return;
      }
      Map<class_1792, Integer> still = this.planner.remainingItems();
      int unreachable = this.planner.deferredCount();
      StringBuilder sb = new StringBuilder();
      int shown = 0;
      for (Map.Entry<class_1792, Integer> e : still.entrySet()) {
         if (shown++ >= 8) {
            sb.append("and ").append(still.size() - 8).append(" more");
            break;
         }
         sb.append(e.getValue()).append("x ").append(name(e.getKey())).append(", ");
      }
      String list = sb.length() > 2 ? (sb.toString().endsWith(", ") ? sb.substring(0, sb.length() - 2) : sb.toString()) : "nothing";
      this.halt("Placed " + done + " of " + this.total + " blocks with what you had. Still need: " + list
         + (unreachable > 0 ? ". " + unreachable + " blocks couldn't be reached or have nothing to attach to yet." : "")
         + " Turn stay-on-when-out on to wait for materials instead.", false);
   }

   /** True in creative mode (the same check Litematica uses: the player's abilities). */
   private boolean creative() {
      try {
         return this.mc.field_1724 != null && this.mc.field_1724.method_31549().field_7477;
      } catch (Throwable t) {
         return false;
      }
   }

   /** Puts a full stack of the item into a hotbar slot through the creative inventory. */
   private boolean conjure(class_1792 item) {
      try {
         int slot = this.freeHotbarSlot();
         if (slot < 0) slot = this.leastUsefulHotbarSlot();
         class_1799 stack = new class_1799(item);
         stack.method_7939(Math.max(1, stack.method_7914()));
         this.mc.field_1724.method_31548().method_5447(slot, stack);
         this.mc.field_1761.method_2909(stack, 36 + slot);
         this.countTick = -100;
         this.pause(2);
         return true;
      } catch (Throwable t) {
         if (!this.creativeBroken) {
            this.creativeBroken = true;
            this.warning("Couldn't give myself items in creative (%s). Turn creative-supply off or tell me the log.", t.getClass().getSimpleName());
            System.out.println("[OnlyBuild] creative give failed: " + t);
         }
         return false;
      }
   }

   /** Hotbar slot to overwrite when none is empty: the one holding the fewest items, preferring things that aren't build materials. */
   private int leastUsefulHotbarSlot() {
      int best = this.mc.field_1724.method_31548().method_67532();
      int bestScore = Integer.MAX_VALUE;
      for (int i = 0; i < 9; i++) {
         class_1799 st = this.mc.field_1724.method_31548().method_5438(i);
         int score = (this.required.containsKey(st.method_7909()) ? 1000 : 0) + st.method_7947();
         if (score < bestScore) {
            bestScore = score;
            best = i;
         }
      }
      return best;
   }

   /** Creative test helper: fills the free slots with a stack of each material the build needs, most needed first. */
   private void getEverything() {
      if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
         this.error("Join a world first.");
         return;
      }
      if (!this.creative()) {
         this.error("Switch to creative mode first.");
         return;
      }
      try {
         if (this.planner.isEmpty()) this.load();
         Map<class_1792, Integer> need = new LinkedHashMap<>(this.planner.remainingItems());
         List<Map.Entry<class_1792, Integer>> sorted = new ArrayList<>(need.entrySet());
         sorted.sort((a, b) -> b.getValue() - a.getValue());
         class_1661 inv = this.mc.field_1724.method_31548();
         int given = 0;
         int left = 0;
         int[] order = new int[36];
         for (int i = 0; i < 36; i++) order[i] = i;
         int next = 0;
         for (Map.Entry<class_1792, Integer> e : sorted) {
            class_1792 item = e.getKey();
            boolean have = false;
            for (int i = 0; i < 36 && !have; i++) have = inv.method_5438(i).method_7909() == item;
            if (have) continue;
            int slot = -1;
            while (next < 36 && slot < 0) {
               int i = order[next++];
               class_1799 st = inv.method_5438(i);
               if (st.method_7960() || !need.containsKey(st.method_7909())) slot = i;
            }
            if (slot < 0) {
               left++;
               continue;
            }
            class_1799 stack = new class_1799(item);
            stack.method_7939(Math.max(1, stack.method_7914()));
            inv.method_5447(slot, stack);
            this.mc.field_1761.method_2909(stack, Look.inventorySlotToHandlerSlot(slot));
            given++;
         }
         this.countTick = -100;
         this.info("Gave you %d material stacks.%s", given, left > 0 ? " " + left + " more types didn't fit; creative-supply gives them as the build needs them." : "");
      } catch (Throwable t) {
         this.error("Couldn't give items: %s", t.getClass().getSimpleName());
         System.out.println("[OnlyBuild] get everything failed: " + t);
      }
   }

   /** An empty hotbar slot if there is one, so materials spread over the bar instead of swapping each other out. */
   private int freeHotbarSlot() {
      for (int i = 0; i < 9; i++) if (this.mc.field_1724.method_31548().method_5438(i).method_7960()) return i;
      return -1;
   }

   /** True when the item is in the selected hotbar slot; otherwise starts moving it there. */
   private boolean hold(class_1792 item) {
      if (Look.selectHotbar((class_1799 s) -> s.method_7909() == item)) return true;
      class_1661 inv = this.mc.field_1724.method_31548();
      for (int i = 9; i < 36; i++) {
         class_1799 st = inv.method_5438(i);
         if (!st.method_7960() && st.method_7909() == item) {
            this.hotbarFrom = i;
            this.hotbarTo = this.freeHotbarSlot();
            this.hotbarStep = 0;
            this.phase = Phase.HOTBAR;
            return false;
         }
      }
      if (this.creativeNow && this.conjure(item)) return false;
      this.countTick = -100;
      return false;
   }

   private static IBaritone baritone() {
      try {
         return BaritoneAPI.getProvider().getPrimaryBaritone();
      } catch (Throwable t) {
         return null;
      }
   }

   // ---------------------------------------------------------------- dashboard

   @EventHandler
   private void onRender(Render2DEvent event) {
      try {
         if (!this.showPanel.get() || this.mc.field_1724 == null || this.mc.field_1690.field_1842) return;
         if (this.hudModel == null || this.ticks - this.hudTick >= 10 || this.ticks < this.hudTick) {
            this.hudModel = this.dashModel();
            this.hudTick = this.ticks;
         }
         this.hud.render(event.drawContext, this.mc.field_1772, this.panelX.get(), this.panelY.get(), this.hudModel, this.compactPanel.get());
      } catch (Throwable t) {
         this.hudModel = null;
      }
   }

   /** " (next: layer 3, 36 blocks)" while another layer is waiting above the one being built. */
   private String nextLayerNote() {
      int next = this.planner.nextLayerY();
      if (next == Integer.MIN_VALUE) return " (last layer)";
      int first = this.planner.activeLayerY() != Integer.MIN_VALUE ? this.planner.activeLayerY() : this.planner.lowestLayerY();
      return " (next: layer " + (this.planner.layerNumber() + (next - first)) + ", " + this.planner.blocksIn(next) + " blocks)";
   }

   private HudPanel.Model dashModel() {
      HudPanel.Model m = new HudPanel.Model();
      m.title = "ONLY BUILD";
      m.badge = this.halted ? (this.haltError ? "ERROR" : "DONE") : this.planner.isEmpty() ? "DONE" : this.doing.equals("Waiting for materials") ? "WAITING" : "BUILDING";
      m.badgeColor = HudPanel.badgeColor(m.badge);
      m.activity = this.halted ? this.haltMessage : this.phase == Phase.HOTBAR ? "Moving materials to the hotbar"
         : !this.doing.isEmpty() && !this.doing.equals("Placing blocks") ? this.doing + ": " + this.planner.size() + " left"
         : "Placing layer " + this.planner.layerNumber() + " of " + this.planner.layerCount() + this.nextLayerNote() + ": " + this.planner.size() + " blocks left";
      int done = Math.max(0, this.total - this.planner.size());
      m.subtitle = this.total > 0 ? done * 100 / this.total + "% built" : "Inventory only";
      if (this.total > 0) m.bar("Build", done + "/" + this.total, done / (float) this.total, HudPanel.ACCENT);
      if (this.origin != null && this.mc.field_1724 != null) {
         double dx = this.mc.field_1724.method_23317() - this.origin.method_10263();
         double dz = this.mc.field_1724.method_23321() - this.origin.method_10260();
         m.row("Corner", where(this.origin) + "  (" + (int) Math.sqrt(dx * dx + dz * dz) + "m)");
      }
      if (this.missingTypes > 0) m.row("Missing", this.missingTypes + " material type" + (this.missingTypes == 1 ? "" : "s"));
      int skippedAll = this.skipped + this.planner.skipped + this.planner.blocked;
      if (skippedAll > 0) m.row("Skipped", skippedAll + " blocks");
      if (this.mc.field_1724 != null) {
         int mins = (int) Math.max(0, System.currentTimeMillis() - this.startedAt) / 60000;
         m.chip("HP " + Math.round(this.mc.field_1724.method_6032()));
         m.chip("Food " + this.mc.field_1724.method_7344().method_7586());
         m.chip("Up " + mins / 60 + "h" + mins % 60 + "m");
      }
      return m;
   }

   // ---------------------------------------------------------------- panel

   private static void section(GuiTheme theme, WVerticalList col, String name) {
      try {
         col.add((WWidget) (Object) theme.horizontalSeparator(name));
      } catch (Throwable t) {
         col.add(theme.label("— " + name + " —"));
      }
   }

   private static void title(GuiTheme theme, WVerticalList col, String text) {
      try {
         col.add(theme.label(text, true));
      } catch (Throwable t) {
         col.add(theme.label(text));
      }
   }

   @Override
   public WWidget getWidget(GuiTheme theme) {
      WVerticalList col = theme.verticalList();
      title(theme, col, "◆ TheOne Client — Only Build");
      col.add(theme.label("Status: " + (!this.isActive() ? "idle" : this.halted ? this.haltMessage
         : (this.doing.isEmpty() ? "Placing blocks" : this.doing) + " (" + this.planner.size() + " left of " + this.total + ")")));
      col.add(theme.label("Builds with what you carry. No shopping, no chests, no orders."));

      section(theme, col, "Build");
      WHorizontalList run = theme.horizontalList();
      WButton start = run.add(theme.button("Start Build")).widget();
      start.action = () -> {
         this.clearJob();
         if (this.isActive()) this.toggle();
         this.toggle();
      };
      WButton stop = run.add(theme.button("Stop")).widget();
      stop.action = () -> {
         this.clearJob();
         if (this.isActive()) this.toggle();
      };
      col.add(run);
      WHorizontalList place = theme.horizontalList();
      WButton here = place.add(theme.button("Set Build Here")).widget();
      here.action = () -> this.saveSpot(this.mc.field_1724 == null ? null : this.mc.field_1724.method_24515());
      WButton looking = place.add(theme.button("Set Build Where I'm Looking")).widget();
      looking.action = () -> {
         if (this.mc.field_1724 == null) return;
         class_239 hit = this.mc.field_1724.method_5745(8.0, 1.0F, false);
         if (hit instanceof class_3965 block && hit.method_17783() == class_239.class_240.field_1332) this.saveSpot(block.method_17777().method_10084());
         else this.error("Aim at a ground block within 8 blocks first.");
      };
      WButton clearPlace = place.add(theme.button("Aim When Starting")).widget();
      clearPlace.action = () -> {
         this.clearJob();
         if (PLACE_FILE.delete() || !PLACE_FILE.exists()) this.info("Saved build corner cleared; the corner is where you aim when you start.");
      };
      col.add(place);
      FarmLocation saved = this.mc.field_1687 == null ? null : this.readSaved(PLACE_FILE);
      col.add(theme.label(saved == null ? "Build corner: where you aim when you press Start (farm goes toward +X and +Z)"
         : "Build corner: " + where(saved.origin())));

      section(theme, col, "Materials");
      WHorizontalList tools = theme.horizontalList();
      WButton everything = tools.add(theme.button("Creative: Get Everything")).widget();
      everything.action = this::getEverything;
      WButton check = tools.add(theme.button("Material Check")).widget();
      check.action = () -> {
         if (this.mc.field_1724 == null || this.mc.field_1687 == null) {
            this.error("Join a world first.");
            return;
         }
         if (this.planner.isEmpty()) this.info("Start the module once to load the schematic, then press this again.");
         else this.materialCheck();
      };
      col.add(tools);
      col.add(theme.label("In creative it gives itself what the build needs automatically (creative-supply). Get Everything fills your inventory up front."));
      col.add(theme.label("In survival it builds with what you carry and, with stay-on-when-out, waits for more materials instead of turning off."));
      return col;
   }

   private void saveSpot(class_2338 p) {
      if (p == null || this.mc.field_1687 == null) {
         this.error("Join a world first.");
         return;
      }
      try {
         new FarmLocation(p, this.currentDim(), this.currentServer()).save(PLACE_FILE.toPath());
         this.clearJob();
         this.info("Build corner saved at %s.", where(p));
      } catch (Exception e) {
         this.error("Couldn't save the build corner: %s", e.getMessage());
      }
   }
}
