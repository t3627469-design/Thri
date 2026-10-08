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
        patch(root, "Pillar", Patch::pillar);
        patch(root, "Stand", Patch::stand);
        patch(root, "OnlyBuild", cn -> { hook(cn); settings(cn, true); climbHooks(cn, true); });
        patch(root, "HumanBuilder", cn -> { hook(cn); settings(cn, false); climbHooks(cn, false); });
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
        // groundToward(float): return Guard.groundToward(f)
        MethodNode g = method(cn, "groundToward", "(F)Z");
        InsnList gl = new InsnList();
        gl.add(new VarInsnNode(Opcodes.FLOAD, 0));
        gl.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PKG + "Guard", "groundToward", "(F)Z", false));
        gl.add(new InsnNode(Opcodes.IRETURN));
        replaceBody(g, gl);
        // useCrosshairBlock(): if (!Guard.allowClick()) return false;
        MethodNode u = method(cn, "useCrosshairBlock", "()Z");
        LabelNode ok = new LabelNode();
        InsnList gate = new InsnList();
        gate.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PKG + "Guard", "allowClick", "()Z", false));
        gate.add(new JumpInsnNode(Opcodes.IFNE, ok));
        gate.add(new InsnNode(Opcodes.ICONST_0));
        gate.add(new InsnNode(Opcodes.IRETURN));
        gate.add(ok);
        gate.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        u.instructions.insert(gate);

        // attackCrosshairBlock(): if (!Guard.allowClick()) return false;   (same rule for mining)
        MethodNode a = method(cn, "attackCrosshairBlock", "()Z");
        LabelNode aok = new LabelNode();
        InsnList agate = new InsnList();
        agate.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PKG + "Guard", "allowClick", "()Z", false));
        agate.add(new JumpInsnNode(Opcodes.IFNE, aok));
        agate.add(new InsnNode(Opcodes.ICONST_0));
        agate.add(new InsnNode(Opcodes.IRETURN));
        agate.add(aok);
        agate.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        a.instructions.insert(agate);

        MethodNode m = method(cn, "findPlacement", "(Lnet/minecraft/class_2338;)L" + PKG + "Look$Placement;");
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PKG + "Guard", "findPlacement",
            "(Lnet/minecraft/class_2338;)L" + PKG + "Look$Placement;", false));
        il.add(new InsnNode(Opcodes.ARETURN));
        replaceBody(m, il);
    }

    /** Stand.find(pos, reach, planner) -> Guard.standFor(pos, reach, planner) */
    static void stand(ClassNode cn) {
        MethodNode m = method(cn, "find", "(Lnet/minecraft/class_2338;DL" + PKG + "Planner;)Lnet/minecraft/class_2338;");
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new VarInsnNode(Opcodes.DLOAD, 1));
        il.add(new VarInsnNode(Opcodes.ALOAD, 3));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PKG + "Guard", "standFor",
            "(Lnet/minecraft/class_2338;DL" + PKG + "Planner;)Lnet/minecraft/class_2338;", false));
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

    /** Pillar.tickUp: the "standing on the ground" test also waits for the wanted height. */
    static void pillar(ClassNode cn) {
        MethodNode m = method(cn, "tickUp", "(Lnet/minecraft/class_2338;DLjava/util/function/BooleanSupplier;)V");
        int n = 0;
        for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
            if (i instanceof MethodInsnNode mi && mi.owner.equals("net/minecraft/class_746") && mi.name.equals("method_24828")) {
                m.instructions.set(i, new MethodInsnNode(Opcodes.INVOKESTATIC, PKG + "Guard", "pillarReady",
                    "(Lnet/minecraft/class_746;)Z", false));
                n++;
            }
        }
        if (n != 1) throw new IllegalStateException("Pillar.tickUp onGround sites: " + n);
    }

    /**
     * Build-up hooks: Module.tick() mine-down check, goToward() fly/pillar, and only-build's own startPillar (which climbs back down too early) is retired.
     */
    static void climbHooks(ClassNode cn, boolean onlyBuild) {
        // tick(): if (Guard.always(this)) return;
        MethodNode t = method(cn, "tick", "()V");
        LabelNode go = new LabelNode();
        InsnList il = new InsnList();
        il.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PKG + "Guard", "always",
            "(Lmeteordevelopment/meteorclient/systems/modules/Module;)Z", false));
        il.add(new JumpInsnNode(Opcodes.IFEQ, go));
        il.add(new InsnNode(Opcodes.RETURN));
        il.add(go);
        il.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        t.instructions.insert(il);

        // goToward(IBaritone, BlockPos, boolean): if (Guard.goToward(this, this.planner, pos, underUs)) return;
        MethodNode g = method(cn, "goToward", "(Lbaritone/api/IBaritone;Lnet/minecraft/class_2338;Z)V");
        LabelNode go2 = new LabelNode();
        InsnList il2 = new InsnList();
        il2.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il2.add(new VarInsnNode(Opcodes.ALOAD, 0));
        il2.add(new FieldInsnNode(Opcodes.GETFIELD, cn.name, "planner", "L" + PKG + "Planner;"));
        il2.add(new VarInsnNode(Opcodes.ALOAD, 2));
        il2.add(new VarInsnNode(Opcodes.ILOAD, 3));
        il2.add(new MethodInsnNode(Opcodes.INVOKESTATIC, PKG + "Guard", "goToward",
            "(Lmeteordevelopment/meteorclient/systems/modules/Module;L" + PKG + "Planner;Lnet/minecraft/class_2338;Z)Z", false));
        il2.add(new JumpInsnNode(Opcodes.IFEQ, go2));
        il2.add(new InsnNode(Opcodes.RETURN));
        il2.add(go2);
        il2.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        g.instructions.insert(il2);

        if (onlyBuild) {
            MethodNode sp = method(cn, "startPillar", "(Lnet/minecraft/class_2338;)Z");
            InsnList no = new InsnList();
            no.add(new InsnNode(Opcodes.ICONST_0));
            no.add(new InsnNode(Opcodes.IRETURN));
            replaceBody(sp, no);
        }
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
