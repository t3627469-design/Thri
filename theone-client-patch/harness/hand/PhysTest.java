import java.util.*;
import net.minecraft.*;
public class PhysTest {
    public static void main(String[] a) {
        Sim sim = new Sim();
        class_2248 stone = new class_2248("minecraft:stone", false, false);
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) sim.world.method_8501(new class_2338(x, -1, z), new class_2680(stone, stone.defaults));
        // a 1-high step at x=3..5, z=0..6 (on top of ground)
        for (int x = 3; x <= 5; x++) for (int z = 0; z <= 6; z++) sim.world.method_8501(new class_2338(x, 0, z), new class_2680(stone, stone.defaults));
        sim.player.x = -1.5; sim.player.y = 0; sim.player.z = 3.5; sim.player.yaw = 270; // yaw 270 -> walks +x
        for (int t = 0; t < 60; t++) {
            sim.tickPlayer(true, false, false, false, t == 30, 0.2);
            if (t % 5 == 0 || t == 0) System.out.printf("t%d x=%.2f y=%.2f z=%.2f on=%b vy=%.2f%n", t, sim.player.x, sim.player.y, sim.player.z, sim.player.onGround, sim.player.vy);
        }
        // stand still on flat ground for 20 ticks: must not drift off y=0
        sim.player.x = -5.5; sim.player.y = 0; sim.player.z = 9.5;
        for (int t = 0; t < 20; t++) sim.tickPlayer(false, false, false, false, false, 0.2);
        System.out.printf("idle y=%.3f on=%b%n", sim.player.y, sim.player.onGround);
    }
}
