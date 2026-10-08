import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Remaps classes compiled against yarn-named Minecraft stubs to intermediary names, using the
 * yarn enigma mapping files (which map intermediary -> named).
 *
 * Usage:
 *   Remap check <yarnMappingsDir> <stubClassesDir>
 *       verifies every member declared on a net/minecraft stub exists in the mappings
 *   Remap remap <yarnMappingsDir> <stubClassesDir> <inputClassesDir> <outputDir>
 *       remaps every class under inputClassesDir into outputDir
 *   Remap verify <yarnMappingsDir> <stubClassesDir> <intermediary.tiny> <remappedClassesDir>
 *       checks every Minecraft field/method referenced by the remapped classes against the
 *       official intermediary table (exact name and descriptor, searched up the class hierarchy)
 *
 * Any Minecraft class or member that cannot be resolved is a hard error, so a stub that does not
 * match the real game fails the build instead of producing a jar that crashes at runtime.
 */
public final class Remap {
    private static final String MC = "net/minecraft/";

    // named class -> intermediary class
    private final Map<String, String> classes = new HashMap<>();
    // intermediary class -> named class
    private final Map<String, String> classesInv = new HashMap<>();
    // raw member entries collected while parsing: intermediaryOwner, intermediaryName, namedName, intermediaryDesc, isMethod
    private final List<String[]> rawMembers = new ArrayList<>();
    // "namedOwner.namedName namedDesc" -> intermediary name
    private final Map<String, String> methods = new HashMap<>();
    // "namedOwner.namedName(params)" -> intermediary name; resolves covariant overrides whose
    // return type differs from the mapped declaration (e.g. BlockPos.down() overriding Vec3i.down())
    private final Map<String, String> methodsByParams = new HashMap<>();
    // "namedOwner.namedName" -> intermediary name (field descriptors are not needed to resolve fields)
    private final Map<String, String> fields = new HashMap<>();
    // stub hierarchy, named: class -> [super, interfaces...]
    private final Map<String, List<String>> parents = new HashMap<>();
    private final Set<String> errors = new TreeSet<>();

    public static void main(String[] args) throws IOException {
        if (args.length < 3) {
            System.err.println("usage: Remap check <yarnDir> <stubs> | Remap remap <yarnDir> <stubs> <in> <out>");
            System.exit(2);
        }
        Remap r = new Remap();
        r.loadMappings(Path.of(args[1]));
        r.loadHierarchy(Path.of(args[2]));
        switch (args[0]) {
            case "check" -> r.check(Path.of(args[2]));
            case "remap" -> r.remap(Path.of(args[3]), Path.of(args[4]));
            case "verify" -> r.verify(Path.of(args[3]), Path.of(args[4]));
            default -> {
                System.err.println("unknown mode " + args[0]);
                System.exit(2);
            }
        }
        if (!r.errors.isEmpty()) {
            r.errors.forEach(e -> System.err.println("ERROR: " + e));
            System.err.println(r.errors.size() + " unresolved Minecraft reference(s)");
            System.exit(1);
        }
    }

    // ---- mappings ----

