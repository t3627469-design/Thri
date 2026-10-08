package meteordevelopment.meteorclient.addons;
public abstract class MeteorAddon {
    public abstract void onInitialize();
    public void onRegisterCategories() {}
    public abstract String getPackage();
}
