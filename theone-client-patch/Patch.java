import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Patches the TheOne client classes in an unpacked jar directory. Usage: Patch <unpackedJarDir> */
public class Patch {
    static final String PKG = "dev/rex/farmbuilder/modules/";

    public static void main(String[] a) throws Exception {
        Path root = Paths.get(a[0]);
        patch(root, "Look", Patch::look);
        patch(root, "Planner", Patch::planner);
        patch(root, "OnlyBuild", cn -> { hook(cn); settings(cn, true); });
        patch(root, "HumanBuilder", cn -> { hook(cn); settings(cn, false); });
    }

    interface P { void run(ClassNode cn); }

    static void patch(Path root, String name, P p) throws IOException {
        Path f = root.resolve(PKG + name + ".class");
        ClassNode cn = new ClassNode();
        new ClassReader(Files.readAllBytes(f)).accept(cn, 0);
        p.run(cn);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        Files.write(f, cw.toByteArray());
        System.out.println("patched " + name);
    }

    static MethodNode method(ClassNode cn, String name, String desc) {
        for (MethodNode m : cn.methods) if (m.name.equals(name) && m.desc.equals(desc)) return m;
        throw new IllegalStateException(cn.name + "." + name + desc + " not found");
    }

    static void replaceBody(MethodNode m, InsnList body) {
        m.instructions = body;
        m.tryCatchBlocks = new ArrayList<>();
        m.localVariables = null;
        m.maxStack = 0;
    }

    static void look(ClassNode cn) {
        MethodNode m = method(cn, "findPlacement", "(Lnet/minecraft/class_2338;)L" + PKG + "Look$Placement;");
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PKG + "Guard", "findPlacement",
            "(Lnet/minecraft/class_2338;)L" + PKG + "Look$Placement;", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        replaceBody(m, il);
    }

    static void planner(ClassNode cn) {
        MethodNode m = method(cn, "speedProfile", "(I)[I");
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ILOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PKG + "Guard", "speedProfile", "(I)[I", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        replaceBody(m, il);
    }

    /** if (Guard.pre(this.wanted, this.planner, this)) return;  at the top of tickBuild(). */
    static void hook(ClassNode cn) {
        MethodNode m = method(cn, "tickBuild", "()V");
        LabelNode go = new LabelNode();
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, cn.name, "wanted", "Ljava/util/Map;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new FieldInsnNode(Opcodes.GETFIELD, cn.name, "planner", "L" + PKG + "Planner;"));
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PKG + "Guard", "pre",
            "(Ljava/util/Map;L" + PKG + "Planner;Lmeteordevelopment/meteorclient/systems/modules/Module;)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, go));
        il.add(new InsnNode(Opcodes.RETURN));
        il.add(go);
        il.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        // the original first instruction may be a label/frame already; insert before everything
        m.instructions.insert(il);
    }

    /** speed setting: range 1..5 -> 1..6, new description, optional default 6. */
    static void settings(ClassNode cn, boolean defaultMax) {
        MethodNode m = method(cn, "<init>", "()V");
        boolean inSpeed = false;
        int widened = 0;
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null && widened < 2; n = n.getNext()) {
            if (n instanceof LdcInsnNode l && l.cst instanceof String s && s.startsWith("How fast it aims and places")) {
                l.cst = "How fast it aims and places: 1 careful, 3 normal, 5 very fast, 6 instinct (instant aim, places every tick).";
                inSpeed = true;
                if (defaultMax) {
                    AbstractInsnNode d = l.getNext();
                    while (d.getOpcode() != Opcodes.INVOKESTATIC) d = d.getNext();
                    m.instructions.set(d.getPrevious(), new IntInsnNode(Opcodes.BIPUSH, 6)); // iconst_4 -> 6
                }
            } else if (inSpeed && n instanceof MethodInsnNode mi && mi.owner.endsWith("IntSetting$Builder")
                && (mi.name.equals("range") || mi.name.equals("sliderRange"))
                && n.getPrevious().getOpcode() == Opcodes.ICONST_5 && n.getPrevious().getPrevious().getOpcode() == Opcodes.ICONST_1) {
                m.instructions.set(n.getPrevious(), new IntInsnNode(Opcodes.BIPUSH, 6));
                widened++;
            }
        }
        if (widened != 2) throw new IllegalStateException("speed setting not patched in " + cn.name);
    }
}
