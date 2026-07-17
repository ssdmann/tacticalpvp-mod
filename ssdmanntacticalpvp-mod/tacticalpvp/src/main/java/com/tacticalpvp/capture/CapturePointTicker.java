package com.tacticalpvp.capture;

import com.tacticalpvp.TacticalPvpMod;
import com.tacticalpvp.match.MatchManager;
import com.tacticalpvp.match.MatchSavedData;
import com.tacticalpvp.match.MatchState;
import com.tacticalpvp.match.TeamColor;
import com.tacticalpvp.network.NetworkHandler;
import com.tacticalpvp.network.packet.ActionBarPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Drives per-tick logic for every configured capture point: detects which team(s)
 * are physically standing inside the zone, enforces the linear tug-of-war rule
 * (can only capture the sequential neighbor), freezes progress while contested,
 * renders boundary particles, and fires capture rewards (sound + action bar) to
 * players inside the zone at the moment of completion.
 */
public final class CapturePointTicker {

    private static final DustParticleOptions RED_DUST =
            new DustParticleOptions(new org.joml.Vector3f(1.0f, 0.15f, 0.15f), 1.0f);
    private static final DustParticleOptions BLUE_DUST =
            new DustParticleOptions(new org.joml.Vector3f(0.2f, 0.4f, 1.0f), 1.0f);
    private static final DustParticleOptions GRAY_DUST =
            new DustParticleOptions(new org.joml.Vector3f(0.6f, 0.6f, 0.6f), 1.0f);

    private CapturePointTicker() {}

    private static int particleTickCounter = 0;
    private static final int PARTICLE_INTERVAL_TICKS = 5; // throttle to avoid flooding clients

    public static void tickAllPoints(MinecraftServer server) {
        MatchSavedData data = MatchSavedData.get(server);
        ServerLevel level = server.overworld();

        boolean matchRunning = data.getState() == MatchState.RUNNING;
        particleTickCounter++;
        boolean drawParticlesThisTick = particleTickCounter % PARTICLE_INTERVAL_TICKS == 0;

        for (CapturePoint point : data.getPoints()) {
            if (drawParticlesThisTick) {
                spawnBoundaryParticles(level, point);
            }

            if (!matchRunning) {
                continue; // don't progress captures outside of an active match
            }
            tickCaptureLogic(server, data, point);
        }
    }

