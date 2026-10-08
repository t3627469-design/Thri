package dev.thri;

import dev.thri.config.ConfigManager;
import dev.thri.event.EventBus;
import dev.thri.module.ModuleManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Thri implements ClientModInitializer {
    public static final String ID = "thri";
    public static final Logger LOG = LoggerFactory.getLogger(ID);
    public static final EventBus BUS = new EventBus();
    public static ModuleManager MODULES;
    public static ConfigManager CONFIG;

    @Override
    public void onInitializeClient() {
        MODULES = new ModuleManager();
        CONFIG  = new ConfigManager();
        CONFIG.load();
        ClientTickEvents.END_CLIENT_TICK.register(mc -> MODULES.onTick());
        Runtime.getRuntime().addShutdownHook(new Thread(CONFIG::save));
        LOG.info("Thri loaded with {} modules", MODULES.all().size());
    }
}
