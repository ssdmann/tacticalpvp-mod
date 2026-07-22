package com.tacticalpvp.mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tacticalpvp.mod.match.TeamZone;
import com.tacticalpvp.mod.util.Team;
import com.tacticalpvp.mod.util.TacticalWorldData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** /teamzone create <red/blue> <length> <width> — центр = позиція адміна. */
public class TeamZoneCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("teamzone")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("create")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .then(Commands.argument("length", IntegerArgumentType.integer(1))
                                        .then(Commands.argument("width", IntegerArgumentType.integer(1))
                                                .executes(TeamZoneCommands::create))))));
    }

    private static int create(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        Team team = Team.fromString(StringArgumentType.getString(ctx, "team"));
        int length = IntegerArgumentType.getInteger(ctx, "length");
        int width = IntegerArgumentType.getInteger(ctx, "width");
        BlockPos center = BlockPos.containing(src.getPosition());

        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        data.teamZones.add(new TeamZone(team, center, length, width));
        data.setDirty();

        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.teamzone_created", team.name()), true);
        return 1;
    }
}
