package net.fabricmc.loader.api;
public interface FabricLoader {
    static FabricLoader getInstance() { throw new AssertionError(); }
    java.nio.file.Path getGameDir();
}
