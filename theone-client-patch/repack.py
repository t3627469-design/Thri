"""Writes the patched jar: every class under build/work that differs from the original jar is replaced,
new addon classes in build/guard are added, fabric.mod.json gets the version, README gets NOTES.txt."""
import sys, zipfile, os
src, out = sys.argv[1], sys.argv[2]
W = 'build/work/'
G = 'build/guard/'
VERSION = os.environ.get('VERSION', '1.5.0')
zi = zipfile.ZipFile(src)
orig = {it.filename: zi.read(it.filename) for it in zi.infolist()}
changed = []
with zipfile.ZipFile(out, 'w') as zo:
    for it in zi.infolist():
        data = orig[it.filename]
        if it.filename.endswith('.class') and it.filename.startswith('dev/'):
            new = W + it.filename
            if os.path.exists(new):
                nd = open(new, 'rb').read()
                if nd != data:
                    data = nd
                    changed.append(it.filename)
        elif it.filename == 'fabric.mod.json':
            data = data.decode().replace('"version": "1.2.9"', '"version": "%s"' % VERSION, 1).encode()
        elif it.filename == 'README.txt':
            data += open('NOTES.txt', 'rb').read()
        zo.writestr(it, data)
    have = set(orig)
    for root, _, files in os.walk(G):
        for f in files:
            if not f.endswith('.class'):
                continue
            rel = os.path.relpath(os.path.join(root, f), G)
            name = 'dev/rex/farmbuilder/modules/' + rel if not rel.startswith('dev/') else rel
            name = name.replace(os.sep, '/')
            if name not in have:
                zo.write(os.path.join(root, f), name)
                changed.append(name + ' (new)')
print('ok', out, 'changed classes:', len(changed))
for c in sorted(changed):
    print('  ', c)
