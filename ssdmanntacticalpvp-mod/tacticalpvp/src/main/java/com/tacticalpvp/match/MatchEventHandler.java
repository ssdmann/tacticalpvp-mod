package com.tacticalpvp.match;

import com.tacticalpvp.TacticalPvpMod;
import com.tacticalpvp.capture.CapturePointTicker;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "tacticalpvp", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MatchEventHandler {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        MatchManager.serverTick(server);
        CapturePointTicker.tickAllPoints(server);
    }

    // players awaiting lobby routing on their next respawn (death -> respawn button -> here)
    private static final Set<UUID> pendingLobbyRoute = new HashSet<>();

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;

        var server = victim.getServer();
        if (server == null) return;

        MatchSavedData data = MatchSavedData.get(server);
        if (data.getState() != MatchState.RUNNING) return;

        TeamColor victimTeam = TacticalPvpMod.getPlayerTeam(victim);

        // Award the kill to the killer's team if the killer is on the opposing team.
        var attacker = event.getSource().getEntity();
        if (attacker instanceof ServerPlayer killer) {
            TeamColor killerTeam = TacticalPvpMod.getPlayerTeam(killer);
            if (killerTeam != TeamColor.NONE && killerTeam != victimTeam) {
                MatchManager.recordKill(server, killerTeam);
            }
        }

        if (victimTeam != TeamColor.NONE) {
            pendingLobbyRoute.add(victim.getUUID());
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!pendingLobbyRoute.remove(player.getUUID())) return;

        TeamColor team = TacticalPvpMod.getPlayerTeam(player);
        if (team != TeamColor.NONE) {
            MatchManager.sendToWaitingLobby(player, team);
        }
    }
}
