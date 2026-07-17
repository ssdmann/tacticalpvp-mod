package com.tacticalpvp.command;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implements /match randomize teams [keyword]. Shuffles all online players and splits
 * them into the vanilla "red"/"blue" scoreboard teams as evenly as possible (difference
 * of at most 1 player). If a keyword argument is supplied, the admin who ran the command
 * is excluded from the pool entirely (useful for spectating admins/streamers).
 */
public final class TeamRandomizer {

    private TeamRandomizer() {}

    public static int run(CommandSourceStack source, String excludeKeyword) {
        MinecraftServer server = source.getServer();
        Scoreboard scoreboard = server.getScoreboard();

        PlayerTeam red = getOrCreateTeam(scoreboard, "red");
        PlayerTeam blue = getOrCreateTeam(scoreboard, "blue");

        List<ServerPlayer> pool = new ArrayList<>(server.getPlayerList().getPlayers());

        if (excludeKeyword != null && !excludeKeyword.isEmpty() && source.getEntity() instanceof ServerPlayer admin) {
            pool.removeIf(p -> p.getUUID().equals(admin.getUUID()));
        }

        // clear any pre-existing team membership so re-randomizing doesn't leave stragglers
        for (ServerPlayer p : pool) {
            scoreboard.removePlayerFromTeam(p.getScoreboardName());
        }

        Collections.shuffle(pool);

        int redCount = 0;
        int blueCount = 0;
        for (int i = 0; i < pool.size(); i++) {
            ServerPlayer player = pool.get(i);
            if (i % 2 == 0) {
                scoreboard.addPlayerToTeam(player.getScoreboardName(), red);
                redCount++;
            } else {
                scoreboard.addPlayerToTeam(player.getScoreboardName(), blue);
                blueCount++;
            }
        }

        int redFinal = redCount;
        int blueFinal = blueCount;
        source.sendSuccess(() -> Component.translatable("command.tacticalpvp.teams_randomized", redFinal, blueFinal), true);
        return pool.size();
    }

    private static PlayerTeam getOrCreateTeam(Scoreboard scoreboard, String name) {
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            team = scoreboard.addPlayerTeam(name);
        }
        return team;
    }
}
