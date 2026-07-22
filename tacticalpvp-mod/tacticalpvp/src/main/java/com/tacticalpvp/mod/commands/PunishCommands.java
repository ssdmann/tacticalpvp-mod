package com.tacticalpvp.mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tacticalpvp.mod.punish.PunishEntry;
import com.tacticalpvp.mod.util.TacticalWorldData;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public class PunishCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("punishpoint")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("set").executes(PunishCommands::setPoint)));

        dispatcher.register(Commands.literal("punish")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1))
                                .executes(PunishCommands::punish))));
    }

    private static int setPoint(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        data.punishPoint = BlockPos.containing(src.getPosition());
        data.setDirty();
        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.punishpoint_set"), true);
        return 1;
    }

    private static int punish(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int minutes = IntegerArgumentType.getInteger(ctx, "minutes");

        TacticalWorldData data = TacticalWorldData.get(src.getLevel());
        if (data.punishPoint == null) {
            src.sendFailure(Component.translatable("command.tacticalpvp.punishpoint_not_set"));
            return 0;
        }

        target.getInventory().clearContent();
        target.setGameMode(GameType.ADVENTURE);
        target.teleportTo(src.getLevel(), data.punishPoint.getX() + 0.5, data.punishPoint.getY(),
                data.punishPoint.getZ() + 0.5, target.getYRot(), target.getXRot());

        int ticks = minutes * 60 * 20;
        data.punishEntries.removeIf(e -> e.playerId.equals(target.getUUID()));
        data.punishEntries.add(new PunishEntry(target.getUUID(), ticks));
        data.setDirty();

        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.player_punished", target.getName().getString(), minutes), true);
        return 1;
    }
}
