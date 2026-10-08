package meteordevelopment.meteorclient.utils.player;
import java.util.function.Predicate;
import net.minecraft.item.ItemStack;
public class InvUtils {
    public static FindItemResult findInHotbar(Predicate<ItemStack> isGood) { return null; }
    public static FindItemResult find(Predicate<ItemStack> isGood) { return null; }
    public static boolean swap(int slot, boolean swapBack) { return false; }
    public static boolean swapBack() { return false; }
    public static Action move() { return null; }
    public static class Action {
        public Action from(int index) { return this; }
        public void toHotbar(int i) {}
    }
}
