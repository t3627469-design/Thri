import sys, math
sys.path.insert(0, sys.argv[3] if len(sys.argv) > 3 else '/tmp/claude-0/-home-user-Thri/404a597e-b00f-530d-ad55-58b0f504a163/scratchpad/tools')
from litread import read, unpack
root = read(sys.argv[1])
r = next(iter(root['Regions'].values()))
sx, sy, sz = abs(r['Size']['x']), abs(r['Size']['y']), abs(r['Size']['z'])
pal = r['BlockStatePalette']
idx = unpack(r['BlockStates'], max(2, math.ceil(math.log2(len(pal)))), sx * sy * sz)
with open(sys.argv[2] + '/palette.tsv', 'w') as f:
    for i, e in enumerate(pal):
        props = e.get('Properties', {}) or {}
        f.write('%d\t%s\t%s\n' % (i, e['Name'], ';'.join('%s=%s' % (k, v) for k, v in props.items())))
n = 0
with open(sys.argv[2] + '/cells.tsv', 'w') as f:
    for i, p in enumerate(idx):
        if pal[p]['Name'] == 'minecraft:air': continue
        x = i % sx; z = (i // sx) % sz; y = i // (sx * sz)
        f.write('%d\t%d\t%d\t%d\n' % (x, y, z, p)); n += 1
print('size', sx, sy, sz, 'palette', len(pal), 'non-air cells', n)
