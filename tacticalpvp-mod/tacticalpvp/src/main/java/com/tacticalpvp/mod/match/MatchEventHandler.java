package com.tacticalpvp.mod.match;

import com.tacticalpvp.mod.network.MatchHudSyncPacket;
import com.tacticalpvp.mod.network.NetworkHandler;
import com.tacticalpvp.mod.points.CapturePoint;
import com.tacticalpvp.mod.points.SpawnLogic;
import com.tacticalpvp.mod.points.TeamSpawnPoint;
import com.tacticalpvp.mod.util.PlayerTeamTracker;
import com.tacticalpvp.mod.util.TacticalWorldData;
import com.tacticalpvp.mod.util.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class MatchEventHandler {

    private final Map<UUID, Integer> waitingPlayers = new HashMap<>();
    private int zoneParticleTimer = 0;

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
        drawTeamZones(level, data); // Підсвітка зон вибору команд частками
    }

    private void tickPrestart(MinecraftServer server, MatchState state) {
        state.prestartRemainingTicks--;
        int secondsLeft = state.prestartRemainingTicks / 20;

        // --- ФІНАЛ ВІДЛІКУ: АВТО-ВИБІР КІТІВ ТА ЗАПУСК МАТЧУ ---
        if (state.prestartRemainingTicks <= 0) {
            ServerLevel level = server.overworld();
            TacticalWorldData data = TacticalWorldData.get(level);

            // 1. Автоматично видаємо кіт гравцям, які не обрали його самі
            assignDefaultKitsToRemainingPlayers(server, data);

            // 2. Переводимо матч у фазу RUNNING
            state.phase = MatchState.Phase.RUNNING;
            state.matchRemainingTicks = state.matchDurationTicks;
            data.setDirty();

            broadcast(server, Component.translatable("message.tacticalpvp.match_started"));
            return;
        }

        // Повідомлення в чат / над хотбаром
        if (secondsLeft <= 10 && state.prestartRemainingTicks % 20 == 0) {
            if (secondsLeft > 0) {
                Component msg = Component.literal("До старту: " + secondsLeft + " сек.");
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    p.displayClientMessage(msg, true); // Повідомлення НАД хотбаром (ActionBar)
                }
            }
        } else if (state.prestartRemainingTicks % (20 * 20) == 0 && secondsLeft > 0) {
            broadcast(server, Component.translatable("message.tacticalpvp.prestart_countdown", secondsLeft / 60));
        }
    }

    /**
     * Знаходить гравців із "Оком Ендера" в інвентарі (які не обрали клас)
     * і видає їм перший вільний за лімітом клас.
     */
    private void assignDefaultKitsToRemainingPlayers(MinecraftServer server, TacticalWorldData data) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Team team = PlayerTeamTracker.get(player.getUUID());
            if (team == Team.NEUTRAL) continue;

            // Перевіряємо чи є у гравця Око вибору класу
            boolean hasEye = false;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                if (player.getInventory().getItem(i).getItem() == com.tacticalpvp.mod.item.ModItems.ENDER_EYE_KIT.get()) {
                    hasEye = true;
                    break;
                }
            }

            if (hasEye) {
                int teamSize = PlayerTeamTracker.countTeam(team);
                com.tacticalpvp.mod.kits.ClassLimitManager limitManager = com.tacticalpvp.mod.TacticalPvpMod.CLASS_LIMIT_MANAGER;

                // Шукаємо перший клас, у якого є вільний слот
                for (com.tacticalpvp.mod.kits.PlayerClass pClass : com.tacticalpvp.mod.kits.PlayerClass.values()) {
                    if (limitManager.hasFreeSlot(team, pClass, teamSize)) {
                        // Призначаємо клас і видаємо кіт
                        limitManager.assign(player.getUUID(), team, pClass);
                        com.tacticalpvp.mod.kits.KitDispenser.giveKit(player, team, pClass);

                        // Видаляємо Око з інвентарю
                        player.getInventory().clearOrCountMatchingItems(
                                stack -> stack.getItem() == com.tacticalpvp.mod.item.ModItems.ENDER_EYE_KIT.get(), 1, player.getInventory());

                        player.sendSystemMessage(Component.literal("§eЧас вийшов! Вам автоматично призначено клас: §a" + pClass.uaName));
                        break;
                    }
                }
            }
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
            // 1. Повернення у загальне лобі, очищення та перенесення точки спавну гравців
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.getInventory().clearContent();
                p.removeAllEffects();

                if (state.generalLobby != null) {
                    p.teleportTo(server.overworld(), state.generalLobby.getX() + 0.5,
                            state.generalLobby.getY(), state.generalLobby.getZ() + 0.5, p.getYRot(), p.getXRot());

                    // Переносимо точку спавну гравця в загальне лобі
                    p.setRespawnPosition(server.overworld().dimension(), state.generalLobby, 0.0f, true, false);
                }
            }

            // 2. Скидання точок у нейтральний стан
            for (CapturePoint point : data.points.values()) {
                point.owner = Team.NEUTRAL;
                point.captureProgress = 0;
                point.capturingTeam = Team.NEUTRAL;
            }

            // 3. Скидання стану матчу
            state.phase = MatchState.Phase.LOBBY;
            state.redScore = 0;
            state.blueScore = 0;
            PlayerTeamTracker.clear();
            com.tacticalpvp.mod.TacticalPvpMod.CLASS_LIMIT_MANAGER.reset();
            waitingPlayers.clear();

            data.setDirty();
        }
    }

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

        if (scoringTeam == Team.RED) data.matchState.redScore++;
        else if (scoringTeam == Team.BLUE) data.matchState.blueScore++;
        data.setDirty();
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        ServerLevel level = server.overworld();
        TacticalWorldData data = TacticalWorldData.get(level);

        if (data.matchState.phase == MatchState.Phase.RUNNING) {
            Team team = PlayerTeamTracker.get(player.getUUID());
            BlockPos lobby = team == Team.RED ? data.matchState.redLobby : data.matchState.blueLobby;

            if (lobby != null) {
                player.teleportTo(level, lobby.getX() + 0.5, lobby.getY(), lobby.getZ() + 0.5, player.getYRot(), player.getXRot());
            }

            int respawnTicks = data.matchState.lobbyTimerTicks > 0 ? data.matchState.lobbyTimerTicks : 200;
            waitingPlayers.put(player.getUUID(), respawnTicks);
        }
    }

    private void tickWaitingPlayers(MinecraftServer server, TacticalWorldData data) {
        Iterator<Map.Entry<UUID, Integer>> it = waitingPlayers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> e = it.next();
            int remaining = e.getValue() - 1;
            if (remaining <= 0) {
                ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
                if (p != null && p.isAlive()) {
                    respawnAtFrontline(server, data, p);
                }
                it.remove();
            } else {
                e.setValue(remaining);
            }
        }
    }

    private void respawnAtFrontline(MinecraftServer server, TacticalWorldData data, ServerPlayer player) {
        Team team = PlayerTeamTracker.get(player.getUUID());
        TeamSpawnPoint spawn = SpawnLogic.findActiveFrontlineSpawn(data.points, team);

        if (spawn == null) return;

        var rand = player.getRandom();
        int radius = TeamSpawnPoint.SPAWN_RADIUS;
        double dx = (rand.nextDouble() * 2 - 1) * radius;
        double dz = (rand.nextDouble() * 2 - 1) * radius;

        player.teleportTo(server.overworld(), spawn.pos.getX() + 0.5 + dx, spawn.pos.getY(),
                spawn.pos.getZ() + 0.5 + dz, player.getYRot(), player.getXRot());
    }

    // ================= Зони вибору команди (Кольоровий напис) =================
    private void checkTeamZones(MinecraftServer server, TacticalWorldData data) {
        boolean swappingLocked = data.matchState.phase != MatchState.Phase.LOBBY;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            for (TeamZone zone : data.teamZones) {
                if (!zone.isInside(player.blockPosition())) continue;
                Team current = PlayerTeamTracker.get(player.getUUID());
                if (current == zone.team) continue;
                if (current != Team.NEUTRAL && swappingLocked) continue;

                PlayerTeamTracker.set(player.getUUID(), zone.team);

                // Кольоровий напис команди
                int colorHex = zone.team == Team.RED ? 0xFF5555 : 0x5555FF;
                Component titleComponent = Component.literal(zone.team.fullTitleWord())
                        .withStyle(style -> style.withColor(colorHex).withBold(true));

                player.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(titleComponent));
            }
        }
    }

    // ================= Підсвітка зон вибору команд частками =================
    private void drawTeamZones(ServerLevel level, TacticalWorldData data) {
        if (data.teamZones.isEmpty()) return;

        for (TeamZone zone : data.teamZones) {
            int halfL = zone.length / 2;
            int halfW = zone.width / 2;
            int y = zone.center.getY();
            int minX = zone.center.getX() - halfL, maxX = zone.center.getX() + halfL;
            int minZ = zone.center.getZ() - halfW, maxZ = zone.center.getZ() + halfW;

            // Яскраві кольори частинок пилу (Червоний або Синій)
            Vector3f color = zone.team == Team.RED ? new Vector3f(1.0f, 0.1f, 0.1f) : new Vector3f(0.1f, 0.4f, 1.0f);
            DustParticleOptions particleOptions = new DustParticleOptions(color, 1.2f);

            // Крок 1 для щільної прямокутної рамки
            for (int x = minX; x <= maxX; x++) {
                level.sendParticles(particleOptions, x + 0.5, y + 0.2, minZ + 0.5, 1, 0, 0, 0, 0);
                level.sendParticles(particleOptions, x + 0.5, y + 0.2, maxZ + 0.5, 1, 0, 0, 0, 0);
            }
            for (int z = minZ; z <= maxZ; z++) {
                level.sendParticles(particleOptions, minX + 0.5, y + 0.2, z + 0.5, 1, 0, 0, 0, 0);
                level.sendParticles(particleOptions, maxX + 0.5, y + 0.2, z + 0.5, 1, 0, 0, 0, 0);
            }
        }
    }

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