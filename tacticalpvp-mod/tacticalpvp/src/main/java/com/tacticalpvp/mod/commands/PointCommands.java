package com.tacticalpvp.mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tacticalpvp.mod.points.CapturePoint;
import com.tacticalpvp.mod.points.TeamSpawnPoint;
import com.tacticalpvp.mod.util.TacticalWorldData;
import com.tacticalpvp.mod.util.Team;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

public class PointCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("point")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                .then(Commands.argument("line_team", StringArgumentType.word()) // red/neutral/blue
                                        .then(Commands.argument("length", IntegerArgumentType.integer(1))
                                                .then(Commands.argument("width", IntegerArgumentType.integer(1))
                                                        .executes(ctx -> {
                                                            int number = IntegerArgumentType.getInteger(ctx, "number");
                                                            Team rawTeam = Team.fromString(StringArgumentType.getString(ctx, "line_team"));
                                                            int length = IntegerArgumentType.getInteger(ctx, "length");
                                                            int width = IntegerArgumentType.getInteger(ctx, "width");
                                                            ServerPlayer player = ctx.getSource().getPlayerOrException();

                                                            TacticalWorldData data = TacticalWorldData.get(player.serverLevel());
                                                            
                                                            // Використовуємо окрему final змінну для лінії
                                                            final Team lineTeam = (number == 1) ? Team.NEUTRAL : rawTeam;

                                                            CapturePoint point = new CapturePoint(number, lineTeam, player.blockPosition(), length, width);
                                                            
                                                            // Зберігаємо під ключем "1_neutral", "2_red", "2_blue" тощо
                                                            data.points.put(point.UniqueId(), point);
                                                            data.setDirty();

                                                            String teamLabel = lineTeam == Team.NEUTRAL ? "НЕЙТРАЛЬНА (ЦЕНТР)" : lineTeam.name();
                                                            ctx.getSource().sendSuccess(() -> Component.literal("§aСтворено нейтральну точку №" + number + " [" + teamLabel + "]"), true);
                                                            return 1;
                                                        }))))))
                .then(Commands.literal("spawnpoint")
                        .then(Commands.argument("point_number", IntegerArgumentType.integer(1))
                                .then(Commands.argument("point_line", StringArgumentType.word()) // red/neutral/blue
                                        .then(Commands.argument("spawn_team", StringArgumentType.word()) // red/blue
                                                .executes(ctx -> {
                                                    int number = IntegerArgumentType.getInteger(ctx, "point_number");
                                                    Team rawLine = Team.fromString(StringArgumentType.getString(ctx, "point_line"));
                                                    Team spawnTeam = Team.fromString(StringArgumentType.getString(ctx, "spawn_team"));
                                                    ServerPlayer player = ctx.getSource().getPlayerOrException();

                                                    TacticalWorldData data = TacticalWorldData.get(player.serverLevel());
                                                    
                                                    final Team pointLine = (number == 1) ? Team.NEUTRAL : rawLine;

                                                    String key = number + "_" + pointLine.name().toLowerCase();
                                                    CapturePoint point = data.points.get(key);

                                                    if (point == null) {
                                                        ctx.getSource().sendFailure(Component.literal("§cКонтрольну точку №" + number + " (" + pointLine.name() + ") не знайдено!"));
                                                        return 0;
                                                    }

                                                    TeamSpawnPoint spawn = new TeamSpawnPoint(number, spawnTeam, player.blockPosition());
                                                    if (spawnTeam == Team.RED) point.redSpawn = spawn;
                                                    else if (spawnTeam == Team.BLUE) point.blueSpawn = spawn;

                                                    data.setDirty();
                                                    ctx.getSource().sendSuccess(() -> Component.literal("§aСпавн команди " + spawnTeam.name() + " прив'язано до точки №" + number + " (" + point.assignedTeam.name() + ")"), true);
                                                    return 1;
                                                })))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("number", IntegerArgumentType.integer(1))
                                .then(Commands.argument("line_team", StringArgumentType.word())
                                        .executes(ctx -> {
                                            int number = IntegerArgumentType.getInteger(ctx, "number");
                                            Team rawTeam = Team.fromString(StringArgumentType.getString(ctx, "line_team"));
                                            
                                            // Фіксована final змінна для безпечного виклику в лямбді
                                            final Team lineTeam = (number == 1) ? Team.NEUTRAL : rawTeam;

                                            ServerLevel level = ctx.getSource().getLevel();
                                            TacticalWorldData data = TacticalWorldData.get(level);

                                            String key = number + "_" + lineTeam.name().toLowerCase();
                                            if (data.points.remove(key) != null) {
                                                data.setDirty();
                                                ctx.getSource().sendSuccess(() -> Component.literal("§eТочку №" + number + " (" + lineTeam.name() + ") та всі її спавни видалено!"), true);
                                                return 1;
                                            } else {
                                                ctx.getSource().sendFailure(Component.literal("§cТочку №" + number + " (" + lineTeam.name() + ") не знайдено!"));
                                                return 0;
                                            }
                                        }))))
                .then(Commands.literal("clearall")
                        .executes(ctx -> {
                            ServerLevel level = ctx.getSource().getLevel();
                            TacticalWorldData data = TacticalWorldData.get(level);
                            data.points.clear();
                            data.setDirty();
                            ctx.getSource().sendSuccess(() -> Component.literal("§cУсі точки захоплення та спавни видалено!"), true);
                            return 1;
                        }))
        );
    }
}