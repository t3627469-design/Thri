"""Generates throwing stubs for every external class the patched addon calls.

Reads the bytecode of the patched addon classes (build/chk), collects every external
owner/member/descriptor, and writes one Java source file per class. Classes listed in
HAND are written by hand in harness/hand and are skipped here.
"""
import os, re, subprocess, sys, collections

CHK = sys.argv[1]          # extracted patched jar (contains dev/...)
OUT = sys.argv[2]          # output source root

HAND = {
    'meteordevelopment/meteorclient/MeteorClient',
    'meteordevelopment/meteorclient/systems/modules/Module',
    'meteordevelopment/meteorclient/systems/modules/Modules',
    'meteordevelopment/meteorclient/settings/Settings',
    'meteordevelopment/meteorclient/settings/SettingGroup',
    'meteordevelopment/meteorclient/settings/Setting',
    'meteordevelopment/meteorclient/settings/IntSetting',
    'meteordevelopment/meteorclient/settings/BoolSetting',
    'meteordevelopment/meteorclient/settings/DoubleSetting',
    'meteordevelopment/meteorclient/settings/StringSetting',
    'meteordevelopment/meteorclient/settings/EnumSetting',
    'meteordevelopment/meteorclient/settings/IntSetting$Builder',
    'meteordevelopment/meteorclient/settings/BoolSetting$Builder',
    'meteordevelopment/meteorclient/settings/DoubleSetting$Builder',
    'meteordevelopment/meteorclient/settings/StringSetting$Builder',
    'meteordevelopment/meteorclient/settings/EnumSetting$Builder',
    'meteordevelopment/meteorclient/utils/player/InvUtils',
    'meteordevelopment/meteorclient/utils/world/BlockUtils',
    'baritone/api/BaritoneAPI',
    'baritone/api/IBaritone', 'baritone/api/IBaritoneProvider', 'baritone/api/SimBaritone',
    'baritone/api/process/ICustomGoalProcess', 'baritone/api/behavior/IPathingBehavior',
    'baritone/api/utils/IInputOverrideHandler', 'baritone/api/utils/input/Input',
    'baritone/api/pathing/goals/Goal', 'baritone/api/pathing/goals/GoalNear',
    'baritone/api/Settings',
    'baritone/api/Settings$Setting',
    'meteordevelopment/meteorclient/events/world/TickEvent$Post',
    'net/minecraft/class_310', 'net/minecraft/class_746', 'net/minecraft/class_1657',
    'net/minecraft/class_1309', 'net/minecraft/class_1297', 'net/minecraft/class_638',
    'net/minecraft/class_636', 'net/minecraft/class_315', 'net/minecraft/class_304',
    'net/minecraft/class_1661', 'net/minecraft/class_1799', 'net/minecraft/class_1792',
    'net/minecraft/class_1747', 'net/minecraft/class_2248', 'net/minecraft/class_2680',
    'net/minecraft/class_2769', 'net/minecraft/class_3610', 'net/minecraft/class_265',
    'net/minecraft/class_2382', 'net/minecraft/class_2338', 'net/minecraft/class_243',
    'net/minecraft/class_2350', 'net/minecraft/class_2350$class_2351',
    'net/minecraft/class_239', 'net/minecraft/class_239$class_240', 'net/minecraft/class_3965',
    'net/minecraft/class_3959', 'net/minecraft/class_3959$class_242', 'net/minecraft/class_3959$class_3960',
    'net/minecraft/class_1268', 'net/minecraft/class_1750', 'net/minecraft/class_1838',
    'net/minecraft/class_1656', 'net/minecraft/class_3532', 'net/minecraft/class_7923',
    'net/minecraft/class_7922', 'net/minecraft/SimResult', 'net/minecraft/SimRegistry', 'net/minecraft/class_2960', 'net/minecraft/class_1922',
    'net/minecraft/class_1269', 'net/minecraft/class_1713', 'net/minecraft/class_1657',
}

# supertypes the generated classes need (only where instanceof/checkcast depends on them)
EXTENDS = {
    'net/minecraft/class_490': 'net/minecraft/class_465',
    'net/minecraft/class_465': 'net/minecraft/class_437',
    'net/minecraft/class_419': 'net/minecraft/class_437',
    'net/minecraft/class_418': 'net/minecraft/class_437',
    'net/minecraft/class_3966': 'net/minecraft/class_239',
    'net/minecraft/class_1793': None,
    'net/minecraft/class_1802': None,
}
IMPLEMENTS = {}

KIND_LINE = re.compile(r'^\s*\d+: (\w+)\s+#\d+\s+// (Method|InterfaceMethod|Field|class) (.+)$')

def jtype(desc, i=0):
    """Parse one JVM type descriptor at desc[i:], return (java type, next index)."""
    c = desc[i]
    prim = {'I': 'int', 'J': 'long', 'Z': 'boolean', 'D': 'double', 'F': 'float',
            'B': 'byte', 'C': 'char', 'S': 'short', 'V': 'void'}
    if c in prim:
        return prim[c], i + 1
    if c == '[':
        t, j = jtype(desc, i + 1)
        return t + '[]', j
    if c == 'L':
        j = desc.index(';', i)
        name = desc[i + 1:j]
        return name.replace('/', '.'), j + 1
    raise ValueError(desc)

