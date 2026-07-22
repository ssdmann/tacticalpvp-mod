package com.tacticalpvp.mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tacticalpvp.mod.match.TeamZone;
import com.tacticalpvp.mod.util.TacticalWorldData;
import com.tacticalpvp.mod.util.Team;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** 
 * /teamzone create <red/blue> <length> <width> — центр = позиція адміна. 
 * /teamzone delete <red/blue> — видаляє зону конкретної команди.
 * /teamzone clearall — видаляє всі зони команд.
 */
public class TeamZoneCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("teamzone")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .then(Commands.argument("length", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("width", IntegerArgumentType.integer(1))
                                                .executes(TeamZoneCommands::create)))))
                .then(Commands.literal("delete")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .executes(TeamZoneCommands::delete)))
                .then(Commands.literal("clearall")
                        .executes(TeamZoneCommands::clearAll)));
    }

    private static int create(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Team team = Team.fromString(StringArgumentType.getString(ctx, "team"));
        int length = IntegerArgumentType.getInteger(ctx, "length");
        int width = IntegerArgumentType.getInteger(ctx, "width");
        BlockPos center = BlockPos.containing(src.getPosition());

        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        
        // Видаляємо стару зону цієї ж команди, якщо вона існувала
        data.teamZones.removeIf(z -> z.team == team);
        
        data.teamZones.add(new TeamZone(team, center, length, width));
        data.setDirty();

        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.teamzone_created", team.name()), true);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Team team = Team.fromString(StringArgumentType.getString(ctx, "team"));

        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        boolean removed = data.teamZones.removeIf(z -> z.team == team);

        if (removed) {
            data.setDirty();
            src.sendSuccess(() -> Component.literal("§eЗону вибору для команди " + team.name() + " успішно видалено!"), true);
            return 1;
        } else {
            src.sendFailure(Component.literal("§cЗону для команди " + team.name() + " не знайдено!"));
            return 0;
        }
    }

    private static int clearAll(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());

        data.teamZones.clear();
        data.setDirty();

        src.sendSuccess(() -> Component.literal("§cУсі зони вибору команд успішно видалено!"), true);
        return 1;
    }
}