package com.tacticalpvp.match;

import com.tacticalpvp.TacticalPvpMod;
import com.tacticalpvp.network.NetworkHandler;
import com.tacticalpvp.network.packet.ActionBarPacket;
import com.tacticalpvp.network.packet.HudSyncPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the round lifecycle. This class holds no persistent state itself
 * (all persisted values live in MatchSavedData) but drives ticking, scoring,
 * team win checks, and the transient in-memory "player is waiting in the lobby" queue.
 */
public class MatchManager {

    // transient per-player waiting-lobby countdown (not persisted - a mid-wait
    // server restart simply drops the player back into survival on next tick pass,
    // which is acceptable since lobby waits are short-lived).
    private static final Map<UUID, Integer> respawnQueue = new HashMap<>();

    public static void startMatch(MinecraftServer server) {
        MatchSavedData data = MatchSavedData.get(server);
        data.resetScores();
        data.setState(MatchState.RUNNING);
        data.setRemainingTicks(data.isInfiniteTime() ? 0 : data.getConfiguredDurationSeconds() * 20);

        // ensure keepInventory is on as required by the spec
        server.overworld().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(true, server);

        broadcast(server, Component.translatable("message.tacticalpvp.match_started")
                .withStyle(ChatFormatting.GREEN));
        syncHudToAll(server);
    }

    public static void stopMatch(MinecraftServer server) {
        MatchSavedData data = MatchSavedData.get(server);
        data.setState(MatchState.IDLE);
        respawnQueue.clear();
        broadcast(server, Component.translatable("message.tacticalpvp.match_stopped")
                .withStyle(ChatFormatting.YELLOW));
        syncHudToAll(server);
    }

    /** @return true if the match was RUNNING and is now PAUSED; false if the state didn't allow it. */
    public static boolean pauseMatch(MinecraftServer server) {
        MatchSavedData data = MatchSavedData.get(server);
        if (data.getState() != MatchState.RUNNING) {
            return false;
        }
        data.setState(MatchState.PAUSED);
        broadcast(server, Component.translatable("message.tacticalpvp.match_paused")
                .withStyle(ChatFormatting.YELLOW));
        syncHudToAll(server);
        return true;
    }

    /** @return true if the match was PAUSED and is now RUNNING again; false if the state didn't allow it. */
    public static boolean resumeMatch(MinecraftServer server) {
        MatchSavedData data = MatchSavedData.get(server);
        if (data.getState() != MatchState.PAUSED) {
            return false;
        }
        data.setState(MatchState.RUNNING);
        broadcast(server, Component.translatable("message.tacticalpvp.match_resumed")
                .withStyle(ChatFormatting.GREEN));
        syncHudToAll(server);
        return true;
    }

    private static void endMatch(MinecraftServer server, TeamColor winner) {
        MatchSavedData data = MatchSavedData.get(server);
        data.setState(MatchState.ENDED);

        Component winnerName = winner == TeamColor.NONE
                ? Component.translatable("message.tacticalpvp.draw")
                : Component.translatable("message.tacticalpvp.team_" + winner.getKey());

        broadcast(server, Component.translatable("message.tacticalpvp.match_ended", winnerName)
                .withStyle(ChatFormatting.GOLD));
        syncHudToAll(server);
    }

