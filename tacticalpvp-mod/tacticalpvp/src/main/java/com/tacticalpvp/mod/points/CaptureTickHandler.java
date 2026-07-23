package com.tacticalpvp.mod.points;

import com.tacticalpvp.mod.network.CaptureProgressPacket;
import com.tacticalpvp.mod.network.NetworkHandler;
import com.tacticalpvp.mod.util.PlayerTeamTracker;
import com.tacticalpvp.mod.util.TacticalWorldData;
import com.tacticalpvp.mod.util.Team;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
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
        // Сортуємо точки у порядку просування фронту
        sortedPoints.sort(Comparator.comparingInt(this::calculateGlobalPointOrder));

        // 1. ЧАСТИНКИ ТОЧОК МАЛЮЮТЬСЯ ЗАВЖДИ (навіть у Лоббі та до старту матчу!)
        for (CapturePoint point : sortedPoints) {
            if (drawParticles) {
                drawBoundary(level, point);
            }
        }

        // 2. Логіка захоплення та синхронізації працює ТІЛЬКИ під час матчу
        if (data.matchState.phase != com.tacticalpvp.mod.match.MatchState.Phase.RUNNING) return;

        // Визначаємо поточну лінію фронту для кожної з команд
        CapturePoint redFrontier = getFrontierPointForTeam(sortedPoints, Team.RED);
        CapturePoint blueFrontier = getFrontierPointForTeam(sortedPoints, Team.BLUE);

        for (CapturePoint point : sortedPoints) {
            List<ServerPlayer> playersInside = level.players().stream()
                    .filter(p -> point.isInside(p.blockPosition()))
                    .toList();

            processCapture(level, data, point, playersInside, redFrontier, blueFrontier);
            syncHud(point, playersInside);
        }
    }

    private void processCapture(ServerLevel level, TacticalWorldData data, CapturePoint point, 
                                List<ServerPlayer> playersInside, CapturePoint redFrontier, CapturePoint blueFrontier) {
        
        List<Team> teamsInside = playersInside.stream()
                .map(p -> PlayerTeamTracker.get(p.getUUID()))
                .filter(t -> t != Team.NEUTRAL)
                .distinct()
                .toList();

        // Якщо на точці нікого немає або знаходяться обидві команди одразу — скидаємо прогрес
        if (teamsInside.size() != 1) {
            if (point.captureProgress > 0) {
                point.captureProgress = Math.max(0, point.captureProgress - 1.0);
                if (point.captureProgress == 0) {
                    point.capturingTeam = Team.NEUTRAL;
                }
            }
            return;
        }

        Team attacker = teamsInside.get(0);

        // Якщо точку займають її поточні власники — прогрес нападників скидається
        if (attacker == point.owner) {
            point.captureProgress = 0;
            point.capturingTeam = Team.NEUTRAL;
            return;
        }

        // ВІПРАВЛЕНО: порівнюємо унікальні ID (наприклад "2_red"), а не просто числові індекси
        boolean canCapture = (attacker == Team.RED && redFrontier != null && redFrontier.id.equals(point.id)) ||
                             (attacker == Team.BLUE && blueFrontier != null && blueFrontier.id.equals(point.id));

        if (!canCapture) return;

        int captureTimeSec = data.captureTimeSeconds > 0 ? data.captureTimeSeconds : 10;
        point.tickCaptureProgress(attacker, captureTimeSec);

        if (point.captureProgress >= 100.0) {
            completeCapture(level, point, attacker, playersInside);
        }
    }

    /**
     * Знаходить найближчу незахоплену точку на шляху команди.
     */
    private CapturePoint getFrontierPointForTeam(List<CapturePoint> orderedPoints, Team team) {
        if (orderedPoints.isEmpty()) return null;

        if (team == Team.RED) {
            // Червоні рухаються від початку списку до кінця
            for (CapturePoint p : orderedPoints) {
                if (p.owner != Team.RED) return p;
            }
        } else if (team == Team.BLUE) {
            // Сині рухаються з кінця списку до початку
            for (int i = orderedPoints.size() - 1; i >= 0; i--) {
                CapturePoint p = orderedPoints.get(i);
                if (p.owner != Team.BLUE) return p;
            }
        }
        return null;
    }

    /**
     * Порядок точок уздовж карти: RED лінії (від більшого до меншого) -> CENTER (1) -> BLUE лінії (від меншого до більшого)
     */
    private int calculateGlobalPointOrder(CapturePoint p) {
        if (p.assignedTeam == Team.RED) return 100 - p.index;
        if (p.assignedTeam == Team.NEUTRAL) return 500;
        if (p.assignedTeam == Team.BLUE) return 1000 + p.index;
        return 0;
    }

    private void completeCapture(ServerLevel level, CapturePoint point, Team newOwner, List<ServerPlayer> playersInside) {
        point.owner = newOwner;
        point.captureProgress = 0;
        point.capturingTeam = Team.NEUTRAL;

        for (ServerPlayer p : playersInside) {
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        level.players().forEach(p -> p.sendSystemMessage(
                Component.translatable("message.tacticalpvp.point_captured",
                        point.index, newOwner.fullTitleWord())));
    }

    private void syncHud(CapturePoint point, List<ServerPlayer> playersInside) {
        int percent = (int) Math.min(100, point.captureProgress);
        boolean active = point.capturingTeam != Team.NEUTRAL && percent > 0;

        for (ServerPlayer p : playersInside) {
            // 1. Пакет мережі для клієнтського рендеру
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                    new CaptureProgressPacket(active, point.index, percent));

            // 2. ДУБЛЮВАННЯ у стандартний ActionBar Minecraft (гарантує показування прогресу)
            if (active) {
                String teamColor = point.capturingTeam == Team.RED ? "§c" : "§9";
                p.displayClientMessage(
                        Component.literal("§fЗахоплення точки №" + point.index + ": " + teamColor + percent + "%"),
                        true // true = вивід над хотбаром
                );
            }
        }
    }

    private void drawBoundary(ServerLevel level, CapturePoint point) {
        int halfL = point.length / 2;
        int halfW = point.width / 2;
        
        // Малюємо рамку ТІЛЬКИ на підлозі точки (Y центру + 0.15 над землею)
        double y = point.center.getY() + 0.15; 

        double minX = point.center.getX() - halfL;
        double maxX = point.center.getX() + halfL + 1.0;
        double minZ = point.center.getZ() - halfW;
        double maxZ = point.center.getZ() + halfW + 1.0;

        double step = 0.5; // Крок 0.5 для гарної виразної лінії

        // Лінії вздовж X (нижня та верхня межі контуру на землі)
        for (double x = minX; x <= maxX; x += step) {
            spawnBoundaryParticle(level, point, x, y, minZ);
            spawnBoundaryParticle(level, point, x, y, maxZ);
        }

        // Лінії вздовж Z (ліва та права межі контуру на землі)
        for (double z = minZ; z <= maxZ; z += step) {
            spawnBoundaryParticle(level, point, minX, y, z);
            spawnBoundaryParticle(level, point, maxX, y, z);
        }
    }  

    private void spawnBoundaryParticle(ServerLevel level, CapturePoint point, double x, double y, double z) {
        Vector3f color = switch (point.owner) {
            case RED -> new Vector3f(1.0f, 0.2f, 0.2f);
            case BLUE -> new Vector3f(0.2f, 0.4f, 1.0f);
            default -> new Vector3f(0.9f, 0.9f, 0.9f);
        };

        level.sendParticles(
                new DustParticleOptions(color, 1.0f),
                x, y, z, 
                1, 0, 0, 0, 0
        );
    }
}