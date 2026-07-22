package com.tacticalpvp.mod.punish;

import com.tacticalpvp.mod.util.PlayerTeamTracker;
import com.tacticalpvp.mod.util.Team;
import com.tacticalpvp.mod.util.TacticalWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.Iterator;

/**
 * Щотіку зменшує remainingTicks для кожного PunishEntry. Список зберігається
 * у World Save Data, тому таймери переживають рестарт сервера (п.4 специфікації).
 * Після завершення: Survival Mode + телепорт у лобі команди гравця.
 */
public class PunishTickHandler {

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        ServerLevel level = server.overworld();
        TacticalWorldData data = TacticalWorldData.get(level);

        Iterator<PunishEntry> it = data.punishEntries.iterator();
        boolean changed = false;
        while (it.hasNext()) {
            PunishEntry entry = it.next();
            entry.remainingTicks--;
            changed = true;
            if (entry.remainingTicks <= 0) {
                releasePlayer(server, data, entry.playerId);
                it.remove();
            }
        }
        if (changed) data.setDirty();
    }

    private void releasePlayer(net.minecraft.server.MinecraftServer server, TacticalWorldData data, java.util.UUID playerId) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player == null) return; // гравець офлайн - таймер вже спливав збережений; при вході окремо не довідродиться автоматично,
                                     // тому рекомендовано перевіряти punishEntries також при вході гравця (PlayerLoggedInEvent).

        player.setGameMode(GameType.SURVIVAL);
        Team team = PlayerTeamTracker.get(playerId);
        BlockPos lobby = team == Team.RED ? data.matchState.redLobby
                : team == Team.BLUE ? data.matchState.blueLobby
                : data.matchState.generalLobby;
        if (lobby != null) {
            player.teleportTo(server.overworld(), lobby.getX() + 0.5, lobby.getY(), lobby.getZ() + 0.5,
                    player.getYRot(), player.getXRot());
        }
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.tacticalpvp.punish_released"));
    }
}
