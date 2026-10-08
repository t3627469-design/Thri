import sys, zipfile, os
src, out = sys.argv[1], sys.argv[2]
W = 'build/work/'
mod = {'dev/rex/farmbuilder/modules/' + n + '.class' for n in ['Look', 'Planner', 'Pillar', 'OnlyBuild', 'HumanBuilder']}
new = ['Guard', 'Climb']
VERSION = os.environ.get('VERSION', '1.3.1')
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
    for n in new:
        p = 'dev/rex/farmbuilder/modules/' + n + '.class'
        zo.write(W + p, p)
print('ok', out)
