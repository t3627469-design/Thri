# TheOne Client 2.0.1: Schematic Builder

A Meteor Client addon for **Minecraft 1.21.11** (Fabric, Java 21) that builds schematics for you.

## Install

1. Put `dist/theone-client-2.0.1.jar` in `.minecraft/mods`, next to Meteor Client for 1.21.11.
   Remove any older `theone-client-*.jar` (same mod id).
2. Put your schematic in `.minecraft/schematics` (`.litematic`, `.schem` or `.nbt`).
3. In game: Meteor GUI → **TheOne** → **Schematic Builder**.

## How it works

- **Placement:** On enable, the schematic's minimum corner is anchored at your feet. The origin
  is then locked into the X/Y/Z settings, so turning the module off and on resumes the same build.
  Rotation and mirror settings are available.
- **Blocks face the right way:** For every directional block (stairs, logs, slabs, trapdoors,
  observers, pistons, hoppers, wall torches, signs, buttons, anvils...), the builder asks the game's
  own placement logic which click and rotation give the wanted state, then sends that rotation.
- **Fast:** Up to 12 placements per tick (default 3), with an optional delay. It pulls items from
  your inventory into the hotbar automatically.
- **Smart order:** It builds bottom-up, only places blocks that have something to attach to, and
  skips blocks it cannot do yet without stalling the rest of the build.
- **Goes up:** It walks to the next unfinished part, jumps up ledges, and pillars up under itself
  for layers out of reach. Pillars use the schematic's own block where the pillar runs through the
  build; elsewhere they use the scaffold blocks you configure. Leftover scaffold is outlined in red.
- **Farms:** It builds farmland by placing dirt and tilling it with any hoe. Water and lava sources
  are placed with buckets after all solid blocks are done.
- **Materials:** On start and as you build, it tells you what is still missing from your inventory.
- **Optional:** It can break blocks that don't match the schematic. Air-place is off by default.
- **Render:** Missing blocks show as cyan boxes and wrong blocks as red ones.

## Creative test course

The jar ships `theone-test-build.litematic` (11x13x11, 174 blocks), and it is the default
`file`. It has one section per feature: stone floor, farm (farmland, water, wheat), stairs in 4
facings at both halves, logs on 3 axes, observers, bottom/top/double slabs, trapdoors, a sign, a
door, a fence, an 11-high cobblestone tower with a wall torch, a glass platform at the top and a
hanging lantern. Regenerate it with `python3 tools/testmodel/gen_test_build.py <out>`.

To test: in creative (`/gamemode creative`), stand on flat open ground, fill your hotbar and
inventory with the blocks, a hoe and a water bucket, then turn the module on. The schematic's
corner goes at your feet. Stairs, slabs and so on that come out wrong show as red boxes.

## Bundling schematics into the jar

Drop schematic files into `src/main/resources/assets/theone-client/schematics/` and rebuild. On
first launch they are copied into `.minecraft/schematics`. Existing files are never overwritten.

## Building

```
./build.sh
```

The build doesn't need Gradle or Loom. Sources use yarn names and compile against small API stubs
(`tools/stubs`). Then:

1. Every stub member is checked against the yarn 1.21.11 mappings.
2. The compiled classes are remapped to intermediary names (`tools/src/Remap.java`).
3. Every Minecraft field and method the jar references is verified, by exact name and descriptor,
   against the official 1.21.11 intermediary table.

Any reference that doesn't exist in 1.21.11 fails the build. The schematic readers have offline
round-trip tests (`src/test`) that run on every build.
