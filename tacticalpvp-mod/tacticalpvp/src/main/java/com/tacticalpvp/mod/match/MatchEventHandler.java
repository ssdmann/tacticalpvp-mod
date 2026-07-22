package com.tacticalpvp.mod.match;

import com.tacticalpvp.mod.network.MatchHudSyncPacket;
import com.tacticalpvp.mod.network.NetworkHandler;
import com.tacticalpvp.mod.points.SpawnLogic;
import com.tacticalpvp.mod.points.TeamSpawnPoint;
import com.tacticalpvp.mod.util.PlayerTeamTracker;
import com.tacticalpvp.mod.util.Team;
import com.tacticalpvp.mod.util.TacticalWorldData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Життєвий цикл матчу:
 *  LOBBY -> (/match teleporteams) -> PRESTART (відлік + видача Ока Ендера) ->
 *  RUNNING (рахунок/таймер активні) -> POSTMATCH (заголовок перемоги + відлік) -> LOBBY
 *
 * Також обробляє: вхід у зони вибору команди, смерть/friendly-fire рахунок,
 * очікування у лобі команди після смерті і відродження на фронтовому спавні.
 */
public class MatchEventHandler {

    // playerId -> тіків лишилось у "лобі очікування" після смерті
    private final Map<UUID, Integer> waitingPlayers = new HashMap<>();

    // ================= TICK: фази матчу =================
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        TacticalWorldData data = TacticalWorldData.get(level);
        MatchState state = data.matchState;

        switch (state.phase) {
            case PRESTART -> tickPrestart(server, state);
            case RUNNING -> tickRunning(server, data, state);
            case POSTMATCH -> tickPostmatch(server, data, state);
            default -> {}
        }

