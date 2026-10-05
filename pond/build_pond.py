"""
Peaceful Pond -- built entirely in Blender (bpy) from code.

Run:  python3 build_pond.py            (with the `bpy` module installed)
  or  blender -b -P build_pond.py

Outputs (next to this script):
  pond.blend    the full Blender scene
  pond.glb      exported scene used by index.html (three.js)
  preview.png   Cycles render of the scene (skip with --no-render)
"""
import bpy, bmesh, math, random, sys, os
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
        if pond_d(x, y) < 0.45:
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
    if pond_d(x, y) < 1.3 or terrain_h(x, y) < 0.05:
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
for k in range(14):
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

# ------------------------------------------------------------------- critters
def duck(name, scale, kind, pos):
    D = Builder()
    if kind == "drake":
        body, wing, head, ring, chest = srgb(0x8d8a85), srgb(0x6b5a48), srgb(0x1a6b4a), srgb(0xffffff), srgb(0x6b3b24)
    elif kind == "hen":
        body, wing, head, ring, chest = srgb(0x9c7a52), srgb(0x7a5a38), srgb(0xa8845a), srgb(0x9c7a52), srgb(0x9c7a52)
    else:
        body = wing = head = ring = chest = srgb(0xffd84a)
    beak = srgb(0xf29a1c) if kind != "hen" else srgb(0xc98a2a)
    add_ico(D, (0, 0.04, 0.12), (0.2, 0.34, 0.17), 3,
            lambda p, n: mix(body, chest, smooth(-0.3, -0.9, n.y) * 0.9) if n.y < 0 else mix(body, wing, smooth(0.2, 0.9, n.z) * 0.7))
    add_ico(D, (0, 0.34, 0.22), (0.09, 0.14, 0.1), 2, lambda p, n: wing)      # tail lift
    add_tube(D, (0, -0.22, 0.2), (0, -0.28, 0.34), 0.06, 0.05, 8, ring, ring)   # neck
    add_ico(D, (0, -0.3, 0.37), (0.075, 0.09, 0.075), 3, lambda p, n: head)
    add_ico(D, (0, -0.4, 0.34), (0.04, 0.07, 0.016), 2, lambda p, n: beak)
    for sx in (-1, 1):
        add_ico(D, (sx * 0.065, -0.34, 0.4), (0.014, 0.014, 0.014), 1, lambda p, n: srgb(0x111111))
    o = D.to_obj(name, MAT["animal"])
    o.scale = (scale, scale, scale)
    o.location = pos
    return o

duck("Duck_Drake", 1.0, "drake", (-1.5, 2.0, WATER_Z))
duck("Duck_Hen", 0.95, "hen", (-2.8, 2.6, WATER_Z))
for i in range(3):
    duck("Duck_Chick%d" % i, 0.42, "chick", (-3.6 - i * 0.3, 2.9 + i * 0.2, WATER_Z))

def koi(name, scheme):
    K = Builder()
    pal = {
        "orange": (srgb(0xff6a1a), srgb(0xffffff), srgb(0xff8a3a)),
        "kohaku": (srgb(0xfff4ea), srgb(0xe8391a), srgb(0xffffff)),
        "gold":   (srgb(0xffc21a), srgb(0xfff0a0), srgb(0xffd45a)),
        "sanke":  (srgb(0xfffaf0), srgb(0xe8451a), srgb(0x222222)),
    }[scheme]
    def body_col(p, n):
        k = noise.noise(Vector((p.x * 7, p.y * 4, p.z * 7)) + Vector((1, 2, 3)))
        c = pal[0] if k > -0.08 else pal[1]
        if scheme == "sanke" and k > 0.34:
            c = pal[2]
        return c
    add_ico(K, (0, 0, 0), (0.075, 0.34, 0.07), 3, body_col, amp=0.0)
    # head taper handled by shape; tail
    t0 = K.vert(Vector((0, 0.32, 0.0)))
    for sgn in (-1, 1):
        a = K.vert(Vector((0, 0.5, 0.0)))
        b_ = K.vert(Vector((sgn * 0.1, 0.62, 0.0)))
        c_ = K.vert(Vector((sgn * 0.03, 0.55, 0.0)))
        K.face([t0, b_, a], [pal[1], pal[2], pal[2]], smooth_=False)
        K.face([t0, a, b_], [pal[1], pal[2], pal[2]], smooth_=False)
    # dorsal
    d0 = K.vert(Vector((0, -0.05, 0.065)))
    d1 = K.vert(Vector((0, 0.15, 0.065)))
    d2 = K.vert(Vector((0, 0.18, 0.17)))
    K.face([d0, d1, d2], [pal[0], pal[0], pal[2]], smooth_=False)
    K.face([d0, d2, d1], [pal[0], pal[0], pal[2]], smooth_=False)
    for sgn in (-1, 1):
        p0 = K.vert(Vector((sgn * 0.06, -0.12, -0.02)))
        p1 = K.vert(Vector((sgn * 0.18, -0.02, -0.06)))
        p2 = K.vert(Vector((sgn * 0.07, 0.0, -0.03)))
        K.face([p0, p1, p2], pal[2], smooth_=False)
        K.face([p0, p2, p1], pal[2], smooth_=False)
    return K.to_obj(name, MAT["animal"])

for i, sch in enumerate(("orange", "kohaku", "gold", "sanke", "orange")):
    o = koi("Koi_%d" % i, sch)
    o.location = (0, 0, -0.6)

def wing_shape(name, mirror, col_in, col_out, spot):
    W = Builder()
    outline = [(0.0, 0.0), (0.05, 0.08), (0.15, 0.17), (0.25, 0.14), (0.27, 0.05), (0.2, -0.01),
               (0.18, -0.07), (0.2, -0.15), (0.12, -0.16), (0.04, -0.09)]
    ctr = W.vert(Vector((0, 0, 0)))
    vs = [W.vert(Vector((x * mirror, y, 0))) for x, y in outline[1:]]
    for i in range(len(vs) - 1):
        d1 = math.hypot(*outline[1 + i]) / 0.3
        d2 = math.hypot(*outline[2 + i]) / 0.3
        c1 = mix(col_in, col_out, d1)
        c2 = mix(col_in, col_out, d2)
        if (i % 3 == 1):
            c1 = c2 = spot
        W.face([ctr, vs[i], vs[i + 1]], [col_in, c1, c2], smooth_=False)
    return W.to_obj(name, MAT["animal"])

wing_shape("BflyWingL", 1, srgb(0x2a1a10), srgb(0xff8a1a), srgb(0xfff2d0))
wing_shape("BflyWingR", -1, srgb(0x2a1a10), srgb(0xff8a1a), srgb(0xfff2d0))
BB = Builder()
add_ico(BB, (0, 0, 0), (0.014, 0.075, 0.014), 2, lambda p, n: srgb(0x1c120a))
add_ico(BB, (0, -0.085, 0), (0.017, 0.017, 0.017), 2, lambda p, n: srgb(0x1c120a))
bfly_body = BB.to_obj("BflyBody", MAT["animal"])
for o in ("BflyWingL", "BflyWingR", "BflyBody"):
    bpy.data.objects[o].location = (0, 0, -50)   # parked; three.js clones & animates them

# ------------------------------------------------------------ scene metadata
meta = bpy.data.objects.new("PondMeta", None)
bpy.context.scene.collection.objects.link(meta)
meta["water_z"] = WATER_Z
meta["lantern"] = list(LANTERN_POS)
meta["dock_theta"] = dock_theta

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
