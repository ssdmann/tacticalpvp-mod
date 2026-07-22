package com.tacticalpvp.mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.tacticalpvp.mod.TacticalPvpMod;
import com.tacticalpvp.mod.kits.KitDispenser;
import com.tacticalpvp.mod.kits.PlayerClass;
import com.tacticalpvp.mod.util.Team;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** /kit give <player> <blue/red> <class_name> — примусова видача кіта в обхід GUI/лімітів. */
public class KitCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("kit")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("team", StringArgumentType.word())
                                        .then(Commands.argument("class_name", StringArgumentType.word())
                                                .executes(KitCommands::giveKit))))));
    }

    private static int giveKit(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        Team team = Team.fromString(StringArgumentType.getString(ctx, "team"));
        PlayerClass cls;
        try {
            cls = PlayerClass.fromConfigKey(StringArgumentType.getString(ctx, "class_name"));
        } catch (IllegalArgumentException e) {
            src.sendFailure(Component.translatable("command.tacticalpvp.invalid_class"));
            return 0;
        }

        // Адмінська видача - в обхід перевірки ліміту класу (примусова).
        TacticalPvpMod.CLASS_LIMIT_MANAGER.assign(target.getUUID(), team, cls);
        KitDispenser.giveKit(target, team, cls);

        src.sendSuccess(() -> Component.translatable("command.tacticalpvp.kit_given", target.getName().getString(), cls.uaName), true);
        return 1;
    }
}
