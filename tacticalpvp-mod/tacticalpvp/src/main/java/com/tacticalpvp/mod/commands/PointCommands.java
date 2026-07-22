package com.tacticalpvp.mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tacticalpvp.mod.points.CapturePoint;
import com.tacticalpvp.mod.points.TeamSpawnPoint;
import com.tacticalpvp.mod.util.Team;
import com.tacticalpvp.mod.util.TacticalWorldData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /point create <index> <neutral/red/blue> <length> <width>
 * /point create spawnpoint <attached_index> <red/blue>
 *
 * Координати виконання команди адміном ЗАВЖДИ є центром зони/спавну.
 */
public class PointCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("point")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.literal("spawnpoint")
                                .then(Commands.argument("attached_index", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("team", StringArgumentType.word())
                                                .executes(PointCommands::createSpawnpoint))))
                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .then(Commands.argument("length", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("width", IntegerArgumentType.integer(1))
                                                        .executes(PointCommands::createPoint)))))));
    }

    private static int createPoint(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int index = IntegerArgumentType.getInteger(ctx, "index");
        Team team = Team.fromString(StringArgumentType.getString(ctx, "team"));
        int length = IntegerArgumentType.getInteger(ctx, "length");
        int width = IntegerArgumentType.getInteger(ctx, "width");

        BlockPos center = BlockPos.containing(src.getPosition());
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        CapturePoint point = new CapturePoint(index, center, length, width, team);
        data.points.put(index, point);
        data.setDirty();

        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.point_created", index, center.getX(), center.getY(), center.getZ()), true);
        return 1;
    }

    private static int createSpawnpoint(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        int attachedIndex = IntegerArgumentType.getInteger(ctx, "attached_index");
        Team team = Team.fromString(StringArgumentType.getString(ctx, "team"));
        BlockPos pos = BlockPos.containing(src.getPosition());

        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        CapturePoint point = data.points.get(attachedIndex);
        if (point == null) {
            src.sendFailure(Component.translatable("command.tacticalpvp.point_not_found", attachedIndex));
            return 0;
        }
        if (team != Team.RED && team != Team.BLUE) {
            src.sendFailure(Component.translatable("command.tacticalpvp.invalid_team"));
            return 0;
        }

        TeamSpawnPoint spawn = new TeamSpawnPoint(attachedIndex, team, pos);
        if (team == Team.RED) point.redSpawn = spawn; else point.blueSpawn = spawn;
        data.setDirty();

        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.spawnpoint_created", attachedIndex, team.name()), true);
        return 1;
    }
}
