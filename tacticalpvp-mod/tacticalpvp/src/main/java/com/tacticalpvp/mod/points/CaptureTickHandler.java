package com.tacticalpvp.mod.points;

import com.tacticalpvp.mod.match.MatchState;
import com.tacticalpvp.mod.network.CaptureProgressPacket;
import com.tacticalpvp.mod.network.NetworkHandler;
import com.tacticalpvp.mod.util.PlayerTeamTracker;
import com.tacticalpvp.mod.util.Team;
import com.tacticalpvp.mod.util.TacticalWorldData;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Раз на тік:
 *  1) Малює візуальний контур межі точки частками відповідно до власника.
 *  2) Перевіряє, які гравці фізично всередині зони, і рахує кого з якої команди.
 *  3) Просуває/скидає прогрес захоплення (тільки одна команда всередині == прогрес йде;
 *     обидві команди всередині == "контест", прогрес заморожено).
 *  4) При завершенні захоплення: міняє власника, оновлює активний спавн, грає
 *     experience.levelup ЛИШЕ гравцям всередині межі, розсилає HUD.
 */
public class CaptureTickHandler {

    // Швидкість захоплення: повний прогрес (100%) приблизно за 10 секунд (200 тіків).
    private static final double PROGRESS_PER_TICK = 100.0 / 200.0;
    private static final int PARTICLE_INTERVAL = 5; // кожні 5 тіків малюємо контур (оптимізація)

    private int tickCounter = 0;

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        TacticalWorldData data = TacticalWorldData.get(level);

        // Логіка захоплення активна лише під час запущеного матчу
        if (data.matchState.phase != MatchState.Phase.RUNNING) return;

        tickCounter++;
        boolean drawParticles = tickCounter % PARTICLE_INTERVAL == 0;

        for (CapturePoint point : data.points.values()) {
            List<ServerPlayer> playersInside = level.players().stream()
                    .filter(p -> point.isInside(p.blockPosition()))
                    .toList();

            if (drawParticles) drawBoundary(level, point);

            processCapture(level, data, point, playersInside);
            syncHud(point, playersInside);
        }
    }

    private void processCapture(ServerLevel level, TacticalWorldData data, CapturePoint point, List<ServerPlayer> playersInside) {
        Set<Team> teamsPresent = new HashSet<>();
        for (ServerPlayer p : playersInside) {
            Team t = PlayerTeamTracker.get(p.getUUID());
            if (t != Team.NEUTRAL) teamsPresent.add(t);
        }

        if (teamsPresent.size() != 1) {
            // Нікого немає, або точку контестують обидві команди одночасно - прогрес заморожено
            return;
        }

        Team attacker = teamsPresent.iterator().next();
        if (attacker == point.owner) {
            // Команда вже володіє точкою - немає що захоплювати
            point.captureProgress = 0;
            point.capturingTeam = Team.NEUTRAL;
            return;
        }

        if (point.capturingTeam != attacker) {
            // Змінилась атакуюча команда - прогрес починається заново
            point.capturingTeam = attacker;
            point.captureProgress = 0;
        }

        point.captureProgress += PROGRESS_PER_TICK;
        if (point.captureProgress >= 100.0) {
            completeCapture(level, point, attacker, playersInside);
        }
    }

    private void completeCapture(ServerLevel level, CapturePoint point, Team newOwner, List<ServerPlayer> playersInside) {
        point.owner = newOwner;
        point.captureProgress = 0;
        point.capturingTeam = Team.NEUTRAL;

        // Звук рівня - ЛИШЕ гравцям, що фізично всередині зони у момент захоплення
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
        var particle = switch (point.owner) {
            case RED -> ParticleTypes.DUST_COLOR_TRANSITION; // заміщується кольоровим dust нижче
            case BLUE -> ParticleTypes.DUST_COLOR_TRANSITION;
            default -> ParticleTypes.SMOKE;
        };
        int halfL = point.length / 2;
        int halfW = point.width / 2;
        int y = point.center.getY();
        int minX = point.center.getX() - halfL, maxX = point.center.getX() + halfL;
        int minZ = point.center.getZ() - halfW, maxZ = point.center.getZ() + halfW;

        for (int x = minX; x <= maxX; x += 2) {
            spawnBoundaryParticle(level, point, x, y, minZ);
            spawnBoundaryParticle(level, point, x, y, maxZ);
        }
        for (int z = minZ; z <= maxZ; z += 2) {
            spawnBoundaryParticle(level, point, minX, y, z);
            spawnBoundaryParticle(level, point, maxX, y, z);
        }
    }

    private void spawnBoundaryParticle(ServerLevel level, CapturePoint point, int x, int y, int z) {
        switch (point.owner) {
            case RED -> level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                            new org.joml.Vector3f(1f, 0.15f, 0.15f), 1.0f),
                    x + 0.5, y + 1.0, z + 0.5, 1, 0, 0, 0, 0);
            case BLUE -> level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                            new org.joml.Vector3f(0.15f, 0.45f, 1f), 1.0f),
                    x + 0.5, y + 1.0, z + 0.5, 1, 0, 0, 0, 0);
            default -> level.sendParticles(new net.minecraft.core.particles.DustParticleOptions(
                            new org.joml.Vector3f(0.6f, 0.6f, 0.6f), 1.0f),
                    x + 0.5, y + 1.0, z + 0.5, 1, 0, 0, 0, 0);
        }
    }
}