def split_method(desc):
    assert desc[0] == '('
    i = 1
    params = []
    while desc[i] != ')':
        t, i = jtype(desc, i)
        params.append(t)
    ret, _ = jtype(desc, i + 1)
    return params, ret

def desc_classes(desc):
    return set(re.findall(r'L([^;<]+);', desc))

def main():
    classes = []
    for root, _, files in os.walk(os.path.join(CHK, 'dev')):
        for f in files:
            if f.endswith('.class'):
                classes.append(os.path.relpath(os.path.join(root, f), CHK)[:-6].replace('/', '.'))
    refs = collections.defaultdict(lambda: {'iface': False, 'members': set(), 'new': False})
    descr_only = set()
    for i in range(0, len(classes), 40):
        out = subprocess.run(['javap', '-c', '-p', '-cp', CHK] + classes[i:i + 40],
                             capture_output=True, text=True).stdout
        for line in out.splitlines():
            m = KIND_LINE.match(line)
            if not m:
                continue
            op, kind, ref = m.groups()
            if kind == 'class':
                if op == 'new':
                    refs[ref.strip()]['new'] = True
                continue
            if '.' not in ref:
                continue
            owner, rest = ref.split('.', 1)
            if owner.startswith(('dev/', 'java/', '[', '"')) or ':' not in rest:
                continue
            name, desc = rest.split(':', 1)
            name = name.strip('"')
            if kind == 'Field':
                refs[owner]['members'].add(('F', op, name, desc, ''))
                descr_only |= desc_classes(desc)
            else:
                if kind == 'InterfaceMethod':
                    refs[owner]['iface'] = True
                refs[owner]['members'].add(('M', op, name, desc, kind))
                descr_only |= desc_classes(desc)
    for owner in list(refs):
        if owner.startswith(('java/', 'dev/')):
            del refs[owner]
    for c in descr_only:
        if not c.startswith(('java/', 'dev/')):
            refs.setdefault(c, {'iface': False, 'members': set(), 'new': False})
    # every class named in the addon's own descriptors (signatures, fields, constants)
    for i in range(0, len(classes), 40):
        out = subprocess.run(['javap', '-v', '-p', '-cp', CHK] + classes[i:i + 40],
                             capture_output=True, text=True).stdout
        for name in set(re.findall(r'L([A-Za-z0-9_/$]+);', out)) | set(re.findall(r'\[\(\s*\w*\s*\)?\s*', '')):
            if not name.startswith(('java/', 'dev/')):
                refs.setdefault(name, {'iface': False, 'members': set(), 'new': False})
    for parent in EXTENDS.values():
        if parent:
            refs.setdefault(parent, {'iface': False, 'members': set(), 'new': False})
    written = 0
    for owner, info in sorted(refs.items()):
        if owner in HAND:
            continue
        write(owner, info)
        written += 1
    print('generated', written, 'stub classes')

def write(owner, info):
    pkg, _, simple = owner.rpartition('/')
    jname = simple
    path = os.path.join(OUT, owner + '.java')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    is_iface = info['iface']
    lines = []
    if pkg:
        lines.append('package %s;' % pkg.replace('/', '.'))
    head = 'public %s %s' % ('interface' if is_iface else 'class', jname)
    sup = EXTENDS.get(owner)
    if sup and not is_iface:
        head += ' extends ' + sup.replace('/', '.')
    lines.append(head + ' {')
    members = sorted(info['members'])
    seen = set()
    has_init = False
    for m in members:
        if m[0] == 'F':
            _, op, name, desc, _ = m
            key = ('F', name)
            if key in seen:
                continue
            seen.add(key)
            t, _ = jtype(desc)
            if is_iface:
                lines.append('    %s %s = %s;' % (t, name, default(t)))
            else:
                static = 'static ' if op in ('getstatic', 'putstatic') else ''
                lines.append('    public %s%s %s;' % (static, t, name))
        else:
            _, op, name, desc, kind = m
            params, ret = split_method(desc)
            args = ', '.join('%s p%d' % (p, i) for i, p in enumerate(params))
            if name == '<init>':
                if has_init or is_iface:
                    continue
                has_init = True
                lines.append('    public %s(%s) { throw new UnsupportedOperationException("stub %s.<init>%s"); }' % (jname, args, owner, desc))
                continue
            key = ('M', name, desc)
            if key in seen:
                continue
            seen.add(key)
            static = op == 'invokestatic'
            mods = 'static ' if static else ('default ' if is_iface else 'public ')
            if is_iface and static:
                mods = 'static '
            lines.append('    %s%s %s(%s) { throw new UnsupportedOperationException("stub %s.%s%s"); }' % (
                mods, ret, name, args, owner, name, desc))
    if not has_init and not is_iface:
        lines.append('    public %s() { }' % jname)
    lines.append('}')
    with open(path, 'w') as f:
        f.write('\n'.join(lines) + '\n')

def default(t):
    if t in ('int', 'long', 'short', 'byte', 'char'):
        return '0'
    if t == 'boolean':
        return 'false'
    if t in ('float', 'double'):
        return '0'
    return 'null'

if __name__ == '__main__':
    main()
