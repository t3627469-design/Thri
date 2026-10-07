"""
Peaceful Pond -- built entirely in Blender (bpy) from code.

Run:  python3 build_pond.py            (with the `bpy` module installed)
  or  blender -b -P build_pond.py

Outputs (next to this script):
  pond.blend    the full Blender scene
  pond.glb      exported scene used by index.html (three.js)
  preview.png   Cycles render of the scene (skip with --no-render)
"""
import bpy, bmesh, math, random, sys, os, json
from mathutils import Vector, noise

HERE = os.path.dirname(os.path.abspath(__file__))
RENDER = "--no-render" not in sys.argv
random.seed(11)
rnd = random.random
uni = random.uniform

bpy.ops.wm.read_factory_settings(use_empty=True)
scene = bpy.context.scene

# ----------------------------------------------------------------- helpers
def srgb(h):
    r, g, b = ((h >> 16) & 255) / 255, ((h >> 8) & 255) / 255, (h & 255) / 255
    return (r ** 2.2, g ** 2.2, b ** 2.2)

def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))

def smooth(a, b, x):
    t = max(0.0, min(1.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)

def jitter(c, amt=0.08):
    k = 1 + uni(-amt, amt)
    return tuple(min(1, max(0, v * k)) for v in c)

def rotz(v, a):
    c, s = math.cos(a), math.sin(a)
    return Vector((v.x * c - v.y * s, v.x * s + v.y * c, v.z))


class Builder:
    """Collects faces with per-corner vertex colours + UVs, then makes an object."""
    def __init__(self):
        self.bm = bmesh.new()
        self.col = self.bm.loops.layers.color.new("Col")
        self.uv = self.bm.loops.layers.uv.new("UVMap")

    def vert(self, co):
        return self.bm.verts.new(co)

    def face(self, vs, cols, uvs=None, smooth_=True):
        try:
            f = self.bm.faces.new(vs)
        except ValueError:
            return None
        f.smooth = smooth_
        for i, l in enumerate(f.loops):
            c = cols[i] if isinstance(cols[0], (tuple, list)) else cols
            l[self.col] = (c[0], c[1], c[2], 1.0)
            if uvs:
                l[self.uv].uv = uvs[i]
        return f

    def to_obj(self, name, mat, parent=None):
        me = bpy.data.meshes.new(name)
        self.bm.to_mesh(me)
        self.bm.free()
        me.update()
        if "Col" in me.color_attributes:
            me.color_attributes.active_color = me.color_attributes["Col"]
            me.color_attributes.render_color_index = me.color_attributes.find("Col")
        ob = bpy.data.objects.new(name, me)
        bpy.context.scene.collection.objects.link(ob)
        ob.data.materials.append(mat)
        if parent:
            ob.parent = parent
        return ob


def add_ico(B, c, rad, sub, colfn, amp=0.0, seed=0.0, smooth_=True, rot=0.0):
    tmp = bmesh.new()
    bmesh.ops.create_icosphere(tmp, subdivisions=sub, radius=1.0)
    tmp.verts.index_update()
    out = {}
    for v in tmp.verts:
        n = v.co.copy()
        d = 1 + amp * noise.noise(n * 1.8 + Vector((seed, seed * 0.7, seed * 1.3)))
        p = Vector((n.x * rad[0] * d, n.y * rad[1] * d, n.z * rad[2] * d))
        if rot:
            p = rotz(p, rot)
        pos = Vector(c) + p
        out[v.index] = (B.vert(pos), colfn(pos, n))
    for f in tmp.faces:
        vs = [out[v.index][0] for v in f.verts]
        cs = [out[v.index][1] for v in f.verts]
        B.face(vs, cs, smooth_=smooth_)
    tmp.free()


def add_tube(B, p0, p1, r0, r1, seg, c0, c1, cap=False, smooth_=True):
    p0, p1 = Vector(p0), Vector(p1)
    axis = (p1 - p0).normalized()
    up = Vector((0, 0, 1)) if abs(axis.z) < 0.95 else Vector((1, 0, 0))
    a = axis.cross(up).normalized()
    b = axis.cross(a).normalized()
    ring0, ring1 = [], []
    for i in range(seg):
        t = i / seg * math.tau
        d = a * math.cos(t) + b * math.sin(t)
        ring0.append(B.vert(p0 + d * r0))
        ring1.append(B.vert(p1 + d * r1))
    for i in range(seg):
        j = (i + 1) % seg
        B.face([ring0[i], ring0[j], ring1[j], ring1[i]], [c0, c0, c1, c1], smooth_=smooth_)
    if cap:
        B.face(list(reversed(ring0)), c0, smooth_=False)
        B.face(ring1, c1, smooth_=False)


def add_box(B, c, size, col, rot=0.0, cols=None):
    sx, sy, sz = size[0] / 2, size[1] / 2, size[2] / 2
    pts = [(-sx, -sy, -sz), (sx, -sy, -sz), (sx, sy, -sz), (-sx, sy, -sz),
           (-sx, -sy, sz), (sx, -sy, sz), (sx, sy, sz), (-sx, sy, sz)]
    vs = [B.vert(Vector(c) + rotz(Vector(p), rot)) for p in pts]
    for idx in ((0, 3, 2, 1), (4, 5, 6, 7), (0, 1, 5, 4), (1, 2, 6, 5), (2, 3, 7, 6), (3, 0, 4, 7)):
        B.face([vs[i] for i in idx], col, smooth_=False)


def add_cone(B, c, r, h, seg, c0, c1, rot=0.0):
    c = Vector(c)
    ring = [B.vert(c + rotz(Vector((math.cos(i / seg * math.tau) * r, math.sin(i / seg * math.tau) * r, 0)), rot)) for i in range(seg)]
    tip = B.vert(c + Vector((0, 0, h)))
    for i in range(seg):
        B.face([ring[i], ring[(i + 1) % seg], tip], [c0, c0, c1], smooth_=False)
    B.face(list(reversed(ring)), c0, smooth_=False)


# ---------------------------------------------------------------- materials
def vc_mat(name, rough=0.85, emission=None, double=False, spec=0.3):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    nt = m.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    vcol = nt.nodes.new("ShaderNodeVertexColor")
    vcol.layer_name = "Col"
    nt.links.new(vcol.outputs["Color"], bsdf.inputs["Base Color"])
    bsdf.inputs["Roughness"].default_value = rough
    bsdf.inputs["Specular IOR Level"].default_value = spec
    if emission:
        nt.links.new(vcol.outputs["Color"], bsdf.inputs["Emission Color"])
        bsdf.inputs["Emission Strength"].default_value = emission
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    m.use_backface_culling = not double
    return m


MAT = {
    "terrain": vc_mat("TerrainMat", 0.95),
    "grass": vc_mat("GrassMat", 0.6, double=True),
    "plant": vc_mat("PlantMat", 0.7, double=True),
    "rock": vc_mat("RockMat", 0.9),
    "wood": vc_mat("WoodMat", 0.8),
    "leaf": vc_mat("LeafMat", 0.75),
    "animal": vc_mat("AnimalMat", 0.5, double=True),
    "glow": vc_mat("GlowMat", 0.4, emission=8.0),
}
wm = bpy.data.materials.new("WaterMat")
wm.use_nodes = True
b = wm.node_tree.nodes["Principled BSDF"]
b.inputs["Base Color"].default_value = (0.02, 0.16, 0.18, 1)
b.inputs["Roughness"].default_value = 0.02
b.inputs["IOR"].default_value = 1.33
b.inputs["Transmission Weight"].default_value = 0.92
MAT["water"] = wm

# ------------------------------------------------------------------ terrain
WATER_Z = -0.2
SHORE_BASE = 8.2

def shore_radius(th):
    return (SHORE_BASE + 1.6 * math.sin(2 * th + 0.6) + 0.9 * math.sin(3 * th + 2.0)
            + 0.5 * math.sin(5 * th + 1.0))

def pond_d(x, y):
    return math.hypot(x, y) - shore_radius(math.atan2(y, x))

def terrain_h(x, y):
    r = math.hypot(x, y)
    d = pond_d(x, y)
    nh = noise.fractal(Vector((x * 0.045, y * 0.045, 3.1)), 1.0, 2.0, 4)
    micro = noise.noise(Vector((x * 0.35, y * 0.35, 7.7))) * 0.07
    if d < 0:
        t = min(1.0, -d / 3.6)
        return 0.1 - 1.55 * smooth(0, 1, t) + micro * 0.5
    bank = 0.3 * smooth(0, 4, d)
    hills = smooth(9, 34, r) * (1.1 + 4.2 * smooth(18, 44, r)) * max(0.12, 0.62 + 0.55 * nh)
    return 0.1 + bank + (hills + micro) * smooth(0, 4, d)

print("shore radius range: %.2f .. %.2f" % (
    min(shore_radius(i / 90 * math.tau) for i in range(90)),
    max(shore_radius(i / 90 * math.tau) for i in range(90))))

# ---- layout anchors shared by several props
dock_theta = -math.pi / 2 - 0.35
_u = Vector((-math.cos(dock_theta), -math.sin(dock_theta), 0))
_sdir = Vector((-_u.y, _u.x, 0))
_S0 = Vector((shore_radius(dock_theta) * math.cos(dock_theta), shore_radius(dock_theta) * math.sin(dock_theta), 0))
BOAT = _S0 + _u * 3.0 + _sdir * 2.1
SHOP = _S0 + _u * -4.2 + _sdir * -3.4
COLL = []   # (x, y, radius) obstacles for the first-person walker
AOC = []    # (x, y, radius, strength) soft ambient-occlusion blobs baked into ground colours
BENCH = (10.4, 7.9)

# ---- worn footpaths -----------------------------------------------------------------
def _ring(th0, th1, off, n):
    return [((shore_radius(t) + off) * math.cos(t), (shore_radius(t) + off) * math.sin(t)) for t in [th0 + (th1 - th0) * i / n for i in range(n + 1)]]
_bench_th = math.atan2(BENCH[1], BENCH[0])
PATHS = [
    [(SHOP.x + _u.x * 2.4, SHOP.y + _u.y * 2.4), (_S0.x - _u.x * 1.9, _S0.y - _u.y * 1.9)],
    _ring(dock_theta, _bench_th - 0.05, 2.3, 60) + [(BENCH[0] - 0.9, BENCH[1] - 0.7)],
    _ring(dock_theta, dock_theta - 1.5, 2.3, 30),
]
_pgrid = {}
for poly in PATHS:
    for (a0, a1) in zip(poly[:-1], poly[1:]):
        seg = math.hypot(a1[0] - a0[0], a1[1] - a0[1])
        for i in range(int(seg / 0.25) + 1):
            k = i * 0.25 / max(seg, 1e-6)
            px, py = a0[0] + (a1[0] - a0[0]) * k, a0[1] + (a1[1] - a0[1]) * k
            _pgrid.setdefault((math.floor(px), math.floor(py)), []).append((px, py))
def path_dist(x, y):
    best = 9.0
    cx, cy = math.floor(x), math.floor(y)
    for gx in (cx - 1, cx, cx + 1):
        for gy in (cy - 1, cy, cy + 1):
            for (px, py) in _pgrid.get((gx, gy), ()):
                d = (px - x) ** 2 + (py - y) ** 2
                if d < best: best = d
    return math.sqrt(best)

T = Builder()
N, EXT = 230, 46.0
grid = []
heights = {}
for j in range(N + 1):
    row = []
    for i in range(N + 1):
        x = -EXT + 2 * EXT * i / N
        y = -EXT + 2 * EXT * j / N
        h = terrain_h(x, y)
        row.append((x, y, h, T.vert(Vector((x, y, h)))))
    grid.append(row)

C_MUD = srgb(0x2b2a22); C_DEEP = srgb(0x14302e); C_SAND = srgb(0x9c8a5c)
C_WET = srgb(0x4b4128); C_G1 = srgb(0x3b7a1c); C_G2 = srgb(0x69a02a)
C_G3 = srgb(0x2a5e18); C_FAR = srgb(0x5f9a45); C_DIRT = srgb(0x7a5d36)

def terrain_color(x, y, h):
    d = pond_d(x, y)
    r = math.hypot(x, y)
    n1 = noise.noise(Vector((x * 0.12, y * 0.12, 1.0)))
    n2 = noise.noise(Vector((x * 0.6, y * 0.6, 4.0)))
    if h < WATER_Z - 0.05:
        return mix(C_WET, C_DEEP, smooth(WATER_Z, -1.3, h)) if h > -0.5 else mix(C_MUD, C_DEEP, smooth(-0.5, -1.4, h))
    g = mix(C_G1, C_G2, 0.5 + 0.6 * n1)
    g = mix(g, C_G3, smooth(0.2, 0.9, n2) * 0.45)
    g = mix(g, C_FAR, smooth(22, 42, r) * 0.65)
    g = mix(g, C_DIRT, smooth(0.55, 0.9, noise.noise(Vector((x * 0.2, y * 0.2, 9.0)))) * 0.6 * smooth(2, 5, d))
    beach = (1 - smooth(0.2, 1.6, d)) * (1 - smooth(0.12, 0.35, h))
    pd = path_dist(x, y)
    if pd < 1.1:
        dirt = mix(srgb(0x8f6e46), srgb(0x6a5032), noise.noise(Vector((x * 1.7, y * 1.7, 5.0))) * 0.5 + 0.5)
        dirt = mix(dirt, srgb(0xa58a62), smooth(0.3, 0.8, noise.noise(Vector((x * 6, y * 6, 2.0)))) * 0.5)
        g = mix(g, dirt, (1 - smooth(0.45, 1.05, pd + noise.noise(Vector((x * 2.3, y * 2.3, 8.0))) * 0.18)) * 0.92)
    c = mix(g, C_SAND, beach)
    c = mix(c, C_WET, (1 - smooth(-0.2, 0.2, h)) * 0.8)
    return c

for j in range(N):
    for i in range(N):
        q = [grid[j][i], grid[j][i + 1], grid[j + 1][i + 1], grid[j + 1][i]]
        T.face([p[3] for p in q], [terrain_color(p[0], p[1], p[2]) for p in q])
terrain = T.to_obj("Terrain", MAT["terrain"])

# ------------------------------------------------------------------- water
Wb = Builder()
S = 60
vs = [Wb.vert(Vector(p)) for p in ((-S, -S, WATER_Z), (S, -S, WATER_Z), (S, S, WATER_Z), (-S, S, WATER_Z))]
Wb.face(vs, srgb(0x1c6f78), smooth_=False)
water = Wb.to_obj("Water", MAT["water"])

# ------------------------------------------------------------------- grass
def add_blade(B, base, h, w, yaw, bend, tint, phase, tip=None, taper=0.65):
    d = Vector((math.cos(yaw), math.sin(yaw), 0))
    s = Vector((-math.sin(yaw), math.cos(yaw), 0))
    base = Vector(base)
    rows = []
    for t, wf in ((0.0, 1.0), (0.55, taper), (1.0, 0.0)):
        c = base + d * (bend * h * t * t) + Vector((0, 0, h * t))
        rows.append((c, wf, t))
    v = []
    for c, wf, t in rows[:2]:
        v.append(B.vert(c - s * w * wf))
        v.append(B.vert(c + s * w * wf))
    v.append(B.vert(rows[2][0]))
    ct = tip or tint
    B.face([v[0], v[1], v[3], v[2]], [tint, tint, tint, tint],
           [(phase, 0), (phase, 0), (phase, 0.55), (phase, 0.55)], smooth_=True)
    B.face([v[2], v[3], v[4]], [tint, tint, ct], [(phase, 0.55), (phase, 0.55), (phase, 1.0)], smooth_=True)

G = Builder()
blades = 0
tries = 0
while blades < 28000 and tries < 400000:
    tries += 1
    r = 33 * math.sqrt(rnd()) ** 1.15
    th = rnd() * math.tau
    cx, cy = r * math.cos(th), r * math.sin(th)
    d = pond_d(cx, cy)
    ch = terrain_h(cx, cy)
    if d < 0.55 or ch < 0.02:
        continue
    if rnd() > 0.35 + 0.65 * smooth(0.5, 3.0, d):
        continue
    clump_tint = jitter(mix(srgb(0x55a030), srgb(0x2f7a1c), rnd()), 0.1)
    if rnd() < 0.07:
        clump_tint = jitter(srgb(0x8aa83a), 0.1)   # dry-ish patches
    hbase = uni(0.36, 0.72) * (1.1 if d < 3.5 else 1.0)
    for _ in range(random.randint(10, 18)):
        x = cx + random.gauss(0, 0.26)
        y = cy + random.gauss(0, 0.26)
        if pond_d(x, y) < 0.45 or path_dist(x, y) < 0.62:
            continue
        z = terrain_h(x, y) - 0.03
        h = hbase * uni(0.6, 1.25)
        add_blade(G, (x, y, z), h, uni(0.028, 0.05), rnd() * math.tau, uni(0.1, 0.55),
                  jitter(clump_tint, 0.12), rnd())
        blades += 1
print("grass blades:", blades)
grass = G.to_obj("Grass", MAT["grass"])

# ------------------------------------------------------------------- reeds
R = Builder()
C_REED = srgb(0x5d8a2e); C_CAT = srgb(0x4a2c16)
reed_n = 0
dock_theta = -math.pi / 2 - 0.35
for _ in range(150):
    th = rnd() * math.tau
    if abs(math.atan2(math.sin(th - dock_theta), math.cos(th - dock_theta))) < 0.3:
        continue
    rr = shore_radius(th) + uni(-1.9, 0.9)
    cx, cy = rr * math.cos(th), rr * math.sin(th)
    for _ in range(random.randint(5, 10)):
        x, y = cx + random.gauss(0, 0.3), cy + random.gauss(0, 0.3)
        z = terrain_h(x, y) - 0.05
        if z > 0.4 or z < -1.0:
            continue
        h = uni(0.9, 1.7)
        yaw, bend = rnd() * math.tau, uni(0.04, 0.22)
        ph = rnd()
        tint = jitter(mix(srgb(0x4a7a22), srgb(0x7ba83a), rnd()), 0.1)
        add_blade(R, (x, y, z), h, 0.045, yaw, bend, tint, ph, taper=0.8)
        reed_n += 1
        if rnd() < 0.35:
            t0, t1 = 0.68, 0.86
            d = Vector((math.cos(yaw), math.sin(yaw), 0))
            p0 = Vector((x, y, z)) + d * (bend * h * t0 * t0) + Vector((0, 0, h * t0))
            p1 = Vector((x, y, z)) + d * (bend * h * t1 * t1) + Vector((0, 0, h * t1))
            # cattail: brown velvet capsule
            ring = []
            for k, (pp, rad) in enumerate(((p0, 0.0), (p0 + (p1 - p0) * 0.25, 0.034), (p0 + (p1 - p0) * 0.8, 0.034), (p1, 0.0))):
                ring.append((pp, rad))
            vv = []
            for pp, rad in ring:
                if rad == 0:
                    vv.append([R.vert(pp)])
                else:
                    vv.append([R.vert(pp + Vector((math.cos(a / 6 * math.tau) * rad, math.sin(a / 6 * math.tau) * rad, 0))) for a in range(6)])
            uvt = [(ph, 0.7), (ph, 0.72), (ph, 0.82), (ph, 0.86)]
            cc = jitter(C_CAT, 0.2)
            for k in range(3):
                a, bb = vv[k], vv[k + 1]
                for i in range(6):
                    j = (i + 1) % 6
                    if len(a) == 1:
                        R.face([a[0], bb[i], bb[j]], cc, [uvt[k]] * 3, smooth_=False)
                    elif len(bb) == 1:
                        R.face([a[i], a[j], bb[0]], cc, [uvt[k]] * 3, smooth_=False)
                    else:
                        R.face([a[i], a[j], bb[j], bb[i]], cc, [uvt[k]] * 4, smooth_=False)
print("reeds:", reed_n)
reeds = R.to_obj("Reeds", MAT["plant"])

# --------------------------------------------------------------- wildflowers
F = Builder()
palette = [0xffffff, 0xfff2a8, 0xffd23f, 0xff9ec4, 0xc9a7ff, 0xff7b6b, 0x9ed0ff]
fl = 0
while fl < 900:
    r = 5 + 26 * math.sqrt(rnd())
    th = rnd() * math.tau
    x, y = r * math.cos(th), r * math.sin(th)
    if pond_d(x, y) < 1.3 or terrain_h(x, y) < 0.05 or path_dist(x, y) < 0.8:
        continue
    # clustered meadows
    if noise.noise(Vector((x * 0.09, y * 0.09, 12.0))) < -0.05:
        continue
    z = terrain_h(x, y) - 0.02
    h = uni(0.3, 0.62)
    yaw, bend = rnd() * math.tau, uni(0.05, 0.3)
    ph = rnd()
    add_blade(F, (x, y, z), h, 0.012, yaw, bend, srgb(0x3d8a2a), ph, taper=0.9)
    d = Vector((math.cos(yaw), math.sin(yaw), 0))
    top = Vector((x, y, z)) + d * (bend * h) + Vector((0, 0, h))
    pc = srgb(random.choice(palette))
    petals = random.choice((5, 6, 8))
    rad = uni(0.045, 0.075)
    ctr = F.vert(top + Vector((0, 0, 0.02)))
    ring = [F.vert(top + Vector((math.cos(i / petals * math.tau) * rad, math.sin(i / petals * math.tau) * rad, -0.012))) for i in range(petals)]
    for i in range(petals):
        F.face([ctr, ring[i], ring[(i + 1) % petals]], [srgb(0xffc933), pc, pc], [(ph, 1.0)] * 3, smooth_=False)
    fl += 1
flowers = F.to_obj("Flowers", MAT["plant"])

# ------------------------------------------------------------------ lily pads
L = Builder()
pads = []
while len(pads) < 46:
    th = rnd() * math.tau
    rr = shore_radius(th) * uni(0.35, 0.97)
    x, y = rr * math.cos(th), rr * math.sin(th)
    if any(math.hypot(x - p[0], y - p[1]) < (p[2] + 0.5) * 1.05 for p in pads):
        continue
    if math.hypot(x - BOAT.x, y - BOAT.y) < 2.2:
        continue
    if abs(math.atan2(math.sin(th - dock_theta), math.cos(th - dock_theta))) < 0.22 and rr > shore_radius(th) - 5:
        continue
    pads.append((x, y, uni(0.3, 0.62)))
C_PAD1 = srgb(0x2f7a2a); C_PAD2 = srgb(0x5ea83a)
for (x, y, rad) in pads:
    notch = rnd() * math.tau
    seg = 22
    ctr = L.vert(Vector((x, y, WATER_Z + 0.015)))
    ring = []
    for i in range(seg + 1):
        a = notch + 0.2 + i / seg * (math.tau - 0.4)
        ring.append(L.vert(Vector((x + math.cos(a) * rad, y + math.sin(a) * rad, WATER_Z + 0.04))))
    cc = jitter(mix(C_PAD1, C_PAD2, rnd()), 0.1)
    for i in range(seg):
        L.face([ctr, ring[i], ring[i + 1]], [cc, mix(cc, C_PAD2, 0.35), mix(cc, C_PAD2, 0.35)], smooth_=False)
lilypads = L.to_obj("LilyPads", MAT["plant"])

# --------------------------------------------------------------------- lotus
LO = Builder()
for k in range(20):
    for _ in range(50):
        th = rnd() * math.tau
        rr = shore_radius(th) * uni(0.4, 0.9)
        x, y = rr * math.cos(th), rr * math.sin(th)
        if all(math.hypot(x - p[0], y - p[1]) > 1.0 for p in pads[:0]):
            break
    z0 = WATER_Z + 0.02
    scale = uni(0.8, 1.2)
    petal_col = random.choice([(0xffd6e7, 0xff6fa8), (0xffffff, 0xffb3d1), (0xfff0f5, 0xf55f9b)])
    # stalk
    ph = rnd() * math.tau
    for ringi, (cnt, tilt, plen, pw) in enumerate(((8, 1.1, 0.34, 0.12), (7, 0.7, 0.3, 0.11), (5, 0.35, 0.24, 0.09))):
        for i in range(cnt):
            a = ph + i / cnt * math.tau + ringi * 0.4
            dirv = Vector((math.cos(a), math.sin(a), 0))
            sidev = Vector((-math.sin(a), math.cos(a), 0))
            base = Vector((x, y, z0 + 0.04 + ringi * 0.015))
            up = Vector((0, 0, 1))
            axis = (dirv * math.sin(tilt) + up * math.cos(tilt))
            mid = base + axis * plen * scale * 0.55 + dirv * 0.0
            tipp = base + (dirv * math.sin(tilt * 0.65 + 0.15) + up * math.cos(tilt * 0.65 + 0.15)) * plen * scale
            vb = LO.vert(base)
            vl = LO.vert(mid - sidev * pw * scale)
            vr = LO.vert(mid + sidev * pw * scale)
            vt = LO.vert(tipp)
            c0, c1 = srgb(petal_col[0]), srgb(petal_col[1])
            LO.face([vb, vr, vt, vl], [c0, mix(c0, c1, 0.4), c1, mix(c0, c1, 0.4)], smooth_=True)
    add_cone(LO, (x, y, z0 + 0.04), 0.04 * scale, 0.07 * scale, 8, srgb(0xffd23f), srgb(0xffb000))
lotus = LO.to_obj("Lotus", MAT["leaf"])
lotus.data.materials[0] = MAT["leaf"]

# --------------------------------------------------------------------- rocks
RK = Builder()
def rock_col(base, moss):
    def f(pos, n):
        c = jitter(base, 0.12)
        k = smooth(0.35, 0.85, n.z) * (0.4 + 0.6 * (noise.noise(pos * 3.0) * 0.5 + 0.5))
        return mix(c, moss, k * 0.85)
    return f

rock_spots = []
for _ in range(32):
    th = rnd() * math.tau
    rr = shore_radius(th) + uni(-1.2, 3.8)
    x, y = rr * math.cos(th), rr * math.sin(th)
    if abs(math.atan2(math.sin(th - dock_theta), math.cos(th - dock_theta))) < 0.3:
        continue
    rock_spots.append((x, y, uni(0.25, 0.8) * (1.8 if rnd() < 0.12 else 1.0)))
for (x, y, s) in rock_spots:
    COLL.append((x, y, s * 1.05))
    AOC.append((x, y, s * 1.7, 0.32))
    z = terrain_h(x, y)
    base = jitter(mix(srgb(0x7d7a72), srgb(0x5a5750), rnd()), 0.1)
    add_ico(RK, (x, y, z + s * 0.2), (s * uni(1, 1.5), s * uni(0.9, 1.3), s * uni(0.55, 0.9)), 3,
            rock_col(base, srgb(0x4f8a2a)), amp=0.28, seed=rnd() * 50, rot=rnd() * 6)
    for _ in range(random.randint(0, 3)):
        sx, sy = x + uni(-1, 1) * s * 1.4, y + uni(-1, 1) * s * 1.4
        ss = s * uni(0.25, 0.5)
        add_ico(RK, (sx, sy, terrain_h(sx, sy) + ss * 0.2), (ss * 1.2, ss, ss * 0.7), 2,
                rock_col(base, srgb(0x4f8a2a)), amp=0.3, seed=rnd() * 50)
# stepping stones in the shallows
for k in range(6):
    th = dock_theta + 0.55 + k * 0.1
    rr = shore_radius(th) - 0.8 - k * 0.65
    x, y = rr * math.cos(th) + uni(-.2, .2), rr * math.sin(th) + uni(-.2, .2)
    add_ico(RK, (x, y, WATER_Z + 0.02), (0.42, 0.38, 0.2), 3, rock_col(srgb(0x8a8780), srgb(0x4f8a2a)), amp=0.2, seed=rnd() * 50)
rocks = RK.to_obj("Rocks", MAT["rock"])

# --------------------------------------------------------------------- trees
TR = Builder()   # trunks
LF = Builder()   # foliage

def tree(x, y, kind, s):
    z = terrain_h(x, y) - 0.1
    lean = Vector((uni(-0.35, 0.35), uni(-0.35, 0.35), 0))
    if kind == "cherry":
        # lean toward the pond
        lean = Vector((-x, -y, 0)).normalized() * 0.9
    trunk_c0, trunk_c1 = srgb(0x4a3322), srgb(0x6b4a30)
    h = {"oak": 4.2, "cherry": 3.6, "pine": 7.0, "birch": 5.0}[kind] * s
    p0 = Vector((x, y, z))
    pm = p0 + Vector((0, 0, h * 0.55)) + lean * h * 0.25
    p1 = p0 + Vector((0, 0, h)) + lean * h * 0.55
    if kind == "birch":
        trunk_c0, trunk_c1 = srgb(0xd9d4c4), srgb(0xb7b0a0)
    if kind == "pine":
        trunk_c0, trunk_c1 = srgb(0x3b2a1e), srgb(0x4d3828)
    rw = (0.34 if kind != "pine" else 0.3) * s
    COLL.append((x, y, rw * 1.25 + 0.2))
    AOC.append((x, y, (1.6 if kind == 'pine' else 2.6) * s, 0.42))
    AOC.append((x, y, 0.9 * s, 0.25))
    add_tube(TR, p0 - Vector((0, 0, 0.3)), pm, rw * 1.2, rw * 0.8, 8, trunk_c0, trunk_c1)
    add_tube(TR, pm, p1, rw * 0.8, rw * 0.45, 8, trunk_c1, trunk_c1)
    if kind in ("oak", "cherry"):
        for _ in range(3):  # branches
            a = rnd() * math.tau
            bs = pm + Vector((0, 0, h * 0.1))
            be = bs + Vector((math.cos(a) * 1.6 * s, math.sin(a) * 1.6 * s, 1.0 * s))
            add_tube(TR, bs, be, rw * 0.38, rw * 0.15, 6, trunk_c1, trunk_c1)
    if kind == "pine":
        for i in range(6):
            t = i / 6
            cz = z + h * (0.25 + t * 0.7)
            rr = (2.0 - t * 1.6) * s
            col0 = jitter(srgb(0x1f4a2a), 0.12)
            col1 = jitter(srgb(0x35703a), 0.1)
            add_cone(LF, (x + lean.x * h * 0.55 * (cz - z) / h, y + lean.y * h * 0.55 * (cz - z) / h, cz), rr, 1.8 * s, 8, col0, col1, rot=rnd())
        return
    if kind == "cherry":
        cols = [srgb(0xffb7d0), srgb(0xff8fb8), srgb(0xffd6e6), srgb(0xf9a3c7)]
    elif kind == "birch":
        cols = [srgb(0x9ac03a), srgb(0xb5d447), srgb(0x7aa82a)]
    else:
        cols = [srgb(0x2f7a22), srgb(0x3f9a2c), srgb(0x56b03a), srgb(0x25641c)]
    for i in range(7):
        a = rnd() * math.tau
        rad = uni(0.4, 1.9) * s
        c = p1 + Vector((math.cos(a) * rad, math.sin(a) * rad, uni(-0.5, 1.4) * s))
        rr = uni(1.3, 2.1) * s
        base = random.choice(cols)
        add_ico(LF, c, (rr, rr, rr * 0.82), 3,
                lambda pos, n, base=base: mix(jitter(base, 0.08), (min(1, base[0] * 1.6), min(1, base[1] * 1.45), base[2] * 1.2), smooth(0.0, 1.0, n.z) * 0.6),
                amp=0.3, seed=rnd() * 40)

tree(11.8, 4.2, "cherry", 1.15)
tree(-12.5, 5.5, "oak", 1.05)
tree(-8.5, 10.8, "birch", 1.0)
tree(7.2, 11.6, "oak", 1.1)
spots = 0
tries = 0
while spots < 30 and tries < 5000:
    tries += 1
    r = uni(27, 44)
    th = rnd() * math.tau
    x, y = r * math.cos(th), r * math.sin(th)
    if math.hypot(x - 11.8, y - 4.2) < 4 or terrain_h(x, y) < 0.2:
        continue
    kind = random.choice(["oak", "oak", "pine", "pine", "birch", "cherry"])
    tree(x, y, kind, uni(0.9, 1.7))
    spots += 1
trunks = TR.to_obj("Trunks", MAT["wood"])
foliage = LF.to_obj("Foliage", MAT["leaf"])

# --------------------------------------------------------------- dock + lantern
DK = Builder()
GL = Builder()
u = Vector((-math.cos(dock_theta), -math.sin(dock_theta), 0))        # towards pond centre
sdir = Vector((-u.y, u.x, 0))
S0 = Vector((shore_radius(dock_theta) * math.cos(dock_theta), shore_radius(dock_theta) * math.sin(dock_theta), 0))
yaw = math.atan2(u.y, u.x)
DECK_Z = 0.16
wood_a, wood_b = srgb(0x8a6038), srgb(0x6a4626)
for i in range(26):
    s = -1.4 + i * 0.27
    c = S0 + u * s
    add_box(DK, (c.x, c.y, DECK_Z), (0.23, 1.4, 0.07), jitter(mix(wood_a, wood_b, rnd()), 0.12), rot=yaw)
for off in (-0.5, 0.5):
    a = S0 + u * -1.4 + sdir * off
    b_ = S0 + u * 5.6 + sdir * off
    add_box(DK, ((a.x + b_.x) / 2, (a.y + b_.y) / 2, DECK_Z - 0.09), (7.0, 0.12, 0.12), wood_b, rot=yaw)
for s in (-0.9, 0.6, 2.1, 3.6, 5.0):
    for off in (-0.62, 0.62):
        c = S0 + u * s + sdir * off
        add_box(DK, (c.x, c.y, DECK_Z - 0.55), (0.14, 0.14, 1.7), srgb(0x5b3d22), rot=yaw)
# rope rail posts on the first half
for s in (-0.9, 2.1, 5.0):
    for off in (-0.62, 0.62):
        c = S0 + u * s + sdir * off
        add_box(DK, (c.x, c.y, DECK_Z + 0.35), (0.12, 0.12, 0.9), srgb(0x6a4626), rot=yaw)
for off in (-0.62, 0.62):
    a = S0 + u * -0.9 + sdir * off
    b_ = S0 + u * 5.0 + sdir * off
    add_tube(DK, (a.x, a.y, DECK_Z + 0.62), (b_.x, b_.y, DECK_Z + 0.5), 0.022, 0.022, 5, srgb(0xc9b27a), srgb(0xc9b27a))
dock = DK.to_obj("Dock", MAT["wood"])

# stone toro lantern
lp = S0 + u * -2.2 + sdir * 1.7
lz = terrain_h(lp.x, lp.y)
stone, stone2 = srgb(0x8d8a82), srgb(0x6e6b64)
LT = Builder()
add_box(LT, (lp.x, lp.y, lz + 0.1), (0.7, 0.7, 0.2), stone2)
add_tube(LT, (lp.x, lp.y, lz + 0.2), (lp.x, lp.y, lz + 0.95), 0.13, 0.11, 8, stone, stone)
add_box(LT, (lp.x, lp.y, lz + 1.02), (0.5, 0.5, 0.1), stone2)
add_box(LT, (lp.x, lp.y, lz + 1.35), (0.46, 0.46, 0.06), stone2)
for sx in (-1, 1):
    for sy in (-1, 1):
        add_box(LT, (lp.x + sx * 0.21, lp.y + sy * 0.21, lz + 1.2), (0.06, 0.06, 0.3), stone)
add_cone(LT, (lp.x, lp.y, lz + 1.38), 0.5, 0.35, 4, stone2, stone, rot=math.pi / 4)
add_ico(LT, (lp.x, lp.y, lz + 1.8), (0.07, 0.07, 0.09), 2, lambda p, n: stone)
lantern = LT.to_obj("Lantern", MAT["rock"])
add_box(GL, (lp.x, lp.y, lz + 1.2), (0.34, 0.34, 0.3), srgb(0xffc266))
lanternglow = GL.to_obj("LanternGlow", MAT["glow"])
LANTERN_POS = (lp.x, lp.y, lz + 1.2)
COLL.append((lp.x, lp.y, 0.45))
AOC.append((lp.x, lp.y, 0.8, 0.25))

# ------------------------------------------------------------------- critters
def add_shape(B, c, sub, posfn, colfn, smooth_=True):
    """Icosphere whose unit normals are mapped through posfn(n) -> local offset. colfn(n, p) -> colour."""
    tmp = bmesh.new()
    bmesh.ops.create_icosphere(tmp, subdivisions=sub, radius=1.0)
    tmp.verts.index_update()
    out = {}
    for v in tmp.verts:
        n = v.co.copy().normalized()
        p = posfn(n)
        out[v.index] = (B.vert(Vector(c) + p), colfn(n, p))
    for f in tmp.faces:
        B.face([out[v.index][0] for v in f.verts], [out[v.index][1] for v in f.verts], smooth_=smooth_)
    tmp.free()

def fan(B, center, pts, cols, double=True):
    """Triangle fan; pts are rings: pts[ring][i]."""
    pass

def duck(name, scale, kind, pos):
    D = Builder()
    P = {
        "drake": dict(body=0xbdb9b0, back=0x8f897c, breast=0x6e3a22, head=0x1d7a52, ring=0xffffff, tail=0x1c1c1e, bill=0xe8c33a, wing=0x857d70, spec=0x3550d8, belly=0xdedad2, speck=None),
        "hen": dict(body=0xa58058, back=0x7c5a38, breast=0xb08a5c, head=0x9a764c, ring=0x9a764c, tail=0x7c5a38, bill=0xd8892a, wing=0x6e5234, spec=0x3550d8, belly=0xc2a074, speck=0x4a3420),
        "chick": dict(body=0xffd54a, back=0x8a6a2e, breast=0xffe48a, head=0xffd54a, ring=0xffd54a, tail=0x8a6a2e, bill=0x4a3a24, wing=0x9a7a38, spec=0x9a7a38, belly=0xfff0b0, speck=None),
    }[kind]
    C = {k: (srgb(v) if v is not None else None) for k, v in P.items()}
    L, W, H = 0.30, 0.165, 0.125
    def body_pos(n):
        t = (n.y + 1) / 2                                     # 0 = breast, 1 = tail
        w = W * (1 - 0.42 * t ** 2.0) * (1 + 0.08 * (1 - t))
        z = n.z * (H * (1.05 + 0.12 * (1 - t)) if n.z > 0 else H * 0.72)
        z += 0.085 * max(0.0, (t - 0.55) / 0.45) ** 2 * (0.55 + 0.45 * n.z)
        return Vector((n.x * w, n.y * L, z))
    def body_col(n, p):
        t = (n.y + 1) / 2
        c = mix(C["belly"], C["body"], smooth(-0.55, 0.15, n.z))
        c = mix(c, C["back"], smooth(0.45, 0.95, n.z) * 0.65)
        c = mix(C["breast"], c, smooth(0.2, 0.36, t)) if n.z > -0.4 else c
        c = mix(c, C["tail"], smooth(0.84, 0.95, t))
        if C["speck"] and noise.noise(Vector((p.x * 70, p.y * 70, p.z * 70))) > 0.25:
            c = mix(c, C["speck"], 0.55)
        return c
    add_shape(D, (0, 0, 0.065), 3, body_pos, body_col)
    for sx in (-1, 1):                                        # folded wings
        def wpos(n, sx=sx):
            return Vector((sx * 0.112 + n.x * 0.045, 0.05 + n.y * 0.165, 0.135 + n.z * 0.04 + 0.035 * n.y))
        def wcol(n, p, sx=sx):
            c = C["wing"]
            if kind != "chick" and -0.15 < n.y < 0.25 and n.x * sx > 0.25:
                c = C["spec"] if -0.05 < n.y < 0.18 else srgb(0xf4f4f4)
            return mix(c, C["back"], smooth(0.4, 1.0, n.z) * 0.3)
        add_shape(D, (0, 0, 0), 2, wpos, wcol)
    add_tube(D, (0, -0.19, 0.15), (0, -0.25, 0.3), 0.06, 0.047, 10, C["breast"], C["ring"])   # neck
    def hpos(n):
        return Vector((n.x * 0.058, -0.26 + n.y * 0.074 - 0.012 * max(0, -n.y), 0.33 + n.z * 0.062))
    def hcol(n, p):
        c = C["head"]
        if kind == "chick" and n.z > 0.45: c = C["back"]
        if kind == "hen" and abs(n.z - 0.15) < 0.12 and n.y < 0.2: c = mix(c, srgb(0x3a2a18), 0.7)   # eye stripe
        return c
    add_shape(D, (0, 0, 0), 3, hpos, hcol)
    def bpos(n):
        k = 1 + 0.35 * max(0.0, -n.y)
        return Vector((n.x * 0.026 * k, -0.345 + n.y * 0.058, 0.305 + n.z * 0.012))
    add_shape(D, (0, 0, 0), 2, bpos, lambda n, p: mix(C["bill"], srgb(0x1a1a1a), smooth(-0.8, -0.98, n.y) * 0.8))
    if kind == "drake":
        add_tube(D, (0, -0.21, 0.255), (0, -0.226, 0.275), 0.052, 0.05, 10, C["ring"], C["ring"])     # white collar
        add_tube(D, (0, 0.29, 0.2), (0, 0.31, 0.27), 0.012, 0.004, 6, C["tail"], C["tail"])          # curled drake feather
    for sx in (-1, 1):
        add_ico(D, (sx * 0.05, -0.285, 0.35), (0.012, 0.012, 0.012), 1, lambda p, n: srgb(0x0e0e10))
    o = D.to_obj(name, MAT["animal"])
    o.scale = (scale, scale, scale)
    o.location = pos
    return o

duck("Duck_Drake", 1.0, "drake", (-1.5, 2.0, WATER_Z))
duck("Duck_Hen", 0.95, "hen", (-2.8, 2.6, WATER_Z))
for i in range(4):
    duck("Duck_Chick%d" % i, 0.42, "chick", (-3.6 - i * 0.3, 2.9 + i * 0.2, WATER_Z))

def koi(name, scheme):
    K = Builder()
    pal = {
        "orange": (srgb(0xff6a1a), srgb(0xfff4ea), srgb(0xff8a3a)),
        "kohaku": (srgb(0xfff4ea), srgb(0xe8391a), srgb(0xffffff)),
        "gold":   (srgb(0xffc21a), srgb(0xfff0a0), srgb(0xffd45a)),
        "sanke":  (srgb(0xfffaf0), srgb(0xe8451a), srgb(0x1e1e22)),
        "black":  (srgb(0x22252c), srgb(0xff7a2a), srgb(0x3a3e48)),
        "platinum": (srgb(0xe9eef2), srgb(0xc9d3dc), srgb(0xffffff)),
    }[scheme]
    seed = hash(name) % 100
    L, W, H = 0.3, 0.07, 0.062
    def bpos(n):
        t = (n.y + 1) / 2                                      # 0 head, 1 tail
        w = W * (1 - 0.82 * t ** 1.5) * (0.82 + 0.18 * min(1, t * 6))
        h = H * (1 - 0.75 * t ** 1.6) * (0.85 + 0.15 * min(1, t * 6))
        return Vector((n.x * w, n.y * L, n.z * (h if n.z > 0 else h * 0.85)))
    def bcol(n, p):
        k = noise.noise(Vector((p.x * 9 + seed, p.y * 6, p.z * 9)))
        c = pal[0] if k > -0.05 else pal[1]
        if scheme == "sanke" and noise.noise(Vector((p.x * 14, p.y * 11 + seed, 3.0))) > 0.38: c = pal[2]
        return mix(c, srgb(0xf6f2ea), smooth(-0.3, -0.9, n.z) * 0.6)
    add_shape(K, (0, 0, 0), 3, bpos, bcol)
    def fin(pts, col):
        vs = [K.vert(Vector(p)) for p in pts]
        cs = [col] * len(vs)
        K.face(vs, cs, smooth_=False)
        K.face(list(reversed(vs)), list(reversed(cs)), smooth_=False)
    fc = mix(pal[2], srgb(0xffffff), 0.35)
    # tail: two vertical lobes
    fin([(0, 0.27, 0.0), (0, 0.48, 0.11), (0, 0.44, 0.02)], fc)
    fin([(0, 0.27, 0.0), (0, 0.44, -0.02), (0, 0.48, -0.1)], fc)
    fin([(0, 0.27, 0.0), (0, 0.44, 0.02), (0, 0.44, -0.02)], fc)
    # dorsal fin along the back
    fin([(0, -0.06, 0.055), (0, 0.16, 0.035), (0, 0.12, 0.1), (0, -0.02, 0.1)], fc)
    for sx in (-1, 1):                                         # pectoral and pelvic fins
        fin([(sx * 0.05, -0.15, -0.025), (sx * 0.17, -0.05, -0.05), (sx * 0.13, 0.0, -0.045), (sx * 0.05, -0.08, -0.03)], fc)
        fin([(sx * 0.03, 0.06, -0.035), (sx * 0.09, 0.14, -0.05), (sx * 0.04, 0.14, -0.04)], fc)
    for sx in (-1, 1):                                         # eyes + barbels
        add_ico(K, (sx * 0.045, -0.235, 0.018), (0.009, 0.009, 0.009), 1, lambda p, n: srgb(0x111111))
        add_tube(K, (sx * 0.02, -0.29, -0.012), (sx * 0.045, -0.32, -0.03), 0.003, 0.002, 4, pal[0], pal[0])
    return K.to_obj(name, MAT["animal"])

for i, sch in enumerate(("orange", "kohaku", "gold", "sanke", "black", "platinum", "kohaku")):
    o = koi("Koi_%d" % i, sch)
    o.location = (0, 0, -0.6)

# butterflies: one wing mesh per species, drawn as a polar fan so colour can vary across the wing
BUTTERFLIES = {
    "monarch":   dict(base=0xf08a1a, edge=0x16120e, inner=0xc8560e, dot=0xfff4e0, vein=True),
    "morpho":    dict(base=0x2f7dff, edge=0x0e1424, inner=0x6fd0ff, dot=0xffffff, vein=False),
    "brimstone": dict(base=0xf7ec7a, edge=0xc9bb3a, inner=0xfff6b0, dot=0xff9a2a, vein=False),
    "peacock":   dict(base=0xa8281e, edge=0x3a1a14, inner=0x7a1c14, dot=0x5ab0ff, vein=False, eyes=True),
}
def butterfly_wing(spname, d):
    W = Builder()
    base, edge, inner, dot = srgb(d["base"]), srgb(d["edge"]), srgb(d["inner"]), srgb(d["dot"])
    rings = (0.0, 0.3, 0.62, 0.84, 0.93, 1.0)
    def wing(a0, a1, steps, R):
        grid = []
        for i in range(steps + 1):
            a = a0 + (a1 - a0) * i / steps
            r = R(a)
            col_row = []
            for k in rings:
                p = Vector((math.cos(a) * r * k, -math.sin(a) * r * k, 0.0))
                if k < 0.32: c = mix(inner, base, k / 0.32)
                elif k < 0.84: c = base
                elif k < 0.95: c = edge if not (d.get("eyes") and 0.3 < (a - a0) / (a1 - a0) < 0.6) else dot
                else: c = edge
                if k > 0.9 and i % 2 == 0 and k < 0.97: c = dot if not d.get("eyes") else edge
                if d.get("vein") and i % 3 == 0 and 0.2 < k < 0.85: c = mix(c, edge, 0.75)
                col_row.append((W.vert(p), c))
            grid.append(col_row)
        for i in range(steps):
            for k in range(len(rings) - 1):
                a, b_, c_, d_ = grid[i][k], grid[i + 1][k], grid[i + 1][k + 1], grid[i][k + 1]
                if k == 0:
                    W.face([a[0], c_[0], d_[0]], [a[1], c_[1], d_[1]], smooth_=False)
                    W.face([a[0], d_[0], c_[0]], [a[1], d_[1], c_[1]], smooth_=False)
                else:
                    W.face([a[0], b_[0], c_[0], d_[0]], [a[1], b_[1], c_[1], d_[1]], smooth_=False)
                    W.face([d_[0], c_[0], b_[0], a[0]], [d_[1], c_[1], b_[1], a[1]], smooth_=False)
    deg = math.radians
    wing(deg(4), deg(78), 9, lambda a: 0.125 * (0.7 + 0.3 * math.sin((a - deg(4)) / deg(74) * math.pi) ** 0.6) * (1.12 if a > deg(50) else 1.0))
    wing(deg(-72), deg(0), 7, lambda a: 0.092 * (0.75 + 0.25 * math.sin((a + deg(72)) / deg(72) * math.pi)))
    o = W.to_obj("BflyWing_" + spname, MAT["animal"])
    o.location = (0, 0, -50)
for spn, d in BUTTERFLIES.items():
    butterfly_wing(spn, d)
BB = Builder()
add_ico(BB, (0, 0.01, 0), (0.011, 0.05, 0.011), 2, lambda p, n: srgb(0x1c120a))
add_ico(BB, (0, -0.055, 0.003), (0.012, 0.012, 0.012), 2, lambda p, n: srgb(0x1c120a))
for sx in (-1, 1):
    add_tube(BB, (sx * 0.004, -0.062, 0.008), (sx * 0.03, -0.11, 0.035), 0.0018, 0.0018, 4, srgb(0x1c120a), srgb(0x1c120a))
    add_ico(BB, (sx * 0.03, -0.11, 0.035), (0.004, 0.004, 0.004), 1, lambda p, n: srgb(0x1c120a))
bfly_body = BB.to_obj("BflyBody", MAT["animal"])
bfly_body.location = (0, 0, -50)

# grey heron (body + two legs that swing when it walks)
HB = Builder()
grey, dgrey, white, black, yel = srgb(0x9aa3ab), srgb(0x5f6a74), srgb(0xeef0ee), srgb(0x18191c), srgb(0xe8b23a)
def hb_pos(n):
    t = (n.y + 1) / 2
    w = 0.11 * (1 - 0.45 * t ** 1.5)
    return Vector((n.x * w, 0.02 + n.y * 0.27, 0.0 + n.z * 0.12 * (1 - 0.3 * t) - 0.07 * n.y))
add_shape(HB, (0, 0, 0.1), 3, hb_pos, lambda n, p: mix(white, mix(grey, dgrey, smooth(0.2, 0.9, n.z)), smooth(-0.5, 0.0, n.y + n.z * 0.3)))
neck = [(0, -0.2, 0.17), (0, -0.25, 0.3), (0, -0.19, 0.4), (0, -0.23, 0.5), (0, -0.3, 0.55)]
for i in range(len(neck) - 1):
    add_tube(HB, neck[i], neck[i + 1], 0.045 - i * 0.004, 0.041 - i * 0.004, 10, white, white)
add_shape(HB, (0, -0.33, 0.57), 3, lambda n: Vector((n.x * 0.034, n.y * 0.058, n.z * 0.036)), lambda n, p: black if (n.z > 0.35 and abs(n.x) < 0.8) else white)
add_tube(HB, (0, -0.31, 0.6), (0, -0.2, 0.63), 0.008, 0.002, 5, black, black)          # crest plume
add_shape(HB, (0, -0.43, 0.565), 2, lambda n: Vector((n.x * 0.011, n.y * 0.075 - 0.01, n.z * 0.011)), lambda n, p: yel)
for sx in (-1, 1):
    add_ico(HB, (sx * 0.026, -0.355, 0.585), (0.007, 0.007, 0.007), 1, lambda p, n: yel)
add_shape(HB, (0, 0.27, 0.06), 2, lambda n: Vector((n.x * 0.06, n.y * 0.08, n.z * 0.03)), lambda n, p: dgrey)  # tail
heron = HB.to_obj("HeronBody", MAT["animal"]); heron.location = (0, 0, -50)
for side, sx in (("L", -1), ("R", 1)):
    HL = Builder()
    leg = srgb(0x8a7a5a)
    add_tube(HL, (0, 0, 0), (0, 0.02, -0.32), 0.014, 0.011, 6, leg, leg)
    add_tube(HL, (0, 0.02, -0.32), (0, -0.01, -0.62), 0.011, 0.009, 6, leg, leg)
    for a in (-0.5, 0.0, 0.5):
        add_tube(HL, (0, -0.01, -0.62), (math.sin(a) * 0.09, -0.01 - math.cos(a) * 0.09, -0.62), 0.006, 0.004, 4, leg, leg)
    add_tube(HL, (0, -0.01, -0.62), (0, 0.06, -0.62), 0.005, 0.003, 4, leg, leg)
    o = HL.to_obj("HeronLeg" + side, MAT["animal"]); o.location = (0, 0, -50)

# rabbit
RB = Builder()
fur, fur2, wht, pink = srgb(0x8a7258), srgb(0x6e5a44), srgb(0xf2ece4), srgb(0xd8a0a0)
add_shape(RB, (0, 0.03, 0.11), 3, lambda n: Vector((n.x * 0.085 * (1 + 0.15 * max(0, n.y)), n.y * 0.13, n.z * 0.095)), lambda n, p: mix(wht, mix(fur, fur2, smooth(0.3, 1, n.z)), smooth(-0.7, -0.2, n.z)))
add_shape(RB, (0, -0.11, 0.18), 3, lambda n: Vector((n.x * 0.055, n.y * 0.065, n.z * 0.055)), lambda n, p: mix(wht, fur, smooth(-0.6, -0.1, n.z)))
for sx in (-1, 1):
    add_shape(RB, (sx * 0.025, -0.085, 0.27), 2, lambda n, sx=sx: Vector((n.x * 0.018, n.y * 0.022 + n.z * 0.02, n.z * 0.075)), lambda n, p, sx=sx: pink if (n.y < -0.3 and abs(n.x) < 0.6) else fur)
    add_ico(RB, (sx * 0.035, -0.14, 0.2), (0.009, 0.009, 0.009), 1, lambda p, n: srgb(0x111111))
    add_shape(RB, (sx * 0.06, 0.08, 0.06), 2, lambda n: Vector((n.x * 0.04, n.y * 0.08, n.z * 0.055)), lambda n, p: fur)
add_ico(RB, (0, -0.172, 0.17), (0.008, 0.006, 0.006), 1, lambda p, n: pink)
add_ico(RB, (0, 0.17, 0.13), (0.035, 0.035, 0.035), 2, lambda p, n: wht)
rabbit = RB.to_obj("Rabbit", MAT["animal"]); rabbit.location = (0, 0, -50)


# ================================================================ extra props
def loc(base, yaw_, p):
    v = rotz(Vector((p[0], p[1], 0)), yaw_)
    return Vector((base.x + v.x, base.y + v.y, base.z + p[2]))

def quad(B, pts, col):
    B.face([B.vert(Vector(p)) for p in pts], col, smooth_=False)

MAT["shop"] = vc_mat("ShopMat", 0.8, double=True)

# ---- tackle shop (stall with striped awning) ------------------------------
S_yaw = math.atan2(_u.y, _u.x)            # local +x points toward the pond
gz = terrain_h(SHOP.x, SHOP.y)
SBASE = Vector((SHOP.x, SHOP.y, gz))
SB, SG = Builder(), Builder()
wood1, wood2, wood3 = srgb(0xa0703f), srgb(0x6e4a2a), srgb(0xc9a36a)
def L(x, y, z):
    return loc(SBASE, S_yaw, (x, y, z))
def sbox(x, y, z, sx, sy, sz, col):
    add_box(SB, L(x, y, z), (sx, sy, sz), col, rot=S_yaw)
sbox(0, 0, 0.06, 3.4, 3.4, 0.12, wood2)                       # floor
sbox(-1.55, 0, 1.2, 0.12, 3.2, 2.2, wood1)                    # back wall
sbox(0, -1.6, 1.2, 3.2, 0.12, 2.2, wood1)
sbox(0, 1.6, 1.2, 3.2, 0.12, 2.2, wood1)
sbox(1.3, 0, 0.62, 0.7, 3.1, 1.0, wood2)                      # counter
sbox(1.3, 0, 1.15, 1.0, 3.3, 0.1, wood3)
sbox(-1.35, 0, 1.2, 0.3, 2.8, 0.08, wood2)                    # shelves
sbox(-1.35, 0, 1.85, 0.3, 2.8, 0.08, wood2)
jar_cols = [0xe85d4a, 0xf2b632, 0x6fc2c9, 0x7fd36b, 0xd98ba6, 0xb987ff, 0xffffff]
for k in range(7):
    c = srgb(jar_cols[k])
    add_tube(SB, L(-1.35, -1.2 + k * 0.4, 1.24), L(-1.35, -1.2 + k * 0.4, 1.5), 0.09, 0.09, 8, c, c, cap=True)
    add_tube(SB, L(-1.35, -1.2 + k * 0.4, 1.89), L(-1.35, -1.2 + k * 0.4, 2.1), 0.08, 0.08, 8, srgb(jar_cols[(k + 3) % 7]), srgb(jar_cols[(k + 3) % 7]), cap=True)
for sy in (-1.75, 1.75):                                      # front posts
    add_tube(SB, L(2.0, sy, 0.12), L(2.0, sy, 2.4), 0.07, 0.07, 8, wood2, wood2)
quad(SB, [L(-1.7, -1.9, 2.65), L(-1.7, 1.9, 2.65), L(2.1, 1.9, 2.25), L(2.1, -1.9, 2.25)], srgb(0x7a3b2a))
quad(SB, [L(-1.7, -1.9, 2.55), L(2.1, -1.9, 2.15), L(2.1, 1.9, 2.15), L(-1.7, 1.9, 2.55)], wood2)
red, cream = srgb(0xd6483a), srgb(0xf6ead2)
nstr = 12
for i in range(nstr):
    y0 = -1.9 + 3.8 * i / nstr
    y1 = y0 + 3.8 / nstr
    c = red if i % 2 == 0 else cream
    quad(SB, [L(2.1, y0, 2.25), L(2.1, y1, 2.25), L(2.9, y1, 1.9), L(2.9, y0, 1.9)], c)
    quad(SB, [L(2.9, y0, 1.9), L(2.9, y1, 1.9), L(2.9, (y0 + y1) / 2, 1.68)], c)
add_box(SB, L(2.05, -1.55, 1.5), (0.06, 0.9, 0.5), wood3, rot=S_yaw)   # sign board
add_ico(SB, L(2.1, -1.55, 1.5), (0.03, 0.28, 0.12), 3, lambda p, n: srgb(0xe8772a))
add_ico(SB, L(2.1, -1.31, 1.5), (0.03, 0.09, 0.06), 2, lambda p, n: srgb(0xe8772a))
for k in range(4):                                             # hanging fish
    fx, fy = 2.65, -1.3 + k * 0.85
    add_tube(SB, L(fx, fy, 1.88), L(fx, fy, 1.6), 0.008, 0.008, 4, srgb(0xc9b27a), srgb(0xc9b27a))
    add_ico(SB, L(fx, fy, 1.45), (0.04, 0.09, 0.17), 3, lambda p, n, k=k: mix(srgb(0xb8c4c9), srgb(0xf2b632), (k % 2) * 0.7 + smooth(0.2, 0.9, n.z) * 0.2))
for (bx, by) in ((-0.5, 2.25), (0.4, 2.35)):                   # barrels
    add_tube(SB, L(bx, by, 0.12), L(bx, by, 0.95), 0.34, 0.34, 10, wood1, wood1, cap=True)
    add_tube(SB, L(bx, by, 0.35), L(bx, by, 0.4), 0.355, 0.355, 10, wood2, wood2)
    add_tube(SB, L(bx, by, 0.7), L(bx, by, 0.75), 0.355, 0.355, 10, wood2, wood2)
sbox(0.6, -2.3, 0.37, 0.7, 0.7, 0.5, wood3)                    # crates
sbox(0.6, -2.3, 0.87, 0.55, 0.55, 0.5, wood1)
sbox(-0.5, -2.3, 0.3, 0.6, 0.6, 0.36, wood1)
shop = SB.to_obj("Shop", MAT["shop"])
add_box(SG, L(1.8, 0.0, 1.95), (0.24, 0.24, 0.3), srgb(0xffc266))
add_box(SG, L(1.8, 0.0, 2.15), (0.3, 0.3, 0.04), srgb(0x3a2a1c))
shopglow = SG.to_obj("ShopGlow", MAT["glow"])
shop_anchor = bpy.data.objects.new("ShopAnchor", None)
bpy.context.scene.collection.objects.link(shop_anchor)
shop_anchor.location = (SHOP.x, SHOP.y, gz + 1.6)
COLL.append((SHOP.x, SHOP.y, 2.25))
AOC.append((SHOP.x, SHOP.y, 3.3, 0.5))

# ---- rowboat moored beside the dock -----------------------------------------
BT = Builder()
boat_yaw = math.atan2(_u.y, _u.x) - math.pi / 2   # local +y (bow) -> toward pond
HL, HW = 1.5, 0.64
NST, NPT = 13, 11
def hull(t, s, inset):
    w = HW * (1 - abs(t) ** 2.3) ** 0.65
    sheer = 0.17 + 0.12 * abs(t) ** 3
    keel = -0.23 * (1 - abs(t) ** 2.0)
    zz = keel * (1 - s * s) + sheer * s * s
    return Vector((s * w * (1 - inset), t * HL, zz + (-0.03 if inset else 0.0))), sheer
paint, paint2, plank = srgb(0x2f6f78), srgb(0xf2ead8), srgb(0xc29a66)
def bv(v):
    return BT.vert(rotz(v, boat_yaw))
outer, inner = [], []
for i in range(NST):
    t = -1 + 2 * i / (NST - 1)
    ro, ri = [], []
    for j in range(NPT):
        s = -1 + 2 * j / (NPT - 1)
        ro.append(bv(hull(t, s, 0.0)[0]))
        ri.append(bv(hull(t, s, 0.09)[0]))
    outer.append(ro); inner.append(ri)
for i in range(NST - 1):
    for j in range(NPT - 1):
        c = paint2 if (j == 0 or j == NPT - 2) else paint
        BT.face([outer[i][j], outer[i][j + 1], outer[i + 1][j + 1], outer[i + 1][j]], c, smooth_=True)
        pc = jitter(plank, 0.1) if (i + j) % 2 == 0 else jitter(mix(plank, srgb(0x8a6a3e), 0.5), 0.1)
        BT.face([inner[i][j + 1], inner[i][j], inner[i + 1][j], inner[i + 1][j + 1]], pc, smooth_=True)
for i in range(NST - 1):                                   # gunwale rim
    for j in (0, NPT - 1):
        BT.face([outer[i][j], outer[i + 1][j], inner[i + 1][j], inner[i][j]], srgb(0xa87a48), smooth_=False)
for ty, tw in ((-0.35, 0.5), (0.55, 0.42)):                # thwarts
    pos = rotz(Vector((0, ty, 0.07)), boat_yaw)
    add_box(BT, pos, (2 * tw, 0.24, 0.05), srgb(0xb88a54), rot=boat_yaw)
for sx in (-0.18, 0.18):                                    # oars resting inside
    a, b_ = rotz(Vector((sx, -0.9, 0.12)), boat_yaw), rotz(Vector((sx + 0.05, 0.9, 0.12)), boat_yaw)
    add_tube(BT, a, b_, 0.02, 0.02, 6, srgb(0x8a6038), srgb(0x8a6038))
    add_box(BT, rotz(Vector((sx + 0.05, 0.95, 0.12)), boat_yaw), (0.1, 0.28, 0.02), srgb(0xf2ead8), rot=boat_yaw)
boat = BT.to_obj("Boat", MAT["wood"])
boat.data.materials[0] = MAT["shop"]
boat.location = (BOAT.x, BOAT.y, WATER_Z + 0.13)

# ---- bench by the cherry tree -------------------------------------------------
BN = Builder()
bnx, bny = BENCH
bbase = Vector((bnx, bny, terrain_h(bnx, bny)))
byaw = math.atan2(-bny, -bnx)
def BL(x, y, z): return loc(bbase, byaw, (x, y, z))
for k in range(4):
    add_box(BN, BL(0.0 + (k - 1.5) * 0.14, 0, 0.46), (0.12, 1.7, 0.05), jitter(wood1, 0.1), rot=byaw)
for k in range(3):
    add_box(BN, BL(-0.26, 0, 0.62 + k * 0.14), (0.04, 1.7, 0.1), jitter(wood1, 0.1), rot=byaw)
for sy in (-0.75, 0.75):
    for sx in (-0.2, 0.2):
        add_box(BN, BL(sx, sy, 0.22), (0.07, 0.07, 0.44), wood2, rot=byaw)
    add_box(BN, BL(-0.24, sy, 0.62), (0.06, 0.06, 0.45), wood2, rot=byaw)
    add_box(BN, BL(0.0, sy, 0.64), (0.4, 0.06, 0.05), wood2, rot=byaw)
bench = BN.to_obj("Bench", MAT["wood"])
COLL.append((bnx, bny, 0.95))
AOC.append((bnx, bny, 1.2, 0.3))

# ---- mushrooms -------------------------------------------------------------------
MU = Builder()
clusters = [(-12.5, 5.5), (-8.5, 10.8), (7.2, 11.6), (11.8, 4.2), (-9.5, -9.0), (14.0, -6.0), (-14.5, -2.0), (3.5, 15.0)]
for _ in range(10):
    th_ = rnd() * math.tau; r_ = uni(11, 17)
    clusters.append((r_ * math.cos(th_), r_ * math.sin(th_)))
for (cx, cy) in clusters:
    if pond_d(cx, cy) < 2.5:
        continue
    for _ in range(random.randint(3, 6)):
        mx, my = cx + uni(-0.8, 0.8), cy + uni(-0.8, 0.8)
        mz = terrain_h(mx, my)
        sc = uni(0.6, 1.5)
        kind = rnd()
        cap_a = srgb(0xd6382a) if kind < 0.6 else (srgb(0xc58a4a) if kind < 0.85 else srgb(0xe8c24a))
        add_tube(MU, (mx, my, mz - 0.02), (mx, my, mz + 0.12 * sc), 0.025 * sc, 0.02 * sc, 7, srgb(0xefe6cf), srgb(0xf6efdc))
        add_ico(MU, (mx, my, mz + 0.13 * sc), (0.085 * sc, 0.085 * sc, 0.055 * sc), 3,
                lambda p, n, ca=cap_a, k=kind: (srgb(0xffffff) if (k < 0.6 and noise.noise(p * 38) > 0.3 and n.z > 0.2) else ca))
mushrooms = MU.to_obj("Mushrooms", MAT["leaf"])

# ---- fallen log -------------------------------------------------------------------
LG = Builder()
la, lb = Vector((-11.2, -6.4, 0)), Vector((-8.1, -7.6, 0))
la.z, lb.z = terrain_h(la.x, la.y) + 0.3, terrain_h(lb.x, lb.y) + 0.3
add_tube(LG, la, lb, 0.34, 0.3, 12, srgb(0x4a3322), srgb(0x5b402a), cap=True)
add_ico(LG, (la + lb) / 2 + Vector((0, 0, 0.24)), (1.4, 0.26, 0.13), 3, lambda p, n: mix(srgb(0x3f7a22), srgb(0x6aa83a), noise.noise(p * 4) * 0.5 + 0.5), amp=0.3, seed=3.0, rot=math.atan2(lb.y - la.y, lb.x - la.x))
add_ico(LG, la + Vector((0.05, 0, 0.0)), (0.34, 0.34, 0.34), 3, lambda p, n: srgb(0xc9a36a), amp=0.05)
logm = LG.to_obj("Log", MAT["wood"])
for k in range(4):
    pk = la + (lb - la) * (k / 3)
    COLL.append((pk.x, pk.y, 0.45))
    AOC.append((pk.x, pk.y, 0.9, 0.3))

# ---- flowering bushes --------------------------------------------------------------
BU = Builder()
bush_pts = []
tries_b = 0
while len(bush_pts) < 30 and tries_b < 400:
    tries_b += 1
    th_ = rnd() * math.tau
    r_ = shore_radius(th_) + uni(2.0, 7.5)
    bx_, by_ = r_ * math.cos(th_), r_ * math.sin(th_)
    if abs(math.atan2(math.sin(th_ - dock_theta), math.cos(th_ - dock_theta))) < 0.45:
        continue
    if math.hypot(bx_ - SHOP.x, by_ - SHOP.y) < 4 or math.hypot(bx_ - bnx, by_ - bny) < 2.5 or path_dist(bx_, by_) < 1.7:
        continue
    bush_pts.append((bx_, by_))
bloom_sets = [[0xff9ec4, 0xffffff], [0xffffff, 0xfff2a8], [0x9ec8ff, 0xc9a7ff], [0xff7b6b, 0xffd23f]]
for (bx_, by_) in bush_pts:
    COLL.append((bx_, by_, 0.85))
    AOC.append((bx_, by_, 1.5, 0.32))
    bz_ = terrain_h(bx_, by_)
    bl = random.choice(bloom_sets)
    base_g = jitter(mix(srgb(0x2f7a22), srgb(0x56b03a), rnd()), 0.1)
    for _ in range(random.randint(3, 5)):
        c = (bx_ + uni(-0.5, 0.5), by_ + uni(-0.5, 0.5), bz_ + uni(0.25, 0.55))
        rr = uni(0.45, 0.85)
        add_ico(BU, c, (rr, rr, rr * 0.8), 3,
                lambda p, n, g=base_g: mix(jitter(g, 0.06), (min(1, g[0] * 1.5), min(1, g[1] * 1.4), g[2] * 1.2), smooth(0.0, 1.0, n.z) * 0.5),
                amp=0.25, seed=rnd() * 30)
        for _ in range(random.randint(6, 11)):
            nv = Vector((uni(-1, 1), uni(-1, 1), uni(0.0, 1.0))).normalized()
            fc = srgb(random.choice(bl))
            add_ico(BU, Vector(c) + Vector((nv.x * rr, nv.y * rr, nv.z * rr * 0.8)), (0.05, 0.05, 0.035), 1, lambda p, n, fc=fc: fc, smooth_=True)
bushes = BU.to_obj("Bushes", MAT["leaf"])

# ---- ferns ---------------------------------------------------------------------------
FN = Builder()
fern_n = 0
while fern_n < 110:
    r_ = uni(7, 30); th_ = rnd() * math.tau
    fx, fy = r_ * math.cos(th_), r_ * math.sin(th_)
    if pond_d(fx, fy) < 1.8 or terrain_h(fx, fy) < 0.05 or path_dist(fx, fy) < 1.3:
        continue
    fz = terrain_h(fx, fy) - 0.02
    ph = rnd()
    tint = jitter(mix(srgb(0x2a7a2a), srgb(0x4a9a30), rnd()), 0.1)
    for k in range(8):
        add_blade(FN, (fx, fy, fz), uni(0.45, 0.85), 0.07, k / 8 * math.tau + uni(-0.2, 0.2), uni(0.7, 1.1), jitter(tint, 0.1), ph, taper=0.9)
    fern_n += 1
ferns = FN.to_obj("Ferns", MAT["plant"])

# ---- frog (cloned by three.js) ----------------------------------------------------------
FR = Builder()
g1, g2, bel = srgb(0x5fae3a), srgb(0x3c8a2a), srgb(0xe3ecaa)
add_ico(FR, (0, 0.0, 0.07), (0.09, 0.13, 0.07), 3, lambda p, n: mix(bel, mix(g1, g2, noise.noise(p * 24) * 0.5 + 0.5), smooth(-0.1, 0.35, n.z)))
add_ico(FR, (0, -0.12, 0.1), (0.075, 0.07, 0.055), 3, lambda p, n: g1)
for sx in (-1, 1):
    add_ico(FR, (sx * 0.048, -0.135, 0.158), (0.03, 0.03, 0.03), 2, lambda p, n: srgb(0xd9b23a))
    add_ico(FR, (sx * 0.048, -0.162, 0.162), (0.013, 0.012, 0.016), 1, lambda p, n: srgb(0x111111))
    add_ico(FR, (sx * 0.095, 0.07, 0.04), (0.04, 0.1, 0.032), 2, lambda p, n: g2)
    add_ico(FR, (sx * 0.12, 0.15, 0.015), (0.03, 0.07, 0.014), 2, lambda p, n: g1)
    add_ico(FR, (sx * 0.065, -0.075, 0.03), (0.022, 0.05, 0.022), 2, lambda p, n: g1)
frog = FR.to_obj("Frog", MAT["animal"])
frog.location = (0, 0, -50)

# ---- dragonfly (cloned by three.js) ---------------------------------------------------------
DF = Builder()
teal, blue = srgb(0x1fa8c9), srgb(0x174f9a)
for i in range(9):
    y0, y1 = 0.03 + i * 0.045, 0.03 + (i + 1) * 0.045
    c = teal if i % 2 == 0 else blue
    add_tube(DF, (0, y0, 0), (0, y1, 0), 0.012 - i * 0.0006, 0.012 - (i + 1) * 0.0006, 6, c, c)
add_ico(DF, (0, 0.0, 0.0), (0.022, 0.04, 0.022), 2, lambda p, n: srgb(0x2fbf6a))
add_ico(DF, (0, -0.05, 0.0), (0.02, 0.02, 0.02), 2, lambda p, n: srgb(0x2b4fb8))
for sx in (-1, 1):
    add_ico(DF, (sx * 0.015, -0.058, 0.006), (0.014, 0.014, 0.014), 2, lambda p, n: srgb(0x1c3fa8))
dfly = DF.to_obj("DflyBody", MAT["animal"])
dfly.location = (0, 0, -50)
DW = Builder()
dctr = DW.vert(Vector((0, 0, 0)))
outline = [(0.02, 0.012), (0.06, 0.03), (0.11, 0.032), (0.145, 0.015), (0.14, -0.006), (0.1, -0.02), (0.05, -0.015)]
ring = [DW.vert(Vector((x, y, 0))) for x, y in outline]
for i in range(len(ring) - 1):
    DW.face([dctr, ring[i], ring[i + 1]], [srgb(0xe8f4ff), srgb(0xcfe6ff), srgb(0xcfe6ff)], smooth_=False)
dw = DW.to_obj("DflyWing", MAT["animal"])
dw.location = (0, 0, -50)

# ------------------------------------------------- baked ambient occlusion
import numpy as np
def bake_ao(obj, strength=1.0):
    me = obj.data
    co = np.empty(len(me.vertices) * 3, 'f4'); me.vertices.foreach_get('co', co); co = co.reshape(-1, 3)
    vi = np.empty(len(me.loops), 'i4'); me.loops.foreach_get('vertex_index', vi)
    attr = me.color_attributes['Col']
    col = np.empty(len(attr.data) * 4, 'f4'); attr.data.foreach_get('color', col); col = col.reshape(-1, 4)
    x, y = co[vi, 0], co[vi, 1]
    f = np.ones(len(vi), 'f4')
    for (ox, oy, r, k) in AOC:
        f *= 1 - k * strength * np.exp(-((x - ox) ** 2 + (y - oy) ** 2) / (r * r) * 1.6)
    f = np.maximum(f, 0.42)
    col[:, :3] *= f[:, None]
    attr.data.foreach_set('color', col.ravel())
    me.update()
for ob, k in ((terrain, 1.0), (grass, 0.85), (flowers, 0.7), (ferns, 0.6), (reeds, 0.5)):
    bake_ao(ob, k)
print("baked AO with", len(AOC), "occluders")


# =====================================================================================
#  AREA 2: Maple Hollow, an autumn lake with a waterfall (built 400 m east of the pond)
# =====================================================================================
OX = 400.0
def shore2(th):
    return 9.6 + 2.0 * math.sin(2 * th + 1.3) + 1.1 * math.sin(3 * th + 0.4) + 0.6 * math.sin(5 * th + 2.2)
def pd2(x, y):
    return math.hypot(x, y) - shore2(math.atan2(y, x))
def cliffk(x, y):
    a = math.atan2(math.sin(math.atan2(y, x) - math.pi / 2), math.cos(math.atan2(y, x) - math.pi / 2))
    return math.exp(-(a / 0.55) ** 2)
def t2_h(x, y):
    r = math.hypot(x, y); d = pd2(x, y)
    nh = noise.fractal(Vector((x * 0.05 + 40, y * 0.05, 2.2)), 1.0, 2.0, 4)
    micro = noise.noise(Vector((x * 0.4, y * 0.4, 3.3))) * 0.07
    if d < 0:
        return 0.1 - 1.9 * smooth(0, 1, min(1.0, -d / 4.0)) + micro * 0.5
    bank = 0.3 * smooth(0, 4, d)
    hills = smooth(9, 32, r) * (1.4 + 5.2 * smooth(17, 42, r)) * max(0.12, 0.62 + 0.55 * nh)
    cliff = 6.2 * smooth(0.4, 3.2, d) * cliffk(x, y) * (1 + 0.15 * noise.noise(Vector((x * 0.6, y * 0.6, 1.0))))
    return 0.1 + bank + (hills + micro) * smooth(0, 4, d) + cliff
R2N = shore2(math.pi / 2)
th_pier2 = -math.pi / 2 + 0.15
u2 = Vector((-math.cos(th_pier2), -math.sin(th_pier2), 0)); sd2 = Vector((-u2.y, u2.x, 0))
P2 = Vector((shore2(th_pier2) * math.cos(th_pier2), shore2(th_pier2) * math.sin(th_pier2), 0))
SHOP2 = P2 + u2 * -4.6 + sd2 * 4.2
WAY2 = P2 + u2 * -3.4 + sd2 * -3.2
W = lambda x, y, z=0.0: Vector((OX + x, y, z))         # local -> world

# ---- terrain ----
C2_G1, C2_G2, C2_G3 = srgb(0x7c8a34), srgb(0xb09a46), srgb(0x5a6a2a)
LEAFC = [srgb(0xc8502a), srgb(0xe08a2a), srgb(0xd8b03a), srgb(0x9a3a20)]
def terrain2_color(x, y, h):
    d = pd2(x, y); r = math.hypot(x, y)
    n1 = noise.noise(Vector((x * 0.12 + 40, y * 0.12, 1.0)))
    if h < WATER_Z - 0.05:
        return mix(srgb(0x3c4a3a), srgb(0x102a33), smooth(WATER_Z, -1.6, h))
    g = mix(C2_G1, C2_G2, 0.5 + 0.6 * n1)
    g = mix(g, C2_G3, smooth(0.2, 0.9, noise.noise(Vector((x * 0.6, y * 0.6, 4.0)))) * 0.4)
    lk = smooth(0.15, 0.55, noise.noise(Vector((x * 0.45, y * 0.45, 7.0))))
    g = mix(g, LEAFC[int((noise.noise(Vector((x * 1.3, y * 1.3, 2.0))) * 0.5 + 0.5) * 3.99) % 4], lk * 0.6)
    g = mix(g, srgb(0x7a7068), cliffk(x, y) * smooth(0.8, 2.4, d) * 0.9)
    g = mix(g, srgb(0x8a9a6a), smooth(24, 44, r) * 0.4)
    beach = (1 - smooth(0.2, 1.4, d)) * (1 - smooth(0.12, 0.35, h))
    c = mix(g, mix(C_SAND, srgb(0x8a7a5a), 0.4), beach)
    return mix(c, C_WET, (1 - smooth(-0.2, 0.2, h)) * 0.8)
T2 = Builder()
N2 = 170
grid2 = []
for j in range(N2 + 1):
    row = []
    for i in range(N2 + 1):
        x = -EXT + 2 * EXT * i / N2; y = -EXT + 2 * EXT * j / N2
        h = t2_h(x, y)
        row.append((x, y, h, T2.vert(W(x, y, h))))
    grid2.append(row)
for j in range(N2):
    for i in range(N2):
        q = [grid2[j][i], grid2[j][i + 1], grid2[j + 1][i + 1], grid2[j + 1][i]]
        T2.face([p[3] for p in q], [terrain2_color(p[0], p[1], p[2]) for p in q])
terrain2 = T2.to_obj("A2_Terrain", MAT["terrain"])

def clear2(x, y, m):
    return (math.hypot(x - SHOP2.x, y - SHOP2.y) > 3.2 + m and math.hypot(x - WAY2.x, y - WAY2.y) > 1.8 + m
            and not (-1.6 - m < (Vector((x, y, 0)) - P2).dot(u2) < 4.8 and abs((Vector((x, y, 0)) - P2).dot(sd2)) < 1.2 + m))

# ---- grass (golden), reeds, ground leaves, floating leaves ----
G2 = Builder(); n2b = 0; tries = 0
while n2b < 13000 and tries < 300000:
    tries += 1
    r = 32 * math.sqrt(rnd()) ** 1.1; th = rnd() * math.tau
    cx, cy = r * math.cos(th), r * math.sin(th)
    if pd2(cx, cy) < 0.6 or t2_h(cx, cy) < 0.02 or cliffk(cx, cy) * smooth(0.5, 2, pd2(cx, cy)) > 0.4 or not clear2(cx, cy, 0.3):
        continue
    tint0 = jitter(mix(srgb(0x9aa040), srgb(0xc8b052), rnd()), 0.12)
    hb = uni(0.32, 0.7)
    for _ in range(random.randint(9, 15)):
        x, y = cx + random.gauss(0, 0.26), cy + random.gauss(0, 0.26)
        if pd2(x, y) < 0.45: continue
        add_blade(G2, W(x, y, t2_h(x, y) - 0.03), hb * uni(0.6, 1.25), uni(0.028, 0.05), rnd() * math.tau, uni(0.1, 0.55), jitter(tint0, 0.12), rnd())
        n2b += 1
grass2 = G2.to_obj("A2_Grass", MAT["grass"])
R2B = Builder()
for _ in range(90):
    th = rnd() * math.tau
    if cliffk(math.cos(th), math.sin(th)) > 0.3 or abs(math.atan2(math.sin(th - th_pier2), math.cos(th - th_pier2))) < 0.35: continue
    rr = shore2(th) + uni(-1.6, 0.8); cx, cy = rr * math.cos(th), rr * math.sin(th)
    for _ in range(random.randint(4, 8)):
        x, y = cx + random.gauss(0, 0.3), cy + random.gauss(0, 0.3); z = t2_h(x, y) - 0.05
        if z > 0.4 or z < -1.0: continue
        add_blade(R2B, W(x, y, z), uni(0.9, 1.6), 0.042, rnd() * math.tau, uni(0.04, 0.2), jitter(mix(srgb(0x8a8a3a), srgb(0xb8a050), rnd()), 0.1), rnd(), taper=0.8)
reeds2 = R2B.to_obj("A2_Reeds", MAT["plant"])
def leaf(B, c, s, yaw, col, z_up=0.0):
    pts = [(0, -1.0), (0.55, -0.35), (0.42, 0.45), (0, 1.0), (-0.42, 0.45), (-0.55, -0.35)]
    vs = [B.vert(Vector(c) + rotz(Vector((px * s, py * s, z_up * (abs(px) * 0.5))), yaw)) for px, py in pts]
    ctr = B.vert(Vector(c) + Vector((0, 0, 0.004)))
    for i in range(len(vs)):
        B.face([ctr, vs[i], vs[(i + 1) % len(vs)]], [mix(col, srgb(0x5a2a10), 0.25), col, col], smooth_=True)
LV = Builder()
TREES2 = []
for _ in range(2000):
    r = uni(3, 34); th = rnd() * math.tau; x, y = r * math.cos(th), r * math.sin(th)
    if pd2(x, y) < 0.4 or t2_h(x, y) < 0.0: continue
    if noise.noise(Vector((x * 0.3, y * 0.3, 9.0))) < -0.15 and rnd() < 0.7: continue
    leaf(LV, W(x, y, t2_h(x, y) + 0.015), uni(0.05, 0.09), rnd() * math.tau, jitter(random.choice(LEAFC), 0.15))
groundleaves = LV.to_obj("A2_Leaves", MAT["plant"])
FL = Builder()
for _ in range(80):
    th = rnd() * math.tau; rr = shore2(th) * uni(0.2, 0.95); x, y = rr * math.cos(th), rr * math.sin(th)
    leaf(FL, W(x, y, WATER_Z + 0.012), uni(0.07, 0.12), rnd() * math.tau, jitter(random.choice(LEAFC), 0.12), z_up=0.25)
floatleaves = FL.to_obj("A2_FloatLeaves", MAT["plant"])

# ---- trees: maples, golden birches, a few pines ----
TR2, LF2 = Builder(), Builder()
def tree2(x, y, kind, s):
    z = t2_h(x, y) - 0.1
    lean = Vector((uni(-0.3, 0.3), uni(-0.3, 0.3), 0))
    h = {"maple": 4.4, "birch": 5.2, "pine": 7.0}[kind] * s
    p0 = W(x, y, z); pm = p0 + Vector((0, 0, h * 0.55)) + lean * h * 0.25; p1 = p0 + Vector((0, 0, h)) + lean * h * 0.5
    c0, c1 = (srgb(0xd9d4c4), srgb(0xb7b0a0)) if kind == "birch" else (srgb(0x3e2a1c), srgb(0x5a3e28))
    rw = 0.33 * s
    COLL.append((OX + x, y, rw * 1.25 + 0.2)); AOC.append((OX + x, y, (1.6 if kind == "pine" else 2.6) * s, 0.42))
    add_tube(TR2, p0 - Vector((0, 0, 0.3)), pm, rw * 1.2, rw * 0.8, 8, c0, c1)
    add_tube(TR2, pm, p1, rw * 0.8, rw * 0.45, 8, c1, c1)
    if kind == "pine":
        for i in range(6):
            t = i / 6; cz = z + h * (0.25 + t * 0.7)
            add_cone(LF2, (OX + x + lean.x * h * 0.5 * t, y + lean.y * h * 0.5 * t, cz), (2.0 - t * 1.6) * s, 1.8 * s, 8, jitter(srgb(0x24502e), 0.1), jitter(srgb(0x3a6a3a), 0.1), rot=rnd())
        return
    for _ in range(3):
        a = rnd() * math.tau; bs = pm + Vector((0, 0, h * 0.1))
        add_tube(TR2, bs, bs + Vector((math.cos(a) * 1.6 * s, math.sin(a) * 1.6 * s, 1.0 * s)), rw * 0.38, rw * 0.15, 6, c1, c1)
    cols = [srgb(0xd8402a), srgb(0xe8742a), srgb(0xf0a830), srgb(0xb83020)] if kind == "maple" else [srgb(0xf0c840), srgb(0xe8b030), srgb(0xd8d060)]
    for _ in range(7):
        a = rnd() * math.tau; rad = uni(0.4, 1.9) * s
        c = p1 + Vector((math.cos(a) * rad, math.sin(a) * rad, uni(-0.5, 1.4) * s)); rr = uni(1.3, 2.1) * s
        base = random.choice(cols)
        add_ico(LF2, c, (rr, rr, rr * 0.82), 3, lambda pos, n, base=base: mix(jitter(base, 0.08), (min(1, base[0] * 1.35), min(1, base[1] * 1.4), base[2] * 1.2), smooth(0.0, 1.0, n.z) * 0.6), amp=0.3, seed=rnd() * 40)
for (x, y, k, s) in ((12.5, 3.0, "maple", 1.2), (-12.0, 2.5, "maple", 1.1), (-6.0, -14.0, "birch", 1.0), (8.0, -13.5, "maple", 1.05), (-13.5, -6.0, "maple", 1.0)):
    tree2(x, y, k, s)
cnt = 0; tries = 0
while cnt < 30 and tries < 4000:
    tries += 1
    r = uni(26, 44); th = rnd() * math.tau; x, y = r * math.cos(th), r * math.sin(th)
    if t2_h(x, y) < 0.2: continue
    tree2(x, y, random.choice(["maple", "maple", "maple", "birch", "pine"]), uni(0.9, 1.6)); cnt += 1
trunks2 = TR2.to_obj("A2_Trunks", MAT["wood"]); foliage2 = LF2.to_obj("A2_Foliage", MAT["leaf"])

# ---- rocks, cliff boulders, waterfall ----
RK2 = Builder()
for _ in range(26):
    th = rnd() * math.tau; rr = shore2(th) + uni(-1.0, 3.5); x, y = rr * math.cos(th), rr * math.sin(th)
    if not clear2(x, y, 0.5): continue
    s = uni(0.3, 0.85) * (1.7 if rnd() < 0.15 else 1)
    COLL.append((OX + x, y, s * 1.05)); AOC.append((OX + x, y, s * 1.7, 0.32))
    add_ico(RK2, W(x, y, t2_h(x, y) + s * 0.2), (s * uni(1, 1.5), s * uni(0.9, 1.3), s * uni(0.55, 0.9)), 3, rock_col(jitter(srgb(0x75706a), 0.1), srgb(0x6a7a2a)), amp=0.28, seed=rnd() * 50, rot=rnd() * 6)
for k in range(16):                                       # boulders flanking the falls
    sx = (-1 if k % 2 else 1) * uni(1.6, 3.4); yy = R2N + uni(-0.2, 4.0)
    s = uni(0.7, 1.5)
    add_ico(RK2, W(sx, yy, t2_h(sx, yy) + s * 0.25), (s * 1.3, s, s * 0.9), 3, rock_col(jitter(srgb(0x6e6a64), 0.1), srgb(0x4f6a2a)), amp=0.3, seed=rnd() * 50, rot=rnd() * 6)
rocks2 = RK2.to_obj("A2_Rocks", MAT["rock"])
WF = Builder()
rows = []
NR = 14
for i in range(NR + 1):
    k = i / NR
    y = R2N + 3.2 - 3.6 * k
    z = max(t2_h(0, y) + 0.18, WATER_Z + 0.01) if k > 0.05 else t2_h(0, R2N + 3.2) + 0.15
    w = 1.0 + 0.5 * k
    rows.append([WF.vert(W(-w, y, z)), WF.vert(W(0, y + 0.08, z + 0.04)), WF.vert(W(w, y, z))])
for i in range(NR):
    for j in range(2):
        a, b_, c, d = rows[i][j], rows[i][j + 1], rows[i + 1][j + 1], rows[i + 1][j]
        WF.face([a, b_, c, d], srgb(0xffffff), [(j / 2, i / NR), ((j + 1) / 2, i / NR), ((j + 1) / 2, (i + 1) / NR), (j / 2, (i + 1) / NR)], smooth_=True)
waterfall = WF.to_obj("A2_Waterfall", MAT["water"])

# ---- pier ----
DK2 = Builder()
for i in range(18):
    s = -1.0 + i * 0.3; c = P2 + u2 * s
    add_box(DK2, W(c.x, c.y, DECK_Z), (0.26, 1.3, 0.07), jitter(mix(srgb(0x8a5a32), srgb(0x6a4226), rnd()), 0.12), rot=math.atan2(u2.y, u2.x))
for s in (-0.6, 1.6, 3.8):
    for off in (-0.58, 0.58):
        c = P2 + u2 * s + sd2 * off
        add_box(DK2, W(c.x, c.y, DECK_Z - 0.5), (0.14, 0.14, 1.6), srgb(0x4b3220), rot=math.atan2(u2.y, u2.x))
dock2 = DK2.to_obj("A2_Dock", MAT["wood"])

# ---- the outfitters' cabin ----
S2_yaw = math.atan2(u2.y, u2.x)
gz2 = t2_h(SHOP2.x, SHOP2.y)
S2B = Vector((OX + SHOP2.x, SHOP2.y, gz2))
CB, CG = Builder(), Builder()
def L2(x, y, z): return loc(S2B, S2_yaw, (x, y, z))
def cbox(x, y, z, sx, sy, sz, col): add_box(CB, L2(x, y, z), (sx, sy, sz), col, rot=S2_yaw)
logc, logd, roofc = srgb(0x8a5a34), srgb(0x5e3c22), srgb(0x3e5a3a)
cbox(0, 0, 0.06, 3.4, 3.6, 0.12, logd)
for k in range(9):                                         # log walls
    z = 0.2 + k * 0.24; c = jitter(logc, 0.08)
    cbox(-1.55, 0, z, 0.24, 3.4, 0.22, c); cbox(0, -1.7, z, 3.1, 0.24, 0.22, c); cbox(0, 1.7, z, 3.1, 0.24, 0.22, c)
cbox(1.35, 0, 0.6, 0.6, 3.2, 1.0, logd); cbox(1.35, 0, 1.13, 0.9, 3.4, 0.1, srgb(0xc9a36a))
quad(CB, [L2(-1.9, -2.1, 2.9), L2(-1.9, 2.1, 2.9), L2(2.2, 2.1, 2.3), L2(2.2, -2.1, 2.3)], roofc)
quad(CB, [L2(-1.9, -2.1, 2.8), L2(2.2, -2.1, 2.2), L2(2.2, 2.1, 2.2), L2(-1.9, 2.1, 2.8)], logd)
for i in range(12):
    y0 = -2.1 + 4.2 * i / 12; y1 = y0 + 4.2 / 12; c = srgb(0x2f6a3a) if i % 2 == 0 else srgb(0xf2e2b0)
    quad(CB, [L2(2.2, y0, 2.3), L2(2.2, y1, 2.3), L2(2.9, y1, 1.95), L2(2.9, y0, 1.95)], c)
for sy in (-1.9, 1.9): add_tube(CB, L2(2.05, sy, 0.12), L2(2.05, sy, 2.35), 0.08, 0.08, 8, logd, logd)
add_box(CB, L2(2.1, 0, 2.6), (0.06, 1.4, 0.5), srgb(0xc9a36a), rot=S2_yaw)
leaf(CB, L2(2.14, 0, 2.6), 0.2, 0, srgb(0xd8402a))
for k in range(7):                                         # rods for sale leaning on the counter
    add_tube(CB, L2(1.7, -1.4 + k * 0.45, 0.1), L2(1.95, -1.3 + k * 0.45, 2.0), 0.015, 0.008, 5, srgb([0xc8502a, 0x6a3a20, 0x7ad9ff, 0x2a2a6a, 0x2fa38a, 0xd8b03a, 0xe8e8e8][k]), srgb(0x2a2a2a))
for (bx, by) in ((-0.5, 2.3), (0.4, 2.4)):
    add_tube(CB, L2(bx, by, 0.12), L2(bx, by, 0.9), 0.33, 0.33, 10, logc, logc, cap=True)
cabin = CB.to_obj("A2_Shop", MAT["shop"])
add_box(CG, L2(1.85, 0, 1.95), (0.24, 0.24, 0.3), srgb(0xffc266))
cabinglow = CG.to_obj("A2_ShopGlow", MAT["glow"])
a2s = bpy.data.objects.new("A2_ShopAnchor", None); bpy.context.scene.collection.objects.link(a2s); a2s.location = (OX + SHOP2.x, SHOP2.y, gz2 + 1.6)
COLL.append((OX + SHOP2.x, SHOP2.y, 2.4)); AOC.append((OX + SHOP2.x, SHOP2.y, 3.4, 0.5))

# ---- waystones (one at each pond) ----
def waystone(name, base, glowcol):
    WS, WG = Builder(), Builder()
    add_box(WS, base + Vector((0, 0, 0.1)), (1.3, 1.3, 0.2), srgb(0x6a6660))
    add_ico(WS, base + Vector((0, 0, 1.2)), (0.38, 0.28, 1.1), 3, lambda p, n: mix(srgb(0x7c7870), srgb(0x5a7a3a), smooth(0.4, 0.9, n.z) * 0.6), amp=0.1, seed=rnd() * 40)
    for k in range(3):
        add_box(WG, base + Vector((0, -0.29, 0.75 + k * 0.45)), (0.14 - k * 0.02, 0.04, 0.2), glowcol)
    add_ico(WG, base + Vector((0, 0, 2.55)), (0.12, 0.12, 0.12), 2, lambda p, n: glowcol)
    WS.to_obj(name, MAT["rock"]); WG.to_obj(name + "Glow", MAT["glow"])
    a = bpy.data.objects.new(name + "Anchor", None); bpy.context.scene.collection.objects.link(a); a.location = base + Vector((0, 0, 1.2))
    COLL.append((base.x, base.y, 0.75)); AOC.append((base.x, base.y, 1.1, 0.3))
W1 = _S0 - _u * 3.8 + _sdir * 3.4
waystone("Waystone1", Vector((W1.x, W1.y, terrain_h(W1.x, W1.y))), srgb(0x7ff0ff))
waystone("A2_Waystone", Vector((OX + WAY2.x, WAY2.y, t2_h(WAY2.x, WAY2.y))), srgb(0xffb35a))
for ob, k in ((terrain2, 1.0), (grass2, 0.85), (groundleaves, 0.6)):
    bake_ao(ob, k)
DOCKS = [[_S0.x, _S0.y, _u.x, _u.y, -1.6, 5.4, 0.66], [OX + P2.x, P2.y, u2.x, u2.y, -1.0, 4.6, 0.62]]
print("area 2 built:", n2b, "blades")

# ------------------------------------------------------------ scene metadata
meta = bpy.data.objects.new("PondMeta", None)
bpy.context.scene.collection.objects.link(meta)
meta["water_z"] = WATER_Z
meta["lantern"] = list(LANTERN_POS)
meta["dock_theta"] = dock_theta
meta["docks"] = json.dumps([[round(v, 3) for v in d] for d in DOCKS])
meta["colliders"] = json.dumps([[round(x, 2), round(y, 2), round(r, 2)] for x, y, r in COLL])
meta["pads"] = json.dumps([[round(x, 3), round(y, 3), round(r, 3)] for x, y, r in pads])

# ------------------------------------------------------------- lights/camera
sun = bpy.data.lights.new("Sun", "SUN")
sun.energy = 3.2
sun.color = (1.0, 0.82, 0.62)
sun.angle = math.radians(2.5)
sun_ob = bpy.data.objects.new("Sun", sun)
bpy.context.scene.collection.objects.link(sun_ob)
sun_ob.rotation_euler = (math.radians(66), 0, math.radians(35))

cam = bpy.data.cameras.new("Camera")
cam.lens = 28
cam_ob = bpy.data.objects.new("Camera", cam)
bpy.context.scene.collection.objects.link(cam_ob)
cam_ob.location = (-7.5, -17.5, 6.0)
target = Vector((0.5, 1.5, 0.0))
cam_ob.rotation_euler = (target - cam_ob.location).to_track_quat("-Z", "Y").to_euler()
scene.camera = cam_ob

world = bpy.data.worlds.new("World")
scene.world = world
world.use_nodes = True
nt = world.node_tree
for n in list(nt.nodes):
    nt.nodes.remove(n)
sky = nt.nodes.new("ShaderNodeTexSky")
sky.sky_type = "MULTIPLE_SCATTERING"
sky.sun_elevation = math.radians(24)
sky.sun_rotation = math.radians(215)
bg = nt.nodes.new("ShaderNodeBackground")
bg.inputs["Strength"].default_value = 1.0
wo = nt.nodes.new("ShaderNodeOutputWorld")
nt.links.new(sky.outputs["Color"], bg.inputs["Color"])
nt.links.new(bg.outputs["Background"], wo.inputs["Surface"])
sun_ob.rotation_euler = (math.radians(90 - 24), 0, math.radians(215 + 180 - 90 + 90))

scene.view_settings.view_transform = "AgX"
scene.view_settings.exposure = -0.9
scene.view_settings.look = "AgX - Medium High Contrast" if hasattr(scene.view_settings, "look") else "None"

# --------------------------------------------------------------------- save
bpy.ops.wm.save_as_mainfile(filepath=os.path.join(HERE, "pond.blend"))
print("saved pond.blend")

bpy.ops.export_scene.gltf(
    filepath=os.path.join(HERE, "pond.glb"),
    export_format="GLB",
    export_apply=True,
    export_cameras=False,
    export_lights=False,
    export_yup=True,
    export_texcoords=True,
    export_normals=True,
    export_extras=True,
)
print("exported pond.glb", os.path.getsize(os.path.join(HERE, "pond.glb")) // 1024, "KB")

def optimise_glb(path, keep_uv):
    """Shrink the GLB: vertex colours -> normalised uint16, drop UVs on meshes that never use them."""
    import struct
    raw = open(path, "rb").read()
    jl = struct.unpack("<I", raw[12:16])[0]
    J = json.loads(raw[20:20 + jl])
    binoff = 20 + jl + 8
    B = raw[binoff:]
    CT = {5126: "<f4", 5123: "<u2", 5125: "<u4", 5121: "u1"}
    NC = {"SCALAR": 1, "VEC2": 2, "VEC3": 3, "VEC4": 4}
    role = {}
    for m in J["meshes"]:
        for pr in m["primitives"]:
            if "TEXCOORD_0" in pr["attributes"] and m["name"] not in keep_uv:
                del pr["attributes"]["TEXCOORD_0"]
            for k, a in pr["attributes"].items():
                role[a] = k
            role[pr["indices"]] = "INDEX"
    out = bytearray(); views = []; accs = []; remap = {}
    for i, A in enumerate(J["accessors"]):
        if i not in role:
            continue
        V = J["bufferViews"][A["bufferView"]]
        n = NC[A["type"]]
        arr = np.frombuffer(B, dtype=CT[A["componentType"]], count=A["count"] * n, offset=V.get("byteOffset", 0) + A.get("byteOffset", 0)).reshape(-1, n)
        A2 = dict(A); A2.pop("byteOffset", None)
        if role[i].startswith("COLOR") and A["componentType"] == 5126:
            if n == 3:
                arr = np.concatenate([arr, np.ones((arr.shape[0], 1), "f4")], 1); A2["type"] = "VEC4"
            arr = np.clip(np.round(arr * 65535), 0, 65535).astype("<u2")
            A2["componentType"] = 5123; A2["normalized"] = True
            A2.pop("min", None); A2.pop("max", None)
        stride = None
        if role[i] == "NORMAL" and A["componentType"] == 5126:
            q = np.clip(np.round(np.clip(arr, -1, 1) * 127), -127, 127).astype("i1")
            arr = np.concatenate([q, np.zeros((q.shape[0], 1), "i1")], 1)
            A2["componentType"] = 5120; A2["normalized"] = True; A2.pop("min", None); A2.pop("max", None)
            stride = 4
        data = np.ascontiguousarray(arr).tobytes()
        while len(out) % 4: out.append(0)
        views.append({"buffer": 0, "byteOffset": len(out), "byteLength": len(data), **({"target": V["target"]} if "target" in V else {}), **({"byteStride": stride} if stride else {})})
        out += data
        A2["bufferView"] = len(views) - 1
        remap[i] = len(accs); accs.append(A2)
    for m in J["meshes"]:
        for pr in m["primitives"]:
            pr["attributes"] = {k: remap[a] for k, a in pr["attributes"].items()}
            pr["indices"] = remap[pr["indices"]]
    J["accessors"] = accs; J["bufferViews"] = views
    J["extensionsUsed"] = sorted(set(J.get("extensionsUsed", [])) | {"KHR_mesh_quantization"})
    J["extensionsRequired"] = sorted(set(J.get("extensionsRequired", [])) | {"KHR_mesh_quantization"})
    J["buffers"] = [{"byteLength": len(out)}]
    js = json.dumps(J, separators=(",", ":")).encode()
    while len(js) % 4: js += b" "
    while len(out) % 4: out.append(0)
    glb = struct.pack("<III", 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(out)) + struct.pack("<II", len(js), 0x4E4F534A) + js + struct.pack("<II", len(out), 0x004E4942) + bytes(out)
    open(path, "wb").write(glb)
optimise_glb(os.path.join(HERE, "pond.glb"), {"Grass", "Reeds", "Flowers", "Ferns", "A2_Grass", "A2_Reeds", "A2_Waterfall"})
print("optimised pond.glb", os.path.getsize(os.path.join(HERE, "pond.glb")) // 1024, "KB")

if RENDER:
    scene.render.engine = "CYCLES"
    scene.cycles.device = "CPU"
    scene.cycles.samples = 40
    scene.cycles.use_denoising = True
    scene.render.resolution_x = 1280
    scene.render.resolution_y = 720
    scene.render.filepath = os.path.join(HERE, "preview.png")
    scene.render.image_settings.file_format = "PNG"
    bpy.ops.render.render(write_still=True)
    print("rendered preview.png")
