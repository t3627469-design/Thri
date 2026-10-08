import gzip, struct, sys, math, collections

def read(path):
    data = gzip.open(path).read()
    pos = [0]
    def take(n):
        b = data[pos[0]:pos[0]+n]; pos[0] += n; return b
    def s16(): return struct.unpack('>h', take(2))[0]
    def i32(): return struct.unpack('>i', take(4))[0]
    def i64(): return struct.unpack('>q', take(8))[0]
    def string(): return take(s16()).decode('utf-8')
    def payload(t):
        if t == 1: return struct.unpack('>b', take(1))[0]
        if t == 2: return s16()
        if t == 3: return i32()
        if t == 4: return i64()
        if t == 5: return struct.unpack('>f', take(4))[0]
        if t == 6: return struct.unpack('>d', take(8))[0]
        if t == 7: n = i32(); return list(take(n))
        if t == 8: return string()
        if t == 9:
            et = struct.unpack('>b', take(1))[0]; n = i32()
            return [payload(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                tt = struct.unpack('>b', take(1))[0]
                if tt == 0: break
                name = string(); d[name] = payload(tt)
            return d
        if t == 11: n = i32(); return [i32() for _ in range(n)]
        if t == 12: n = i32(); return [i64() for _ in range(n)]
        raise ValueError('tag %d at %d' % (t, pos[0]))
    tt = struct.unpack('>b', take(1))[0]
    take(s16())
    return payload(tt)

def unpack(longs, bits, count):
    out = []
    mask = (1 << bits) - 1
    for i in range(count):
        p = i * bits
        w, off = p // 64, p % 64
        v = (longs[w] & 0xFFFFFFFFFFFFFFFF) >> off
        if off + bits > 64:
            v |= (longs[w+1] & 0xFFFFFFFFFFFFFFFF) << (64 - off)
        out.append(v & mask)
    return out

if __name__ == '__main__':
    root = read(sys.argv[1])
    print('regions:', list(root['Regions'].keys()))
    for name, r in root['Regions'].items():
        sx, sy, sz = (abs(r['Size']['x']), abs(r['Size']['y']), abs(r['Size']['z']))
        pal = r['BlockStatePalette']
        n = sx * sy * sz
        bits = max(2, math.ceil(math.log2(len(pal)))) if len(pal) > 1 else 2
        idx = unpack(r['BlockStates'], bits, n)
        print('region', name, 'size', sx, sy, sz, 'palette', len(pal), 'bits', bits, 'entries', n)
        counts = collections.Counter(pal[i]['Name'] for i in idx)
        for k, v in counts.most_common(): print('   ', k, v)
        per_layer = collections.defaultdict(collections.Counter)
        for i, p in enumerate(idx):
            x = i % sx; z = (i // sx) % sz; y = i // (sx * sz)
            nm = pal[p]['Name']
            if nm != 'minecraft:air':
                per_layer[y][nm] += 1
        for y in sorted(per_layer):
            print('  layer y=%d nonair=%d %s' % (y, sum(per_layer[y].values()), dict(per_layer[y].most_common(4))))
