package dev.thri.module.modules.world;

import dev.thri.module.Category;
import dev.thri.module.Module;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.HashSet;
import java.util.Set;

public class XRay extends Module {
    private final Set<Block> visible = new HashSet<>();

    public XRay() {
        super("XRay", Category.WORLD, 0);
        for (String id : new String[]{
                "minecraft:coal_ore","minecraft:deepslate_coal_ore",
                "minecraft:iron_ore","minecraft:deepslate_iron_ore",
                "minecraft:copper_ore","minecraft:deepslate_copper_ore",
                "minecraft:gold_ore","minecraft:deepslate_gold_ore","minecraft:nether_gold_ore",
                "minecraft:redstone_ore","minecraft:deepslate_redstone_ore",
                "minecraft:emerald_ore","minecraft:deepslate_emerald_ore",
                "minecraft:lapis_ore","minecraft:deepslate_lapis_ore",
                "minecraft:diamond_ore","minecraft:deepslate_diamond_ore",
                "minecraft:ancient_debris","minecraft:nether_quartz_ore",
                "minecraft:spawner","minecraft:chest","minecraft:trapped_chest"
        }) {
            Block b = Registries.BLOCK.get(Identifier.of(id));
            if (b != Blocks.AIR) visible.add(b);
        }
    }

    @Override protected void onEnable()  { if (mc.worldRenderer != null) mc.worldRenderer.reload(); }
    @Override protected void onDisable() { if (mc.worldRenderer != null) mc.worldRenderer.reload(); }

    public boolean visible(BlockState s) { return visible.contains(s.getBlock()); }
}
