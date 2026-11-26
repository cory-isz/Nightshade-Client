package com.nightshade.module.hud;

import com.nightshade.NightshadeClient;
import com.nightshade.event.events.PlayerRespawnEvent;
import com.nightshade.event.events.TickEvent;
import com.nightshade.module.HudModule;
import com.nightshade.module.ModuleCategory;
import com.nightshade.module.setting.BooleanSetting;
import com.nightshade.module.setting.ColorSetting;
import com.nightshade.render.Fonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;

import java.util.Optional;

/**
 * Where you last died: coordinates, distance and direction on the HUD, a "Last Death" waypoint and a chat line when
 * you respawn. Uses the death location the server sends with every respawn, so it survives leaving and rejoining.
 */
public final class LastDeathHud extends HudModule {
    public static final String WAYPOINT_NAME = "Last Death";
    private final BooleanSetting showDistance = add(new BooleanSetting("Show Distance", "Blocks to your death spot and which way it is", true));
    private final BooleanSetting addWaypoint = add(new BooleanSetting("Add Waypoint", "Mark it with a \"Last Death\" waypoint (replaces the previous one)", true));
    private final BooleanSetting chatMessage = add(new BooleanSetting("Chat Message", "Say where you died in chat when you respawn (only you see it)", true));
    private final BooleanSetting hideUntilDeath = add(new BooleanSetting("Hide Until You Die", "Only show once there is a death to show", true));
    private final ColorSetting labelColor = add(new ColorSetting("Label Color", "Colour of the label", 0xFFFF6B6B));

    /** Last death this module knows about: position and dimension id. */
    private BlockPos deathPos;
    private String deathDim;
    private boolean announced = true;
    private boolean wasDead;
    private String line1 = "";
    private String line2 = "";
    private int width = 80;

    public LastDeathHud() {
        super("Last Death", "Shows where you last died, with a waypoint and a chat line", ModuleCategory.HUD, 0.0, 0.42);
        listen(TickEvent.Post.class, event -> tick());
        listen(PlayerRespawnEvent.class, event -> onRespawn());
    }

    private void tick() {
        if (mc.player == null) {
            return;
        }
        boolean dead = mc.player.isDeadOrDying();
        if (dead && !wasDead) {
            // right away, so the death screen and the HUD already know (the server's copy follows on respawn)
            record(mc.player.blockPosition(), dimensionId(), false);
        }
        wasDead = dead;
        if (deathPos == null) {
            // joining a world: the server tells us the last death from before
            fromServer(false);
        }
    }

    private void onRespawn() {
        fromServer(true);
        if (!announced && deathPos != null) {
            announce();
        }
    }

    /** Takes the death spot the server stores for the player, if it has one. */
    private void fromServer(boolean respawn) {
        if (mc.player == null) {
            return;
        }
        Optional<GlobalPos> last = mc.player.getLastDeathLocation();
        if (last.isPresent()) {
            GlobalPos g = last.get();
            String dim = g.dimension().identifier().toString();
            boolean changed = deathPos == null || !g.pos().equals(deathPos) || !dim.equals(deathDim);
            if (changed) {
                record(g.pos(), dim, !respawn);
            }
        }
    }

    private void record(BlockPos pos, String dim, boolean old) {
        deathPos = pos.immutable();
        deathDim = dim;
        // an old death found on joining is shown but not announced again
        announced = old;
    }

    private void announce() {
        announced = true;
        String where = deathPos.getX() + ", " + deathPos.getY() + ", " + deathPos.getZ();
        if (chatMessage.get()) {
            mc.gui.getChat().addClientSystemMessage(Component.literal("§c☠ You died at §f" + where
                    + (deathDim.equals(dimensionId()) ? "" : " §7(" + dimName(deathDim) + ")")));
        }
        if (addWaypoint.get()) {
            NightshadeClient.client().waypoints().replaceNamed(WAYPOINT_NAME, deathPos.getX(), deathPos.getY(), deathPos.getZ(), deathDim, 0xFFFF4D4D);
        }
    }

    private String dimensionId() {
        return mc.level == null ? "" : mc.level.dimension().identifier().toString();
    }

    private static String dimName(String id) {
        return switch (id) {
            case "minecraft:the_nether" -> "the Nether";
            case "minecraft:the_end" -> "the End";
            case "minecraft:overworld" -> "the Overworld";
            default -> id.substring(id.indexOf(':') + 1).replace('_', ' ');
        };
    }

    /** Compass direction from the player to the death spot, e.g. "NE". */
    private String direction() {
        double dx = deathPos.getX() + 0.5 - mc.player.getX();
        double dz = deathPos.getZ() + 0.5 - mc.player.getZ();
        double deg = (Math.toDegrees(Math.atan2(dx, -dz)) + 360) % 360;
        return new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"}[(int) Math.round(deg / 45) % 8];
    }

    @Override
    public boolean shouldRender() {
        return mc.player != null && (deathPos != null || !hideUntilDeath.get());
    }

    @Override
    protected void refresh() {
        if (deathPos == null) {
            line1 = "No deaths yet";
            line2 = "";
        } else {
            line1 = deathPos.getX() + ", " + deathPos.getY() + ", " + deathPos.getZ();
            line2 = "";
            if (showDistance.get() && mc.player != null) {
                if (deathDim.equals(dimensionId())) {
                    double d = Math.sqrt(mc.player.blockPosition().distSqr(deathPos));
                    line2 = (int) Math.round(d) + " blocks " + direction();
                } else {
                    line2 = "in " + dimName(deathDim);
                }
            }
        }
        width = Math.max(Fonts.width("Death ") + Fonts.width(line1), Fonts.width(line2)) + 8;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return (line2.isEmpty() ? 1 : 2) * (Fonts.height() + 2) + 5;
    }

    @Override
    protected void renderElement(GuiGraphicsExtractor context, float tickDelta, boolean preview) {
        if (preview && deathPos == null) {
            line1 = "120, 64, -340";
            line2 = "86 blocks NE";
            width = Math.max(Fonts.width("Death ") + Fonts.width(line1), Fonts.width(line2)) + 8;
        }
        drawBackground(context, getWidth(), getHeight());
        int x = Fonts.draw(context, "Death ", 4, 4, labelColor.argb(), shadow()) - 1;
        Fonts.draw(context, line1, x, 4, textColor.argb(), shadow());
        if (!line2.isEmpty()) {
            Fonts.draw(context, line2, 4, 4 + Fonts.height() + 2, textColor.argb(), shadow());
        }
    }

    // ------------------------------------------------------------------ test hooks

    public BlockPos deathPosForTest() {
        return deathPos;
    }

    public String linesForTest() {
        refresh();
        return line1 + " | " + line2;
    }
}
