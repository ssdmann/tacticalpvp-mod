package com.tacticalpvp;

import com.mojang.logging.LogUtils;
import com.tacticalpvp.command.MatchCommand;
import com.tacticalpvp.command.PointCommand;
import com.tacticalpvp.match.TeamColor;
import com.tacticalpvp.network.NetworkHandler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Team Deathmatch / Linear Sector Control.
 *
 * Uses vanilla Minecraft scoreboard teams named "red" and "blue" (created automatically
 * by the server's /team command or by admins ahead of time) as the source of truth for
 * team membership. This mod purely adds the game-mode logic layered on top: scoring,
 * linear tug-of-war capture points, waiting lobbies, HUD, and persistence.
 */
@Mod(TacticalPvpMod.MOD_ID)
public class TacticalPvpMod {

    public static final String MOD_ID = "tacticalpvp";
    private static final Logger LOGGER = LogUtils.getLogger();

    public TacticalPvpMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        // MatchEventHandler / HudOverlay / ClientSetup self-register via @Mod.EventBusSubscriber
    }

    private void commonSetup(final net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        NetworkHandler.register();
        LOGGER.info("Tactical PvP: Linear Sector Control initialized.");
    }

    @net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = MOD_ID, bus = net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus.FORGE)
    public static class CommandRegistrar {
        @net.minecraftforge.eventbus.api.SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            MatchCommand.register(event.getDispatcher());
            PointCommand.register(event.getDispatcher());
        }
    }

    /**
     * Resolves a player's team from vanilla's scoreboard team system. Expects teams
     * literally named "red" and "blue" (case-insensitive) to exist on the server
     * scoreboard - run "/match init" once to create them automatically, then assign
     * players with "/match randomize teams" or manually via "/team join red <player>".
     */
    public static TeamColor getPlayerTeam(ServerPlayer player) {
        PlayerTeam team = player.getTeam();
        if (team == null) return TeamColor.NONE;
        return TeamColor.fromString(team.getName());
    }
}
