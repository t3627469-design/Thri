#!/usr/bin/env python3
"""Generates theone-test-build.litematic: a small creative-mode test course for the Schematic Builder.

Every section exercises one feature, so you can see at a glance what works in game:

  floor      11x11 stone at y=0                     bottom-up order, speed
  farm       3x3 farmland + water + 8 wheat          dirt + hoe, bucket, seeds on farmland
  stairs     4 facings, bottom row and top row       facing + half via rotation
  logs       axis x / z / y                          axis via clicked face
  slabs      bottom / top / double                   half + double slab completion
  trapdoors  bottom / top                            half via click height
  observers  facing up / north                       6-way facing (look up/down)
  sign       standing, rotation 4                    16-way rotation
  door       lower half only (upper auto)            two-block blocks, hinge
  fence      two posts                               neighbour-driven props are ignored
  tower      cobblestone column y=1..11              pillaring up
  wall torch on the tower                            attachment to a side face
  platform   3x3 glass at y=12                       building high up, spreading support
  lantern    hanging under the platform              hanging placement

Writes the Litematica format (v6) with the same metadata Litematica writes, so the file also
opens in Litematica for a side-by-side comparison.
"""
import gzip
import struct
import sys
import time

SIZE = (11, 13, 11)  # x, y, z
DATA_VERSION = 4671  # Minecraft 1.21.11


def build():
    b = {}

    def put(x, y, z, state):
        b[(x, y, z)] = state

    # floor
    for x in range(11):
        for z in range(11):
            put(x, 0, z, "minecraft:stone")
    # farm: water in the middle, farmland around, wheat on top
    for x in range(1, 4):
        for z in range(1, 4):
            if (x, z) == (2, 2):
                put(x, 0, z, "minecraft:water[level=0]")
            else:
                put(x, 0, z, "minecraft:farmland[moisture=0]")
                put(x, 1, z, "minecraft:wheat[age=0]")
    # stairs: bottom row at z=1, top row at z=3
    for i, facing in enumerate(["north", "south", "east", "west"]):
        put(6 + i, 1, 1, f"minecraft:oak_stairs[facing={facing},half=bottom,shape=straight,waterlogged=false]")
        put(6 + i, 1, 3, f"minecraft:oak_stairs[facing={facing},half=top,shape=straight,waterlogged=false]")
    # logs
    put(6, 1, 5, "minecraft:oak_log[axis=x]")
    put(7, 1, 5, "minecraft:oak_log[axis=z]")
    put(8, 1, 5, "minecraft:oak_log[axis=y]")
    # observers
    put(9, 1, 5, "minecraft:observer[facing=up,powered=false]")
    put(9, 1, 7, "minecraft:observer[facing=north,powered=false]")
    # slabs
    put(6, 1, 7, "minecraft:stone_slab[type=bottom,waterlogged=false]")
    put(7, 1, 7, "minecraft:stone_slab[type=top,waterlogged=false]")
    put(8, 1, 7, "minecraft:stone_slab[type=double,waterlogged=false]")
    # trapdoors
    put(6, 1, 9, "minecraft:oak_trapdoor[facing=north,half=bottom,open=false,powered=false,waterlogged=false]")
    put(7, 1, 9, "minecraft:oak_trapdoor[facing=north,half=top,open=false,powered=false,waterlogged=false]")
    # sign, door, fence
    put(2, 1, 6, "minecraft:oak_sign[rotation=4,waterlogged=false]")
    put(2, 1, 8, "minecraft:oak_door[facing=south,half=lower,hinge=left,open=false,powered=false]")
    put(2, 2, 8, "minecraft:oak_door[facing=south,half=upper,hinge=left,open=false,powered=false]")
    put(4, 1, 9, "minecraft:oak_fence[east=true,north=false,south=false,west=false,waterlogged=false]")
    put(5, 1, 9, "minecraft:oak_fence[east=false,north=false,south=false,west=true,waterlogged=false]")
    # tower, torch, platform, lantern
    for y in range(1, 12):
        put(10, y, 10, "minecraft:cobblestone")
    put(9, 3, 10, "minecraft:wall_torch[facing=west]")
    for x in range(8, 11):
        for z in range(8, 11):
            put(x, 12, z, "minecraft:glass")
    put(8, 11, 8, "minecraft:lantern[hanging=true,waterlogged=false]")
    return b


