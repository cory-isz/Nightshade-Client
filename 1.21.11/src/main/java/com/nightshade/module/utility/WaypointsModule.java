package com.nightshade.module.utility;

import com.nightshade.NightshadeClient;
import com.nightshade.gui.menu.WaypointsScreen;
import com.nightshade.module.Module;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.ModeSetting;
import com.nightshade.waypoint.Waypoint;

/** Bind this to add a waypoint where you stand, or open the waypoint list. */
public final class WaypointsModule extends Module {
    private final ModeSetting action = add(new ModeSetting("Key Action", "What the keybind does", "Add Here", "Add Here", "Open List"));

    public WaypointsModule() {
        super("Waypoint Manager", "Add waypoints and manage the waypoint list", ModuleCategory.UTILITY);
    }

    @Override
    public boolean isToggleable() {
        return false;
    }

    @Override
    public void onAction() {
        if (action.is("Open List") || mc.screen != null) {
            mc.setScreen(new WaypointsScreen(mc.screen));
            return;
        }
        Waypoint waypoint = NightshadeClient.client().waypoints().addHere(null);
        if (waypoint != null) {
            NightshadeClient.client().notifications().success("Waypoints", "Added " + waypoint.name() + " at "
                    + waypoint.x() + ", " + waypoint.y() + ", " + waypoint.z());
        } else {
            NightshadeClient.client().notifications().warn("Waypoints", "Join a world to add waypoints");
        }
    }
}
