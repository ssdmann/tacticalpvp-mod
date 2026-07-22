package com.tacticalpvp.mod.points;

import com.tacticalpvp.mod.network.CaptureProgressPacket;
import com.tacticalpvp.mod.network.NetworkHandler;
import com.tacticalpvp.mod.util.PlayerTeamTracker;
import com.tacticalpvp.mod.util.TacticalWorldData;
import com.tacticalpvp.mod.util.Team;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Обробник затримки, підсвітки та прогресу захоплення точок.
 */
public class CaptureTickHandler {

    private static final int PARTICLE_INTERVAL = 5;
    private int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        TacticalWorldData data = TacticalWorldData.get(level);

        tickCounter++;
        boolean drawParticles = tickCounter % PARTICLE_INTERVAL == 0;

        List<CapturePoint> sortedPoints = new ArrayList<>(data.points.values());
        sortedPoints.sort(Comparator.comparingInt(p -> p.index));

        // 1. ЧАСТИНКИ ТОЧОК МАЛЮЮТЬСЯ ЗАВЖДИ (навіть у Лоббі та до старту матчу!)
        for (CapturePoint point : sortedPoints) {
            if (drawParticles) {
                drawBoundary(level, point);
            }
        }

        // 2. Логіка захоплення та синхронізації працює ТІЛЬКИ під час матчу
        if (data.matchState.phase != com.tacticalpvp.mod.match.MatchState.Phase.RUNNING) return;

        int minFrontierIndex = getFrontierIndexForTeam(sortedPoints, Team.RED);
        int maxFrontierIndex = getFrontierIndexForTeam(sortedPoints, Team.BLUE);

        for (CapturePoint point : sortedPoints) {
            List<ServerPlayer> playersInside = level.players().stream()
                    .filter(p -> point.isInside(p.blockPosition()))
                    .toList();

            processCapture(level, data, point, playersInside, minFrontierIndex, maxFrontierIndex);
            syncHud(point, playersInside);
        }
    }

    private void processCapture(ServerLevel level, TacticalWorldData data, CapturePoint point, 
                                List<ServerPlayer> playersInside, int minFrontierIndex, int maxFrontierIndex) {
        
        List<Team> teamsInside = playersInside.stream()
                .map(p -> PlayerTeamTracker.get(p.getUUID()))
                .filter(t -> t != Team.NEUTRAL)
                .distinct()
                .toList();

        if (teamsInside.size() != 1) return;

        Team attacker = teamsInside.get(0);

        if (attacker == point.owner) {
            point.captureProgress = 0;
            point.capturingTeam = Team.NEUTRAL;
            return;
        }

        boolean canCapture = (attacker == Team.RED && point.index == minFrontierIndex) ||
                             (attacker == Team.BLUE && point.index == maxFrontierIndex);

        if (!canCapture) return;

        int captureTimeSec = data.captureTimeSeconds > 0 ? data.captureTimeSeconds : 10;
        point.tickCaptureProgress(attacker, captureTimeSec);

        if (point.captureProgress >= 100.0) {
            completeCapture(level, point, attacker, playersInside);
        }
    }

    private int getFrontierIndexForTeam(List<CapturePoint> points, Team team) {
        if (points.isEmpty()) return -1;

        // Фільтруємо точки відповідної гілки (або нейтрального центру 1_neutral)
        List<CapturePoint> linePoints = points.stream()
                .filter(p -> p.assignedTeam == team || p.assignedTeam == Team.NEUTRAL)
                .sorted(Comparator.comparingInt(p -> p.index))
                .toList();

        if (linePoints.isEmpty()) return -1;

        // Шукаємо найпершу незахоплену точку на шляху
        for (CapturePoint p : linePoints) {
            if (p.owner != team) return p.index;
        }

        // Якщо всі захоплені — беремо крайню
        return linePoints.get(linePoints.size() - 1).index;
    }

    private void completeCapture(ServerLevel level, CapturePoint point, Team newOwner, List<ServerPlayer> playersInside) {
        point.owner = newOwner;
        point.captureProgress = 0;
        point.capturingTeam = Team.NEUTRAL;

        for (ServerPlayer p : playersInside) {
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        level.players().forEach(p -> p.sendSystemMessage(
                net.minecraft.network.chat.Component.translatable("message.tacticalpvp.point_captured",
                        point.index, newOwner.fullTitleWord())));
    }

    private void syncHud(CapturePoint point, List<ServerPlayer> playersInside) {
        int percent = (int) Math.min(100, point.captureProgress);
        boolean active = point.capturingTeam != Team.NEUTRAL && percent > 0;
        for (ServerPlayer p : playersInside) {
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                    new CaptureProgressPacket(active, point.index, percent));
        }
    }

    private void drawBoundary(ServerLevel level, CapturePoint point) {
        int halfL = point.length / 2;
        int halfW = point.width / 2;
        int y = point.center.getY();
        int minX = point.center.getX() - halfL, maxX = point.center.getX() + halfL;
        int minZ = point.center.getZ() - halfW, maxZ = point.center.getZ() + halfW;

        for (int x = minX; x <= maxX; x++) {
            spawnBoundaryParticle(level, point, x, y, minZ);
            spawnBoundaryParticle(level, point, x, y, maxZ);
        }
        for (int z = minZ; z <= maxZ; z++) {
            spawnBoundaryParticle(level, point, minX, y, z);
            spawnBoundaryParticle(level, point, maxX, y, z);
        }
    }

    private void spawnBoundaryParticle(ServerLevel level, CapturePoint point, int x, int y, int z) {
        switch (point.owner) {
            case RED -> level.sendParticles(new DustParticleOptions(new Vector3f(1f, 0.15f, 0.15f), 1.0f),
                    x + 0.5, y + 1.0, z + 0.5, 1, 0, 0, 0, 0);
            case BLUE -> level.sendParticles(new DustParticleOptions(new Vector3f(0.15f, 0.45f, 1f), 1.0f),
                    x + 0.5, y + 1.0, z + 0.5, 1, 0, 0, 0, 0);
            default -> level.sendParticles(new DustParticleOptions(new Vector3f(0.8f, 0.8f, 0.8f), 1.0f),
                    x + 0.5, y + 1.0, z + 0.5, 1, 0, 0, 0, 0);
        }
    }
}