package com.tacticalpvp.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.tacticalpvp.capture.CapturePoint;
import com.tacticalpvp.match.MatchSavedData;
import com.tacticalpvp.match.TeamColor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Registers:
 *   /point create <index> <red|blue|neutral> <capture_time_sec> <length> <width> <height_y>
 *   /point delete <index> <red|blue|neutral>
 *
 * Points are identified by the (designation, index) pair, not index alone, since the
 * mirrored layout can have a "Red 5" and a "Blue 5" point coexisting on either side of
 * the shared neutral center. Index 1 is reserved for the single shared/neutral center
 * point; indices 2-30 must be designated red or blue (one of each side, forming the
 * chain: Red30 ... Red2, Neutral1, Blue2 ... Blue30).
 *
 * The executing admin's current position is always the CENTER of the capture zone
 * (horizontally). "length" maps to the East-West (X) size, "width" maps to the
 * North-South (Z) size, and "height_y" extends straight up from the admin's Y position.
 * Odd length/width values are nudged one block North (-Z) to resolve the halving
 * imbalance, per spec.
 */
public final class PointCommand {

    private static final SimpleCommandExceptionType ERROR_INDEX1_MUST_BE_NEUTRAL =
            new SimpleCommandExceptionType(Component.translatable("command.tacticalpvp.error_index1_neutral"));
    private static final SimpleCommandExceptionType ERROR_NEUTRAL_REQUIRES_INDEX1 =
            new SimpleCommandExceptionType(Component.translatable("command.tacticalpvp.error_neutral_requires_index1"));

    private PointCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("point")
                .requires(src -> src.hasPermission(2))
                .then(literal("create")
                        .then(argument("index", IntegerArgumentType.integer(1, 30))
                                .then(literal("red")
                                        .then(argument("capture_time_sec", IntegerArgumentType.integer(1, 3600))
                                                .then(argument("length", IntegerArgumentType.integer(1, 30))
                                                        .then(argument("width", IntegerArgumentType.integer(1, 30))
                                                                .then(argument("height_y", IntegerArgumentType.integer(1, 200))
                                                                        .executes(ctx -> create(ctx, TeamColor.RED)))))))
                                .then(literal("blue")
                                        .then(argument("capture_time_sec", IntegerArgumentType.integer(1, 3600))
                                                .then(argument("length", IntegerArgumentType.integer(1, 30))
                                                        .then(argument("width", IntegerArgumentType.integer(1, 30))
                                                                .then(argument("height_y", IntegerArgumentType.integer(1, 200))
                                                                        .executes(ctx -> create(ctx, TeamColor.BLUE)))))))
                                .then(literal("neutral")
                                        .then(argument("capture_time_sec", IntegerArgumentType.integer(1, 3600))
                                                .then(argument("length", IntegerArgumentType.integer(1, 30))
                                                        .then(argument("width", IntegerArgumentType.integer(1, 30))
                                                                .then(argument("height_y", IntegerArgumentType.integer(1, 200))
                                                                        .executes(ctx -> create(ctx, TeamColor.NONE)))))))))
                .then(literal("delete")
                        .then(argument("index", IntegerArgumentType.integer(1, 30))
                                .then(literal("red").executes(ctx -> delete(ctx, TeamColor.RED)))
                                .then(literal("blue").executes(ctx -> delete(ctx, TeamColor.BLUE)))
                                .then(literal("neutral").executes(ctx -> delete(ctx, TeamColor.NONE)))))
        );
    }

    private static int create(CommandContext<CommandSourceStack> ctx, TeamColor designation) throws CommandSyntaxException {
        int index = IntegerArgumentType.getInteger(ctx, "index");
        validateIndexDesignation(index, designation);

        int captureTime = IntegerArgumentType.getInteger(ctx, "capture_time_sec");
        int length = IntegerArgumentType.getInteger(ctx, "length");
        int width = IntegerArgumentType.getInteger(ctx, "width");
        int heightY = IntegerArgumentType.getInteger(ctx, "height_y");

        BlockPos center = BlockPos.containing(ctx.getSource().getPosition());

        // Length (X, East-West): symmetric split around the center block, no bias.
        int[] xRange = symmetricRange(center.getX(), length);
        // Width (Z, North-South): odd widths bias the whole zone one block North.
        int[] zRange = northBiasedRange(center.getZ(), width);

        int minY = center.getY();
        int maxY = center.getY() + heightY - 1;

        BlockPos corner1 = new BlockPos(xRange[0], minY, zRange[0]);
        BlockPos corner2 = new BlockPos(xRange[1], maxY, zRange[1]);

        CapturePoint point = new CapturePoint(index, designation, captureTime, corner1, corner2);

        MatchSavedData data = MatchSavedData.get(ctx.getSource().getServer());
        data.addPoint(point);

        ctx.getSource().sendSuccess(() -> Component.translatable("command.tacticalpvp.point_created",
                designationLabel(designation), index, corner1.toShortString(), corner2.toShortString()), true);
        return 1;
    }

    private static int delete(CommandContext<CommandSourceStack> ctx, TeamColor designation) {
        int index = IntegerArgumentType.getInteger(ctx, "index");
        MatchSavedData data = MatchSavedData.get(ctx.getSource().getServer());
        boolean removed = data.removePoint(designation, index);

        if (removed) {
            ctx.getSource().sendSuccess(() -> Component.translatable("command.tacticalpvp.point_deleted",
                    designationLabel(designation), index), true);
            return 1;
        } else {
            ctx.getSource().sendFailure(Component.translatable("command.tacticalpvp.point_delete_not_found",
                    designationLabel(designation), index));
            return 0;
        }
    }

    /** Index 1 is reserved for the single shared/neutral center; 2-30 must be a team side. */
    private static void validateIndexDesignation(int index, TeamColor designation) throws CommandSyntaxException {
        if (index == 1 && designation != TeamColor.NONE) {
            throw ERROR_INDEX1_MUST_BE_NEUTRAL.create();
        }
        if (index != 1 && designation == TeamColor.NONE) {
            throw ERROR_NEUTRAL_REQUIRES_INDEX1.create();
        }
    }

    private static Component designationLabel(TeamColor designation) {
        return Component.translatable("message.tacticalpvp.team_" + designation.getKey());
    }

    /** Even size: two equal halves. Odd size: perfectly centered on the center block (no bias). */
    private static int[] symmetricRange(int center, int size) {
        int half = size / 2;
        if (size % 2 == 0) {
            return new int[]{center - half, center + half - 1};
        }
        return new int[]{center - half, center + half};
    }

    /** Even size: two equal halves, same as symmetricRange. Odd size: shifted one block further North (-Z). */
    private static int[] northBiasedRange(int center, int size) {
        int half = size / 2;
        if (size % 2 == 0) {
            return new int[]{center - half, center + half - 1};
        }
        // Odd size cannot divide cleanly in half; bias the extra block to the North
        // by shifting the naive symmetric range (center-half .. center+half) back by one.
        return new int[]{center - half - 1, center + half - 1};
    }
}