    private static void tickCaptureLogic(MinecraftServer server, MatchSavedData data, CapturePoint point) {
        AABB box = point.getBoundingBox();
        List<ServerPlayer> redPlayers = new ArrayList<>();
        List<ServerPlayer> bluePlayers = new ArrayList<>();

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!box.contains(player.getX(), player.getY(), player.getZ())) continue;
            TeamColor team = TacticalPvpMod.getPlayerTeam(player);
            if (team == TeamColor.RED) redPlayers.add(player);
            else if (team == TeamColor.BLUE) bluePlayers.add(player);
        }

        boolean redPresent = !redPlayers.isEmpty();
        boolean bluePresent = !bluePlayers.isEmpty();

        if (redPresent && bluePresent) {
            // Contested: both teams present at once -> freeze progress entirely.
            point.setContested(true);
            point.haltProgress();
            return;
        }
        point.setContested(false);

        TeamColor attacker = redPresent ? TeamColor.RED : (bluePresent ? TeamColor.BLUE : TeamColor.NONE);

        if (attacker == TeamColor.NONE) {
            point.haltProgress();
            return;
        }

        if (attacker == point.getOwner()) {
            // already owned by the attacker, nothing to capture
            return;
        }

        if (!canCapture(data, point, attacker)) {
            // linear rule not satisfied - attacker present but cannot progress the point yet
            return;
        }

        boolean justCaptured = point.tickProgress(attacker);

        List<ServerPlayer> insideAttackers = attacker == TeamColor.RED ? redPlayers : bluePlayers;

        if (justCaptured) {
            onCaptured(server, point, attacker, insideAttackers);
        } else {
            // action bar indicator for attackers physically inside the zone - wording
            // differs depending on whether they're driving the old owner to neutral
            // first, or building up their own control from 0%.
            String pct = String.valueOf(point.getProgressPercent());
            String messageKey = point.getPhase() == CapturePhase.NEUTRALIZING
                    ? "message.tacticalpvp.neutralizing_progress"
                    : "message.tacticalpvp.capturing_progress";
            for (ServerPlayer p : insideAttackers) {
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                        new ActionBarPacket(Component.translatable(messageKey, point.getIndex(), pct)));
            }
        }
    }

    /**
     * Tug-of-war rule: a team can only make progress on a point if it already owns
     * the point immediately adjacent on the chain, back towards its own base. Red
     * pushes towards increasing chain position and needs the next-lower-position
     * neighbor already Red-owned; Blue mirrors this needing the next-higher-position
     * neighbor already Blue-owned. A point with no such neighbor configured is
     * capturable unconditionally (acts as a map edge / starting point).
     */
    private static boolean canCapture(MatchSavedData data, CapturePoint point, TeamColor attacker) {
        int position = point.getChainPosition();
        if (attacker == TeamColor.RED) {
            CapturePoint neighbor = data.getNeighborTowardRed(position);
            if (neighbor == null) return true; // edge point, no prerequisite
            return neighbor.getOwner() == TeamColor.RED;
        } else if (attacker == TeamColor.BLUE) {
            CapturePoint neighbor = data.getNeighborTowardBlue(position);
            if (neighbor == null) return true; // edge point, no prerequisite
            return neighbor.getOwner() == TeamColor.BLUE;
        }
        return false;
    }

    private static void onCaptured(MinecraftServer server, CapturePoint point, TeamColor newOwner,
                                    List<ServerPlayer> playersInside) {
        for (ServerPlayer p : playersInside) {
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p),
                    new ActionBarPacket(Component.translatable(
                            "message.tacticalpvp.point_captured", point.getIndex())
                            .withStyle(newOwner.getFormatting())));
        }
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.tacticalpvp.point_captured_broadcast",
                        point.getIndex(), Component.translatable("message.tacticalpvp.team_" + newOwner.getKey()))
                        .withStyle(newOwner.getFormatting()), false);
    }

    private static void spawnBoundaryParticles(ServerLevel level, CapturePoint point) {
        BlockPos min = point.getMinCorner();
        BlockPos max = point.getMaxCorner();

        DustParticleOptions dust = switch (point.getOwner()) {
            case RED -> RED_DUST;
            case BLUE -> BLUE_DUST;
            default -> GRAY_DUST;
        };

        // Trace the 12 edges of the box at a fixed sample step to outline the region.
        double step = 1.0;
        int x0 = min.getX(), y0 = min.getY(), z0 = min.getZ();
        int x1 = max.getX() + 1, y1 = max.getY() + 1, z1 = max.getZ() + 1;

        traceEdgesXY(level, dust, x0, x1, y0, y1, z0, step);
        traceEdgesXY(level, dust, x0, x1, y0, y1, z1, step);
        traceEdgesZ(level, dust, x0, y0, z0, z1, step);
        traceEdgesZ(level, dust, x1, y0, z0, z1, step);
        traceEdgesZ(level, dust, x0, y1, z0, z1, step);
        traceEdgesZ(level, dust, x1, y1, z0, z1, step);
    }

    private static void traceEdgesXY(ServerLevel level, DustParticleOptions dust,
                                      double x0, double x1, double y0, double y1, double z, double step) {
        for (double x = x0; x <= x1; x += step) {
            level.sendParticles(dust, x, y0, z, 1, 0, 0, 0, 0);
            level.sendParticles(dust, x, y1, z, 1, 0, 0, 0, 0);
        }
    }

    private static void traceEdgesZ(ServerLevel level, DustParticleOptions dust,
                                     double x, double y0, double z0, double z1, double step) {
        for (double z = z0; z <= z1; z += step) {
            level.sendParticles(dust, x, y0, z, 1, 0, 0, 0, 0);
        }
    }
}
