import sys, zipfile, os
src, out = sys.argv[1], sys.argv[2]
W = 'build/work/'
mod = {'dev/rex/farmbuilder/modules/' + n + '.class' for n in ['Look', 'Planner', 'Pillar', 'OnlyBuild', 'HumanBuilder']}
VERSION = os.environ.get('VERSION', '1.4.2')
zi = zipfile.ZipFile(src)
with zipfile.ZipFile(out, 'w') as zo:
    for it in zi.infolist():
        data = zi.read(it.filename)
        if it.filename in mod:
            data = open(W + it.filename, 'rb').read()
        elif it.filename == 'fabric.mod.json':
            data = data.decode().replace('"version": "1.2.9"', '"version": "%s"' % VERSION, 1).encode()
        elif it.filename == 'README.txt':
            data += open('NOTES.txt', 'rb').read()
        zo.writestr(it, data)
    G = 'build/guard/dev/rex/farmbuilder/modules/'
    for f in sorted(os.listdir(G)):
        zo.write(G + f, 'dev/rex/farmbuilder/modules/' + f)
print('ok', out)
