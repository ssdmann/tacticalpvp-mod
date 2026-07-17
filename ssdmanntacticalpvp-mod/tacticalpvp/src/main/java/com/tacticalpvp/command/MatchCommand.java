package com.tacticalpvp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tacticalpvp.match.MatchManager;
import com.tacticalpvp.match.MatchSavedData;
import com.tacticalpvp.match.MatchState;
import com.tacticalpvp.match.TeamColor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Registers /match init|start|stop|pause|resume|duration|scorelimit|lobbytimer|setlobby|randomize|purge. */
public final class MatchCommand {

    private MatchCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("match")
                .requires(src -> src.hasPermission(2))
                .then(literal("init").executes(MatchCommand::init))
                .then(literal("start").executes(MatchCommand::start))
                .then(literal("stop").executes(MatchCommand::stop))
                .then(literal("pause").executes(MatchCommand::pause))
                .then(literal("resume").executes(MatchCommand::resume))
                .then(literal("purge").executes(MatchCommand::purge))
                .then(literal("duration")
                        .then(argument("seconds", IntegerArgumentType.integer(0, 10800))
                                .executes(MatchCommand::setDuration)))
                .then(literal("scorelimit")
                        .then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(MatchCommand::setScoreLimit)))
                .then(literal("lobbytimer")
                        .then(argument("seconds", IntegerArgumentType.integer(0, 3600))
                                .executes(MatchCommand::setLobbyTimer)))
                .then(literal("setlobby")
                        .then(literal("red").executes(ctx -> setLobby(ctx, TeamColor.RED)))
                        .then(literal("blue").executes(ctx -> setLobby(ctx, TeamColor.BLUE))))
                .then(literal("randomize")
                        .then(literal("teams")
                                .executes(ctx -> TeamRandomizer.run(ctx.getSource(), null))
                                .then(argument("keyword", StringArgumentType.word())
                                        .executes(ctx -> TeamRandomizer.run(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "keyword"))))))
        );
    }

    /**
     * Initializes/enables the gamemode: ensures the vanilla "red"/"blue" scoreboard
     * teams exist, resets to the IDLE state, and refreshes the HUD to show the
     * currently configured duration as a STATIC value (not counting down) until
     * an admin runs /match start.
     */
    private static int init(CommandContext<CommandSourceStack> ctx) {
        var server = ctx.getSource().getServer();
        Scoreboard scoreboard = server.getScoreboard();
        ensureTeamExists(scoreboard, "red");
        ensureTeamExists(scoreboard, "blue");

        MatchSavedData data = MatchSavedData.get(server);
        data.setState(MatchState.IDLE);
        data.setRemainingTicks(data.isInfiniteTime() ? 0 : data.getConfiguredDurationSeconds() * 20);
        MatchManager.syncHudToAll(server);

        ctx.getSource().sendSuccess(() -> Component.translatable("command.tacticalpvp.initialized"), true);
        return 1;
    }

    private static void ensureTeamExists(Scoreboard scoreboard, String name) {
        PlayerTeam team = scoreboard.getPlayerTeam(name);
        if (team == null) {
            scoreboard.addPlayerTeam(name);
        }
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        MatchManager.startMatch(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("command.tacticalpvp.match_started"), true);
        return 1;
    }

    private static int stop(CommandContext<CommandSourceStack> ctx) {
        MatchManager.stopMatch(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("command.tacticalpvp.match_stopped"), true);
        return 1;
    }

    private static int pause(CommandContext<CommandSourceStack> ctx) {
        boolean ok = MatchManager.pauseMatch(ctx.getSource().getServer());
        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.tacticalpvp.match_paused"), true);
        } else {
            ctx.getSource().sendFailure(Component.translatable("command.tacticalpvp.pause_failed"));
        }
        return ok ? 1 : 0;
    }

    private static int resume(CommandContext<CommandSourceStack> ctx) {
        boolean ok = MatchManager.resumeMatch(ctx.getSource().getServer());
        if (ok) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.tacticalpvp.match_resumed"), true);
        } else {
            ctx.getSource().sendFailure(Component.translatable("command.tacticalpvp.resume_failed"));
        }
        return ok ? 1 : 0;
    }

    private static int purge(CommandContext<CommandSourceStack> ctx) {
        MatchSavedData data = MatchSavedData.get(ctx.getSource().getServer());
        data.purgeAll();
        ctx.getSource().sendSuccess(() -> Component.translatable("command.tacticalpvp.purged"), true);
        return 1;
    }

    private static int setDuration(CommandContext<CommandSourceStack> ctx) {
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        var server = ctx.getSource().getServer();
        MatchSavedData data = MatchSavedData.get(server);
        data.setConfiguredDurationSeconds(seconds);

        // Per spec, the timer must remain static at the newly configured preset
        // value until /match start is run - only refresh the display, never the
        // running countdown, so a mid-match change of this admin setting doesn't
        // unfairly interfere with an in-progress round.
        if (data.getState() != MatchState.RUNNING) {
            data.setRemainingTicks(seconds == 0 ? 0 : seconds * 20);
            MatchManager.syncHudToAll(server);
        }

        if (seconds == 0) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.tacticalpvp.duration_infinite"), true);
        } else {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.tacticalpvp.duration_set", seconds), true);
        }
        return 1;
    }

    private static int setScoreLimit(CommandContext<CommandSourceStack> ctx) {
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        MatchSavedData data = MatchSavedData.get(ctx.getSource().getServer());
        data.setScoreLimit(amount);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.tacticalpvp.scorelimit_set", amount), true);
        return 1;
    }

    private static int setLobbyTimer(CommandContext<CommandSourceStack> ctx) {
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        MatchSavedData data = MatchSavedData.get(ctx.getSource().getServer());
        data.setLobbyTimerSeconds(seconds);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.tacticalpvp.lobbytimer_set", seconds), true);
        return 1;
    }

    private static int setLobby(CommandContext<CommandSourceStack> ctx, TeamColor team) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        BlockPos pos = BlockPos.containing(ctx.getSource().getPosition());
        MatchSavedData data = MatchSavedData.get(ctx.getSource().getServer());
        data.setLobby(team, pos);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.tacticalpvp.lobby_set",
                Component.translatable("message.tacticalpvp.team_" + team.getKey()), pos.toShortString()), true);
        return 1;
    }
}
