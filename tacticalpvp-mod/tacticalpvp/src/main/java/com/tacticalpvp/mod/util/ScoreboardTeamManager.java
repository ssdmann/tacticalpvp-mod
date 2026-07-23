package com.tacticalpvp.mod.util;

import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team.Visibility;

public class ScoreboardTeamManager {

    private static final String RED_TEAM_NAME = "tactical_red";
    private static final String BLUE_TEAM_NAME = "tactical_blue";

    /**
     * Оновлює колір ніку гравця відповідно до його команди у моді.
     */
    public static void updatePlayerNameTag(MinecraftServer server, ServerPlayer player, com.tacticalpvp.mod.util.Team team) {
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam redTeam = getOrCreateTeam(scoreboard, RED_TEAM_NAME, "Червоні", ChatFormatting.RED);
        PlayerTeam blueTeam = getOrCreateTeam(scoreboard, BLUE_TEAM_NAME, "Сині", ChatFormatting.BLUE);

        String playerName = player.getScoreboardName();

        // БЕЗПЕЧНЕ ВИДАЛЕННЯ: видаляємо ТІЛЬКИ якщо гравець дійсно є в цій команді
        safelyRemovePlayerFromTeam(scoreboard, playerName, redTeam);
        safelyRemovePlayerFromTeam(scoreboard, playerName, blueTeam);

        if (team == com.tacticalpvp.mod.util.Team.RED) {
            scoreboard.addPlayerToTeam(playerName, redTeam);
        } else if (team == com.tacticalpvp.mod.util.Team.BLUE) {
            scoreboard.addPlayerToTeam(playerName, blueTeam);
        }
    }

    /**
     * Перемикає видимість ніків над головами (ALWAYS для Лобі, NEVER для Матчу).
     */
    public static void setNametagsVisible(MinecraftServer server, boolean visible) {
        Scoreboard scoreboard = server.getScoreboard();
        Visibility visibility = visible ? Visibility.ALWAYS : Visibility.NEVER;

        PlayerTeam redTeam = getOrCreateTeam(scoreboard, RED_TEAM_NAME, "Червоні", ChatFormatting.RED);
        PlayerTeam blueTeam = getOrCreateTeam(scoreboard, BLUE_TEAM_NAME, "Сині", ChatFormatting.BLUE);

        redTeam.setNameTagVisibility(visibility);
        blueTeam.setNameTagVisibility(visibility);
    }

    /**
     * Очищає всі команди та повертає стандартні ніки.
     */
    public static void resetAll(MinecraftServer server) {
        Scoreboard scoreboard = server.getScoreboard();
        PlayerTeam redTeam = scoreboard.getPlayerTeam(RED_TEAM_NAME);
        PlayerTeam blueTeam = scoreboard.getPlayerTeam(BLUE_TEAM_NAME);

        if (redTeam != null) scoreboard.removePlayerTeam(redTeam);
        if (blueTeam != null) scoreboard.removePlayerTeam(blueTeam);
    }

    private static void safelyRemovePlayerFromTeam(Scoreboard scoreboard, String playerName, PlayerTeam team) {
        if (team != null && team.getPlayers().contains(playerName)) {
            scoreboard.removePlayerFromTeam(playerName, team);
        }
    }

    private static PlayerTeam getOrCreateTeam(Scoreboard scoreboard, String id, String displayName, ChatFormatting color) {
        PlayerTeam team = scoreboard.getPlayerTeam(id);
        if (team == null) {
            team = scoreboard.addPlayerTeam(id);
            team.setDisplayName(net.minecraft.network.chat.Component.literal(displayName));
            team.setColor(color);
            team.setNameTagVisibility(Visibility.ALWAYS);
        }
        return team;
    }
}