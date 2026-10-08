package meteordevelopment.meteorclient.utils.player;
public record FindItemResult(int slot, int count) {
    public boolean found() { return false; }
    public boolean isOffhand() { return false; }
    public boolean isHotbar() { return false; }
    public boolean isMain() { return false; }
}
