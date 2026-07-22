package com.tacticalpvp.mod.match;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Стан матчу: фаза, таймери, рахунок, лобі-координати.
 * Усі тривалості зберігаються всередині у ігрових тіках, але налаштовуються
 * адміном у реальних хвилинах/секундах (конвертація відбувається у командах).
 */
public class MatchState {

    public enum Phase {
        LOBBY,            // очікування у загальному лобі, вибір команд відкритий
        PRESTART,         // йде відлік після /match teleporteams, вибір команд заблоковано
        RUNNING,          // матч триває, рахунок і таймер активні
        PAUSED,           // матч на паузі
        POSTMATCH         // показ перемоги + відлік повернення у загальне лобі
    }

    public Phase phase = Phase.LOBBY;

    public BlockPos generalLobby;
    public BlockPos redLobby;
    public BlockPos blueLobby;

    // Налаштування у тіках (внутрішньо), хоча команди приймають хвилини
    public int prestartTimerTicks = 60 * 20 * 5; // за замовч. 5 хв
    public int prestartRemainingTicks = 0;

    public int matchDurationTicks = 20 * 60 * 20; // за замовч. 20 хв
    public int matchRemainingTicks = 0;

    public int scoreLimit = 50;
    public int redScore = 0;
    public int blueScore = 0;

    public int lobbyTimerTicks = 20 * 5; // за замовч. 5 сек затримки очікування після смерті

    public int postMatchCountdownTicks = 20 * 15; // 15 сек за замовч. на повернення у лобі
    public int postMatchRemainingTicks = 0;

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString("phase", phase.name());
        if (generalLobby != null) tag.put("generalLobby", posTag(generalLobby));
        if (redLobby != null) tag.put("redLobby", posTag(redLobby));
        if (blueLobby != null) tag.put("blueLobby", posTag(blueLobby));
        tag.putInt("prestartTimerTicks", prestartTimerTicks);
        tag.putInt("prestartRemainingTicks", prestartRemainingTicks);
        tag.putInt("matchDurationTicks", matchDurationTicks);
        tag.putInt("matchRemainingTicks", matchRemainingTicks);
        tag.putInt("scoreLimit", scoreLimit);
        tag.putInt("redScore", redScore);
        tag.putInt("blueScore", blueScore);
        tag.putInt("lobbyTimerTicks", lobbyTimerTicks);
        tag.putInt("postMatchCountdownTicks", postMatchCountdownTicks);
        tag.putInt("postMatchRemainingTicks", postMatchRemainingTicks);
        return tag;
    }

    public static MatchState deserialize(CompoundTag tag) {
        MatchState s = new MatchState();
        s.phase = Phase.valueOf(tag.getString("phase"));
        if (tag.contains("generalLobby")) s.generalLobby = posFromTag(tag.getCompound("generalLobby"));
        if (tag.contains("redLobby")) s.redLobby = posFromTag(tag.getCompound("redLobby"));
        if (tag.contains("blueLobby")) s.blueLobby = posFromTag(tag.getCompound("blueLobby"));
        s.prestartTimerTicks = tag.getInt("prestartTimerTicks");
        s.prestartRemainingTicks = tag.getInt("prestartRemainingTicks");
        s.matchDurationTicks = tag.getInt("matchDurationTicks");
        s.matchRemainingTicks = tag.getInt("matchRemainingTicks");
        s.scoreLimit = tag.getInt("scoreLimit");
        s.redScore = tag.getInt("redScore");
        s.blueScore = tag.getInt("blueScore");
        s.lobbyTimerTicks = tag.getInt("lobbyTimerTicks");
        s.postMatchCountdownTicks = tag.getInt("postMatchCountdownTicks");
        s.postMatchRemainingTicks = tag.getInt("postMatchRemainingTicks");
        return s;
    }

    private static CompoundTag posTag(BlockPos p) {
        CompoundTag t = new CompoundTag();
        t.putInt("x", p.getX()); t.putInt("y", p.getY()); t.putInt("z", p.getZ());
        return t;
    }
    private static BlockPos posFromTag(CompoundTag t) {
        return new BlockPos(t.getInt("x"), t.getInt("y"), t.getInt("z"));
    }
}