# ---- NBT writer ----

def _str(s):
    e = s.encode("utf-8")
    return struct.pack(">H", len(e)) + e


class Int(int):
    pass


class Long(int):
    pass


class LongArray(list):
    pass


def _tag(v):
    if isinstance(v, dict):
        return 10
    if isinstance(v, LongArray):
        return 12
    if isinstance(v, list):
        return 9
    if isinstance(v, str):
        return 8
    if isinstance(v, Long):
        return 4
    if isinstance(v, int):
        return 3
    raise TypeError(type(v))


def _payload(v):
    t = _tag(v)
    if t == 3:
        return struct.pack(">i", v)
    if t == 4:
        return struct.pack(">q", v)
    if t == 8:
        return _str(v)
    if t == 12:
        return struct.pack(">i", len(v)) + b"".join(struct.pack(">q", x) for x in v)
    if t == 9:
        et = _tag(v[0]) if v else 0
        return bytes([et]) + struct.pack(">i", len(v)) + b"".join(_payload(e) for e in v)
    out = b""
    for k, e in v.items():
        out += bytes([_tag(e)]) + _str(k) + _payload(e)
    return out + b"\x00"


def to_signed(u):
    return u - (1 << 64) if u >= (1 << 63) else u


def litematic(blocks):
    sx, sy, sz = SIZE
    palette = ["minecraft:air"]
    for s in sorted(set(blocks.values())):
        palette.append(s)
    index = {s: i for i, s in enumerate(palette)}
    bits = max(2, (len(palette) - 1).bit_length())
    volume = sx * sy * sz
    words = [0] * ((volume * bits + 63) // 64)
    for y in range(sy):
        for z in range(sz):
            for x in range(sx):
                v = index.get(blocks.get((x, y, z), "minecraft:air"))
                i = y * sx * sz + z * sx + x
                for bit in range(bits):
                    if (v >> bit) & 1:
                        p = i * bits + bit
                        words[p >> 6] |= 1 << (p & 63)

    def state(s):
        name, _, rest = s.partition("[")
        c = {"Name": name}
        if rest:
            c["Properties"] = dict(kv.split("=") for kv in rest.rstrip("]").split(","))
        return c

    now = Long(int(time.time() * 1000))
    xyz = lambda x, y, z: {"x": Int(x), "y": Int(y), "z": Int(z)}
    return {
        "MinecraftDataVersion": Int(DATA_VERSION),
        "Version": Int(6),
        "SubVersion": Int(1),
        "Metadata": {
            "Name": "theone-test-build",
            "Author": "TheOne",
            "Description": "Creative test course for the TheOne Schematic Builder",
            "RegionCount": Int(1),
            "TimeCreated": now,
            "TimeModified": now,
            "TotalBlocks": Int(len(blocks)),
            "TotalVolume": Int(volume),
            "EnclosingSize": xyz(sx, sy, sz),
        },
        "Regions": {
            "test": {
                "Position": xyz(0, 0, 0),
                "Size": xyz(sx, sy, sz),
                "BlockStatePalette": [state(s) for s in palette],
                "BlockStates": LongArray(to_signed(w) for w in words),
                "Entities": [],
                "TileEntities": [],
                "PendingBlockTicks": [],
                "PendingFluidTicks": [],
            }
        },
    }


def main(out):
    blocks = build()
    root = litematic(blocks)
    data = bytes([10]) + _str("") + _payload(root)
    # mtime=0 keeps the output byte-identical between runs apart from the metadata timestamps
    with open(out, "wb") as f:
        f.write(gzip.compress(data, mtime=0))
    print(f"wrote {out}: {len(blocks)} blocks, {SIZE[0]}x{SIZE[1]}x{SIZE[2]}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "theone-test-build.litematic")
