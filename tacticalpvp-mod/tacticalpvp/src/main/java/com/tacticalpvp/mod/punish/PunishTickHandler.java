package com.tacticalpvp.mod.punish;

import com.tacticalpvp.mod.util.PlayerTeamTracker;
import com.tacticalpvp.mod.util.TacticalWorldData;
import com.tacticalpvp.mod.util.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Iterator;

public class PunishTickHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        TacticalWorldData data = TacticalWorldData.get(level);

        Iterator<PunishEntry> it = data.punishEntries.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            PunishEntry entry = it.next();
            entry.remainingTicks--;
            changed = true;

            ServerPlayer player = server.getPlayerList().getPlayer(entry.playerId);

            if (entry.remainingTicks <= 0) {
                if (player != null) {
                    releasePlayer(server, data, player);
                }
                it.remove();
            } else if (player != null) {
                // Захист: якщо покараний гравець намагається втекти із в'язниці
                if (data.punishPoint != null && player.distanceToSqr(data.punishPoint.getX(), data.punishPoint.getY(), data.punishPoint.getZ()) > 25.0) {
                    player.teleportTo(level, data.punishPoint.getX() + 0.5, data.punishPoint.getY(), data.punishPoint.getZ() + 0.5, player.getYRot(), player.getXRot());
                }
                player.setGameMode(GameType.ADVENTURE);
            }
        }
        if (changed) data.setDirty();
    }

    private void releasePlayer(MinecraftServer server, TacticalWorldData data, ServerPlayer player) {
        // Повертаємо режим Survival
        player.setGameMode(GameType.SURVIVAL);

        Team team = PlayerTeamTracker.get(player.getUUID());
        BlockPos lobby = team == Team.RED ? data.matchState.redLobby
                : team == Team.BLUE ? data.matchState.blueLobby
                : data.matchState.generalLobby;

        if (lobby != null) {
            player.teleportTo(server.overworld(), lobby.getX() + 0.5, lobby.getY(), lobby.getZ() + 0.5, player.getYRot(), player.getXRot());
        }

        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aТермін вашого покарання вичерпано. Вас повернено у гру!"));
    }
}