package com.nightshade;

import com.nightshade.manager.ClientManager;
import net.fabricmc.api.ClientModInitializer;

/** Fabric client entrypoint. */
public final class NightshadeClient implements ClientModInitializer {
    public static final String MOD_ID = "nightshade";
    public static final String NAME = "Nightshade Client";
    public static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString().split("\\+")[0])
            .orElse("dev");
    private static final ClientManager CLIENT = new ClientManager();

    public static ClientManager client() {
        return CLIENT;
    }

    @Override
    public void onInitializeClient() {
        CLIENT.initialize();
    }
}
