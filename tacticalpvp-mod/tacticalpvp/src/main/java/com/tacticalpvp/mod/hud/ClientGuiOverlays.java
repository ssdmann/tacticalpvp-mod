package com.tacticalpvp.mod.hud;

import com.tacticalpvp.mod.TacticalPvpMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.gui.overlay.NamedGuiOverlay;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;

/**
 * Реєструє два нових оверлеї:
 *  1) "azimuth_compass" — стрічка компаса вгорі екрана
 *  2) "match_hud" — рахунок команд + таймер матчу, ЗСУНУТИЙ трохи нижче,
 *     щоб не перекриватись зі стрічкою компаса (п.6 специфікації).
 */
public class ClientGuiOverlays {

    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("azimuth_compass", new CompassOverlay());
        event.registerBelow(net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.HOTBAR.id(),
                "match_hud", new MatchHudOverlay());
    }
}
