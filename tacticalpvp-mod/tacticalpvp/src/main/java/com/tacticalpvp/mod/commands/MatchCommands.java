package com.tacticalpvp.mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tacticalpvp.mod.TacticalPvpMod;
import com.tacticalpvp.mod.match.MatchState;
import com.tacticalpvp.mod.points.CapturePoint;
import com.tacticalpvp.mod.util.PlayerTeamTracker;
import com.tacticalpvp.mod.util.ScoreboardTeamManager;
import com.tacticalpvp.mod.util.TacticalWorldData;
import com.tacticalpvp.mod.util.Team;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MatchCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("match")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("init").executes(MatchCommands::init))
                .then(Commands.literal("resetall").executes(MatchCommands::resetAll))
                .then(Commands.literal("teleporteams").executes(MatchCommands::teleportTeams))
                .then(Commands.literal("prestart").executes(MatchCommands::prestart))
                .then(Commands.literal("prestarttimer")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 20))
                                .executes(MatchCommands::setPrestartTimer)))
                .then(Commands.literal("start").executes(MatchCommands::start))
                .then(Commands.literal("stop").executes(MatchCommands::stop))
                .then(Commands.literal("pause").executes(MatchCommands::pause))
                .then(Commands.literal("resume").executes(MatchCommands::resume))
                .then(Commands.literal("duration")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                .executes(MatchCommands::setDuration)))
                .then(Commands.literal("scorelimit")
                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                .executes(MatchCommands::setScoreLimit)))
                .then(Commands.literal("setgenerallobby").executes(MatchCommands::setGeneralLobby))
                .then(Commands.literal("setlobby")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .executes(MatchCommands::setTeamLobby)))
                .then(Commands.literal("lobbytimer")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                .executes(MatchCommands::setLobbyTimer)))
                .then(Commands.literal("capturetime")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 300))
                                .executes(MatchCommands::setCaptureTime))));
    }

    private static int prestart(CommandContext<CommandSourceStack> ctx) {
        return teleportTeams(ctx);
    }

    private static int init(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        data.matchState = new MatchState();
        src.getServer().getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_KEEPINVENTORY).set(true, src.getServer());
        PlayerTeamTracker.clear();
        ScoreboardTeamManager.resetAll(src.getServer());
        TacticalPvpMod.CLASS_LIMIT_MANAGER.reset();
        data.setDirty();
        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.match_initialized"), true);
        return 1;
    }

    private static int resetAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());

        data.points.clear();
        data.teamZones.clear();
        data.punishEntries.clear();
        data.punishPoint = null;
        data.matchState = new MatchState();

        PlayerTeamTracker.clear();
        ScoreboardTeamManager.resetAll(src.getServer());
        TacticalPvpMod.CLASS_LIMIT_MANAGER.reset();
        data.setDirty();

        src.sendSuccess(() -> Component.literal("Усі дані мода повністю очищено!"), true);
        return 1;
    }

    private static int teleportTeams(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        MatchState state = data.matchState;

        if (state.redLobby == null || state.blueLobby == null) {
            src.sendFailure(Component.translatable("command.tacticalpvp.lobbies_not_set"));
            return 0;
        }

        TacticalPvpMod.CLASS_LIMIT_MANAGER.reset();

        int count = 0;
        for (ServerPlayer p : src.getServer().getPlayerList().getPlayers()) {
            Team team = PlayerTeamTracker.get(p.getUUID());
            BlockPos target = team == Team.RED ? state.redLobby : team == Team.BLUE ? state.blueLobby : null;
            if (target == null) continue;

            p.teleportTo(src.getLevel(), target.getX() + 0.5, target.getY(), target.getZ() + 0.5, p.getYRot(), p.getXRot());
            p.getInventory().clearContent();
            p.getInventory().add(new ItemStack(Items.ENDER_EYE));
            count++;
        }

        if (count == 0) {
            src.sendFailure(Component.literal("§cЖоден гравець ще не обрав команду!"));
            return 0;
        }

        // ПРИХОВУЄМО ВСІ НІКИ ПРИ ПРЕСТАРТІ
        ScoreboardTeamManager.setNametagsVisible(src.getServer(), false);

        state.phase = MatchState.Phase.PRESTART;
        state.prestartRemainingTicks = state.prestartTimerTicks > 0 ? state.prestartTimerTicks : 60 * 20;
        data.setDirty();

        src.sendSuccess(() -> Component.literal("§aФазу підготовки запущено! Ніки приховано, гравців телепортовано у лобі."), true);
        return 1;
    }

    private static int start(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        data.matchState.phase = MatchState.Phase.RUNNING;
        data.matchState.matchRemainingTicks = data.matchState.matchDurationTicks;
        data.setDirty();
        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.match_force_started"), true);
        return 1;
    }

    // ================= ЗУПИНКА МАТЧУ З ПОВНИМ СКИДАННЯМ =================
    private static int stop(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        TacticalWorldData data = TacticalWorldData.get(level);
        MatchState state = data.matchState;

        // 1. Повертаємо фазу в LOBBY
        state.phase = MatchState.Phase.LOBBY;
        state.redScore = 0;
        state.blueScore = 0;
        state.matchRemainingTicks = 0;
        state.prestartRemainingTicks = 0;

        // 2. Скидаємо класи, команди ТА ПОВЕРТАЄМО ВИДИМІСТЬ НІКІВ
        TacticalPvpMod.CLASS_LIMIT_MANAGER.reset();
        PlayerTeamTracker.clear();
        ScoreboardTeamManager.resetAll(src.getServer());

        // 3. Повертаємо всіх у загальне лобі та очищаємо інвентар
        for (ServerPlayer player : src.getServer().getPlayerList().getPlayers()) {
            player.getInventory().clearContent();
            player.removeAllEffects();

            if (state.generalLobby != null) {
                player.teleportTo(level, state.generalLobby.getX() + 0.5,
                        state.generalLobby.getY(), state.generalLobby.getZ() + 0.5,
                        player.getYRot(), player.getXRot());

                player.setRespawnPosition(level.dimension(), state.generalLobby, 0.0f, true, false);
            }
        }

        // 4. Скидаємо контрольні точки
        for (CapturePoint point : data.points.values()) {
            point.owner = Team.NEUTRAL;
            point.captureProgress = 0;
            point.capturingTeam = Team.NEUTRAL;
        }

        data.setDirty();
        src.sendSuccess(() -> Component.literal("§cМатч зупинено! Ніки відновлено, гравці повернуті в загальне лобі."), true);
        return 1;
    }

    private static int pause(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        if (data.matchState.phase == MatchState.Phase.RUNNING) {
            data.matchState.phase = MatchState.Phase.PAUSED;
            data.setDirty();
        }
        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.match_paused"), true);
        return 1;
    }

    private static int resume(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        if (data.matchState.phase == MatchState.Phase.PAUSED) {
            data.matchState.phase = MatchState.Phase.RUNNING;
            data.setDirty();
        }
        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.match_resumed"), true);
        return 1;
    }

    private static int setPrestartTimer(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        data.matchState.prestartTimerTicks = minutes * 60 * 20;
        data.setDirty();
        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.prestart_timer_set", minutes), true);
        return 1;
    }

    private static int setDuration(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        data.matchState.matchDurationTicks = minutes * 60 * 20;
        data.setDirty();
        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.duration_set", minutes), true);
        return 1;
    }

    private static int setScoreLimit(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int number = IntegerArgumentType.getInteger(ctx, "number");
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        data.matchState.scoreLimit = number;
        data.setDirty();
        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.scorelimit_set", number), true);
        return 1;
    }

    private static int setGeneralLobby(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        data.matchState.generalLobby = BlockPos.containing(src.getPosition());
        data.setDirty();
        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.general_lobby_set"), true);
        return 1;
    }

    private static int setTeamLobby(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Team team = Team.fromString(StringArgumentType.getString(ctx, "team"));
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        BlockPos pos = BlockPos.containing(src.getPosition());
        if (team == Team.RED) data.matchState.redLobby = pos;
        else if (team == Team.BLUE) data.matchState.blueLobby = pos;
        data.setDirty();
        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.team_lobby_set", team.name()), true);
        return 1;
    }

    private static int setLobbyTimer(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        data.matchState.lobbyTimerTicks = seconds * 20;
        data.setDirty();
        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.lobbytimer_set", seconds), true);
        return 1;
    }

    private static int setCaptureTime(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int seconds = IntegerArgumentType.getInteger(ctx, "seconds");
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        data.captureTimeSeconds = seconds;
        data.setDirty();
        src.sendSuccess(() -> Component.literal("Час захоплення точок встановлено на: " + seconds + " сек."), true);
        return 1;
    }
}