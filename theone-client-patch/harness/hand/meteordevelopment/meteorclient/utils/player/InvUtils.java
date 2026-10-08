package meteordevelopment.meteorclient.utils.player;
public class InvUtils {
    public static Object move() { return null; }
    public static boolean swap(int slot, boolean silent) {
        if (slot < 0 || slot > 8) return false;
        meteordevelopment.meteorclient.MeteorClient.mc.field_1724.method_31548().method_67533(slot);
        return true;
    }
}
