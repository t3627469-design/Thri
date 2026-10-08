package meteordevelopment.meteorclient.utils.world;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
public class BlockUtils {
    public static boolean isClickable(Block block) { return false; }
    public static void interact(BlockHitResult blockHitResult, Hand hand, boolean swing) {}
    public static boolean canBreak(BlockPos blockPos, BlockState state) { return false; }
    public static boolean breakBlock(BlockPos blockPos, boolean swing) { return false; }
}