        tickWaitingPlayers(server, data);
        checkTeamZones(server, data);
    }

    private void tickPrestart(MinecraftServer server, MatchState state) {
        state.prestartRemainingTicks--;
        int secondsLeft = state.prestartRemainingTicks / 20;

        if (state.prestartRemainingTicks <= 0) {
            state.phase = MatchState.Phase.RUNNING;
            state.matchRemainingTicks = state.matchDurationTicks;
            broadcast(server, Component.translatable("message.tacticalpvp.match_started"));
            return;
        }

        if (secondsLeft <= 10 && state.prestartRemainingTicks % 20 == 0) {
            if (secondsLeft > 0) {
                broadcast(server, Component.literal(Integer.toString(secondsLeft)));
            }
        } else if (state.prestartRemainingTicks % (20 * 20) == 0) {
            // кожні 20 секунд під час основної частини відліку
            broadcast(server, Component.translatable("message.tacticalpvp.prestart_countdown", secondsLeft));
        }

        if (state.prestartRemainingTicks == 20) {
            // за секунду до кінця готуємо фінальне повідомлення "5,4,3,2,1,Старт!" пострілково нижче,
            // саме "Старт!" виводиться при переході в RUNNING (secondsLeft==0 гілка вище).
        }
    }

    private void tickRunning(MinecraftServer server, TacticalWorldData data, MatchState state) {
        state.matchRemainingTicks--;
        syncHudAll(server, state);

        if (state.redScore >= state.scoreLimit) {
            triggerVictory(server, data, state, Team.RED);
        } else if (state.blueScore >= state.scoreLimit) {
            triggerVictory(server, data, state, Team.BLUE);
        } else if (state.matchRemainingTicks <= 0) {
            Team winner = state.redScore == state.blueScore ? Team.NEUTRAL
                    : (state.redScore > state.blueScore ? Team.RED : Team.BLUE);
            triggerVictory(server, data, state, winner);
        }
    }

    private void triggerVictory(MinecraftServer server, TacticalWorldData data, MatchState state, Team winner) {
        state.phase = MatchState.Phase.POSTMATCH;
        state.postMatchRemainingTicks = state.postMatchCountdownTicks;

        String titleText = winner == Team.NEUTRAL ? "НІЧИЯ!" : winner.fullTitleWord() + " ПЕРЕМОГЛИ!";
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                    Component.literal(titleText)));
        }
    }

    private void tickPostmatch(MinecraftServer server, TacticalWorldData data, MatchState state) {
        state.postMatchRemainingTicks--;
        if (state.postMatchRemainingTicks <= 0) {
            // Повернення у загальне лобі + повне очищення інвентарю
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.getInventory().clearContent();
                if (state.generalLobby != null) {
                    p.teleportTo(server.overworld(), state.generalLobby.getX() + 0.5,
                            state.generalLobby.getY(), state.generalLobby.getZ() + 0.5, p.getYRot(), p.getXRot());
                }
            }
            state.phase = MatchState.Phase.LOBBY;
            state.redScore = 0;
            state.blueScore = 0;
            PlayerTeamTracker.clear();
            com.tacticalpvp.mod.TacticalPvpMod.CLASS_LIMIT_MANAGER.reset();
        }
    }

    // ================= Смерть / Friendly Fire рахунок =================
    @SubscribeEvent
    public void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        MinecraftServer server = victim.getServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        TacticalWorldData data = TacticalWorldData.get(level);
        if (data.matchState.phase != MatchState.Phase.RUNNING) return;

        Team victimTeam = PlayerTeamTracker.get(victim.getUUID());
        Team scoringTeam = victimTeam.opposite();

        // Навіть якщо вбивця - союзник (friendly fire), очко йде ВОРОЖІЙ команді.
        if (scoringTeam == Team.RED) data.matchState.redScore++;
        else if (scoringTeam == Team.BLUE) data.matchState.blueScore++;
        data.setDirty();

        // Телепорт у лобі очікування команди гравця; після lobbyTimer - на фронтовий спавн
        Team team = PlayerTeamTracker.get(victim.getUUID());
        var lobby = team == Team.RED ? data.matchState.redLobby : data.matchState.blueLobby;
        if (lobby != null) {
            server.execute(() -> victim.teleportTo(level, lobby.getX() + 0.5, lobby.getY(), lobby.getZ() + 0.5,
                    victim.getYRot(), victim.getXRot()));
        }
        waitingPlayers.put(victim.getUUID(), data.matchState.lobbyTimerTicks);
    }

    private void tickWaitingPlayers(MinecraftServer server, TacticalWorldData data) {
        Iterator<Map.Entry<UUID, Integer>> it = waitingPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            int remaining = e.getValue() - 1;
            if (remaining <= 0) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p != null) respawnAtFrontline(server, data, p);
                it.remove();
            } else {
                e.setValue(remaining);
            }
        }
    }

    private void respawnAtFrontline(MinecraftServer server, TacticalWorldData data, ServerPlayer player) {
        Team team = PlayerTeamTracker.get(player.getUUID());
        TeamSpawnPoint spawn = SpawnLogic.findActiveFrontlineSpawn(data.points, team);
        if (spawn == null) return; // немає жодної активної точки для команди ще
        var rand = player.getRandom();
        int radius = TeamSpawnPoint.SPAWN_RADIUS;
        double dx = (rand.nextDouble() * 2 - 1) * radius;
        double dz = (rand.nextDouble() * 2 - 1) * radius;
        player.teleportTo(server.overworld(), spawn.pos.getX() + 0.5 + dx, spawn.pos.getY(),
                spawn.pos.getZ() + 0.5 + dz, player.getYRot(), player.getXRot());
    }

    // ================= Зони вибору команди =================
    private void checkTeamZones(MinecraftServer server, TacticalWorldData data) {
        boolean swappingLocked = data.matchState.phase != MatchState.Phase.LOBBY;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (TeamZone zone : data.teamZones) {
                if (!zone.isInside(player.blockPosition())) continue;
                Team current = PlayerTeamTracker.get(player.getUUID());
                if (current == zone.team) continue; // вже у цій команді
                if (current != Team.NEUTRAL && swappingLocked) continue; // зміна команди заблокована після старту

                PlayerTeamTracker.set(player.getUUID(), zone.team);
                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                        Component.literal(zone.team.fullTitleWord())));
            }
        }
    }

    // ================= Допоміжне =================
    private void broadcast(MinecraftServer server, Component component) {
        server.getPlayerList().getPlayers().forEach(p -> p.sendSystemMessage(component));
    }

    private void syncHudAll(MinecraftServer server, MatchState state) {
        int secondsLeft = Math.max(0, state.matchRemainingTicks / 20);
        MatchHudSyncPacket packet = new MatchHudSyncPacket(state.redScore, state.blueScore, secondsLeft);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
        }
    }
}