    private void loadMappings(Path dir) throws IOException {
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".mapping"))::iterator) {
                parseFile(Files.readAllLines(p));
            }
        }
        for (String[] m : rawMembers) {
            String namedOwner = classesInv.getOrDefault(m[0], m[0]);
            if (m[4] != null) {
                String namedDesc = mapDescToNamed(m[3]);
                methods.put(namedOwner + "." + m[2] + namedDesc, m[1]);
                methodsByParams.put(namedOwner + "." + m[2] + params(namedDesc), m[1]);
            } else {
                fields.put(namedOwner + "." + m[2], m[1]);
            }
        }
        System.out.println("mappings: " + classes.size() + " classes, " + methods.size() + " methods, " + fields.size() + " fields");
    }

    private void parseFile(List<String> lines) {
        // stack of (intermediaryName, namedName) per indentation depth for nested classes
        Deque<String[]> stack = new ArrayDeque<>();
        for (String line : lines) {
            if (line.isBlank()) continue;
            int depth = 0;
            while (depth < line.length() && line.charAt(depth) == '\t') depth++;
            String[] t = line.trim().split(" ");
            switch (t[0]) {
                case "CLASS" -> {
                    while (stack.size() > depth) stack.pop();
                    String obf = t[1];
                    String named = t.length > 2 && !t[2].equals("ACCESS") ? t[2] : null;
                    String fullObf, fullNamed;
                    if (stack.isEmpty()) {
                        fullObf = obf;
                        fullNamed = named != null ? named : obf;
                    } else {
                        String[] outer = stack.peek();
                        fullObf = outer[0] + "$" + obf;
                        fullNamed = outer[1] + "$" + (named != null ? named : obf);
                    }
                    classes.put(fullNamed, fullObf);
                    classesInv.put(fullObf, fullNamed);
                    stack.push(new String[]{fullObf, fullNamed});
                }
                case "FIELD", "METHOD" -> {
                    while (stack.size() > depth) stack.pop();
                    if (stack.isEmpty() || t.length < 3) continue;
                    String owner = stack.peek()[0];
                    String obf = t[1];
                    String named = t.length >= 4 ? t[2] : obf;
                    String desc = t.length >= 4 ? t[3] : t[2];
                    rawMembers.add(new String[]{owner, obf, named, desc, t[0].equals("METHOD") ? "m" : null});
                }
                default -> {
                }
            }
        }
    }

    private static String params(String methodDesc) {
        return methodDesc.substring(0, methodDesc.indexOf(')') + 1);
    }

    private String mapDescToNamed(String desc) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < desc.length(); i++) {
            char c = desc.charAt(i);
            out.append(c);
            if (c == 'L') {
                int end = desc.indexOf(';', i);
                String name = desc.substring(i + 1, end);
                out.append(classesInv.getOrDefault(name, name)).append(';');
                i = end;
            }
        }
        return out.toString();
    }

    // ---- stub hierarchy ----

    private void loadHierarchy(Path stubs) throws IOException {
        forEachClass(stubs, (path, bytes) -> {
            ClassReader cr = new ClassReader(bytes);
            List<String> ps = new ArrayList<>();
            if (cr.getSuperName() != null) ps.add(cr.getSuperName());
            ps.addAll(List.of(cr.getInterfaces()));
            parents.put(cr.getClassName(), ps);
        });
    }

    private Iterable<String> ancestors(String owner) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> todo = new ArrayDeque<>();
        todo.add(owner);
        while (!todo.isEmpty()) {
            String c = todo.poll();
            if (!seen.add(c)) continue;
            todo.addAll(parents.getOrDefault(c, List.of()));
        }
        return seen;
    }

    private static boolean isMc(String internalName) {
        return internalName.startsWith(MC);
    }

    // Methods every Minecraft class inherits from java.lang and that are never renamed.
    private static final Set<String> JAVA_METHODS = Set.of(
        "<init>", "<clinit>", "values", "valueOf", "ordinal", "name", "equals", "hashCode", "toString",
        "compareTo", "getClass", "getDeclaringClass", "clone", "iterator", "forEach", "spliterator");

    private final Remapper remapper = new Remapper() {
        @Override
        public String map(String internalName) {
            if (!isMc(internalName)) return internalName;
            String mapped = classes.get(internalName);
            if (mapped == null) {
                errors.add("class " + internalName);
                return internalName;
            }
            return mapped;
        }

        @Override
        public String mapMethodName(String owner, String name, String descriptor) {
            if (owner.startsWith("[")) return name;
            for (String c : ancestors(owner)) {
                if (!isMc(c)) continue;
                String m = methods.get(c + "." + name + descriptor);
                if (m != null) return m;
            }
            // Intermediary gives an override the same name as the method it overrides, so a
            // covariant override resolves through the ancestor's declaration.
            if (!name.startsWith("<")) {
                for (String c : ancestors(owner)) {
                    if (!isMc(c) || c.equals(owner)) continue;
                    String m = methodsByParams.get(c + "." + name + params(descriptor));
                    if (m != null) return m;
                }
            }
            if (isMcHierarchy(owner) && !JAVA_METHODS.contains(name)) {
                errors.add("method " + owner + "." + name + descriptor);
            }
            return name;
        }

        @Override
        public String mapFieldName(String owner, String name, String descriptor) {
            for (String c : ancestors(owner)) {
                if (!isMc(c)) continue;
                String f = fields.get(c + "." + name);
                if (f != null) return f;
            }
            if (isMcHierarchy(owner)) errors.add("field " + owner + "." + name + " " + descriptor);
            return name;
        }

        @Override
        public String mapRecordComponentName(String owner, String name, String descriptor) {
            return name;
        }
    };

    private boolean isMcHierarchy(String owner) {
        for (String c : ancestors(owner)) if (isMc(c)) return true;
        return false;
    }

    // ---- modes ----

    private void check(Path stubs) throws IOException {
        forEachClass(stubs, (path, bytes) -> {
            ClassReader cr = new ClassReader(bytes);
            String owner = cr.getClassName();
            if (!isMc(owner)) return;
            remapper.map(owner);
            cr.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    // Enum constants are named by yarn's build, not its source mappings; addon code
                    // never references them directly (the remap pass rejects any such reference).
                    if ((access & (Opcodes.ACC_SYNTHETIC | Opcodes.ACC_ENUM)) != 0) return null;
                    remapper.mapFieldName(owner, name, descriptor);
                    remapper.mapDesc(descriptor);
                    return null;
                }

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    if ((access & Opcodes.ACC_SYNTHETIC) == 0) {
                        remapper.mapMethodName(owner, name, descriptor);
                        remapper.mapMethodDesc(descriptor);
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE);
        });
        System.out.println("check finished");
    }

    private void remap(Path in, Path out) throws IOException {
        forEachClass(in, (path, bytes) -> {
            ClassReader cr = new ClassReader(bytes);
            ClassWriter cw = new ClassWriter(0);
            cr.accept(new ClassRemapper(cw, remapper), 0);
            Path target = out.resolve(in.relativize(path));
            Files.createDirectories(target.getParent());
            Files.write(target, cw.toByteArray());
        });
        System.out.println("remapped classes into " + out);
    }

    private void verify(Path tiny, Path remapped) throws IOException {
        // obf -> intermediary classes, then members keyed in intermediary terms
        Map<String, String> obfToInt = new HashMap<>();
        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(tiny)) {
            String[] t = line.split("\t");
            if (t[0].equals("CLASS") && t.length >= 3) obfToInt.put(t[1], t[2]);
            else if ((t[0].equals("METHOD") || t[0].equals("FIELD")) && t.length >= 5) rows.add(t);
        }
        Set<String> members = new java.util.HashSet<>();
        for (String[] t : rows) {
            String owner = obfToInt.getOrDefault(t[1], t[1]);
            members.add(t[0].charAt(0) + owner + "." + t[4] + translate(t[2], obfToInt));
        }
        Set<String> knownClasses = new java.util.HashSet<>(obfToInt.values());

        // stub hierarchy in intermediary names
        Map<String, List<String>> intParents = new HashMap<>();
        for (Map.Entry<String, List<String>> e : parents.entrySet()) {
            List<String> ps = new ArrayList<>();
            for (String p : e.getValue()) ps.add(isMc(p) ? classes.getOrDefault(p, p) : p);
            intParents.put(isMc(e.getKey()) ? classes.getOrDefault(e.getKey(), e.getKey()) : e.getKey(), ps);
        }
        int[] checked = {0};
        forEachClass(remapped, (path, bytes) -> new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String n, String d, boolean itf) {
                        check('M', owner, n, d);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String n, String d) {
                        check('F', owner, n, d);
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        checkClass(type);
                    }

                    @Override
                    public void visitInvokeDynamicInsn(String n, String d, org.objectweb.asm.Handle bsm, Object... args) {
                        for (Object a : args) {
                            if (a instanceof org.objectweb.asm.Handle h) {
                                check(h.getTag() <= Opcodes.H_PUTSTATIC ? 'F' : 'M', h.getOwner(), h.getName(), h.getDesc());
                            }
                        }
                    }

                    private void checkClass(String type) {
                        String t = type.replaceAll("^\\[+L?|;$", "");
                        if (isMc(t) && !knownClasses.contains(t)) errors.add("verify: unknown class " + t);
                    }

                    private void check(char kind, String owner, String n, String d) {
                        if (!isMc(owner) || owner.startsWith("[")) return;
                        checked[0]++;
                        checkClass(owner);
                        if (kind == 'M' && (n.equals("<init>") || n.equals("values") || n.equals("ordinal"))) {
                            // Constructors keep their name and are not listed in the tiny table;
                            // their descriptors were already checked against the yarn sources.
                            return;
                        }
                        for (String c : ancestorsIn(intParents, owner)) {
                            if (members.contains(kind + c + "." + n + d)) return;
                        }
                        errors.add("verify: " + (kind == 'M' ? "method " : "field ") + owner + "." + n + d
                            + " (" + classesInv.getOrDefault(owner, owner) + ") does not exist in 1.21.11");
                    }
                };
            }
        }, 0));
        System.out.println("verified " + checked[0] + " Minecraft member references against " + tiny.getFileName());
    }

    private static Iterable<String> ancestorsIn(Map<String, List<String>> graph, String start) {
        Set<String> seen = new LinkedHashSet<>();
        Deque<String> todo = new ArrayDeque<>();
        todo.add(start);
        while (!todo.isEmpty()) {
            String c = todo.poll();
            if (seen.add(c)) todo.addAll(graph.getOrDefault(c, List.of()));
        }
        return seen;
    }

    private static String translate(String desc, Map<String, String> map) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < desc.length(); i++) {
            char c = desc.charAt(i);
            out.append(c);
            if (c == 'L') {
                int end = desc.indexOf(';', i);
                String name = desc.substring(i + 1, end);
                out.append(map.getOrDefault(name, name)).append(';');
                i = end;
            }
        }
        return out.toString();
    }

    private interface ClassConsumer {
        void accept(Path path, byte[] bytes) throws IOException;
    }

    private static void forEachClass(Path root, ClassConsumer consumer) throws IOException {
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : (Iterable<Path>) files.filter(f -> f.toString().endsWith(".class"))::iterator) {
                consumer.accept(p, Files.readAllBytes(p));
            }
        }
    }

}