    /**
     * Call once per server tick regardless of match state. Internally no-ops the
     * timer/score/win-check logic unless the match is RUNNING - this means the
     * PAUSED state automatically freezes the timer and win conditions for free.
     */
    public static void serverTick(MinecraftServer server) {
        MatchSavedData data = MatchSavedData.get(server);

        tickRespawnQueue(server);

        if (data.getState() != MatchState.RUNNING) {
            return;
        }

        data.decrementRemainingTicks();

        boolean timeUp = !data.isInfiniteTime() && data.getRemainingTicks() <= 0;
        boolean redWon = data.getScore(TeamColor.RED) >= data.getScoreLimit();
        boolean blueWon = data.getScore(TeamColor.BLUE) >= data.getScoreLimit();

        if (redWon || blueWon) {
            TeamColor winner = (redWon && blueWon)
                    ? (data.getScore(TeamColor.RED) == data.getScore(TeamColor.BLUE) ? TeamColor.NONE
                        : (data.getScore(TeamColor.RED) > data.getScore(TeamColor.BLUE) ? TeamColor.RED : TeamColor.BLUE))
                    : (redWon ? TeamColor.RED : TeamColor.BLUE);
            endMatch(server, winner);
            return;
        }

        if (timeUp) {
            int r = data.getScore(TeamColor.RED);
            int b = data.getScore(TeamColor.BLUE);
            TeamColor winner = r == b ? TeamColor.NONE : (r > b ? TeamColor.RED : TeamColor.BLUE);
            endMatch(server, winner);
            return;
        }

        // periodic HUD sync (every 10 ticks / 0.5s is plenty smooth without flooding the network)
        if (data.getRemainingTicks() % 10 == 0) {
            syncHudToAll(server);
        }
    }

    public static void recordKill(MinecraftServer server, TeamColor killerTeam) {
        MatchSavedData data = MatchSavedData.get(server);
        if (data.getState() != MatchState.RUNNING || killerTeam == TeamColor.NONE) return;
        data.addScore(killerTeam, 1);
        syncHudToAll(server);
    }

    // ---------- waiting lobby / respawn queue ----------

    public static void sendToWaitingLobby(ServerPlayer player, TeamColor team) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        MatchSavedData data = MatchSavedData.get(server);

        BlockPos lobby = data.getLobby(team);
        if (lobby != null) {
            ServerLevel level = server.overworld();
            player.teleportTo(level, lobby.getX() + 0.5, lobby.getY(), lobby.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
        }

        player.setGameMode(GameType.ADVENTURE);
        player.setInvulnerable(true);
        player.setHealth(player.getMaxHealth());

        int seconds = Math.max(0, data.getLobbyTimerSeconds());
        respawnQueue.put(player.getUUID(), seconds * 20);

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ActionBarPacket(Component.translatable("message.tacticalpvp.waiting_lobby", seconds)));
    }

    private static void tickRespawnQueue(MinecraftServer server) {
        if (respawnQueue.isEmpty()) return;

        respawnQueue.entrySet().removeIf(entry -> {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null) {
                return true; // player left, drop them from the queue
            }
            int remaining = entry.getValue() - 1;
            if (remaining <= 0) {
                releaseFromLobby(player);
                return true;
            }
            entry.setValue(remaining);
            if (remaining % 20 == 0) {
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new ActionBarPacket(Component.translatable("message.tacticalpvp.waiting_lobby", remaining / 20)));
            }
            return false;
        });
    }

    private static void releaseFromLobby(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        MatchSavedData data = MatchSavedData.get(server);

        TeamColor team = TacticalPvpMod.getPlayerTeam(player);
        player.setGameMode(GameType.SURVIVAL);
        player.setInvulnerable(false);

        var frontline = data.getFrontlinePointFor(team);
        if (frontline != null) {
            var pos = frontline.getMinCorner();
            var max = frontline.getMaxCorner();
            double cx = (pos.getX() + max.getX()) / 2.0 + 0.5;
            double cz = (pos.getZ() + max.getZ()) / 2.0 + 0.5;
            player.teleportTo(server.overworld(), cx, pos.getY() + 1, cz, player.getYRot(), player.getXRot());
        }

        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ActionBarPacket(Component.translatable("message.tacticalpvp.respawned")));
    }

    // ---------- helpers ----------

    private static void broadcast(MinecraftServer server, Component message) {
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    public static void syncHudToAll(MinecraftServer server) {
        MatchSavedData data = MatchSavedData.get(server);
        HudSyncPacket packet = new HudSyncPacket(
                data.getScore(TeamColor.RED),
                data.getScore(TeamColor.BLUE),
                data.getRemainingTicks(),
                data.isInfiniteTime(),
                data.getState()
        );
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
        }
    }
}
