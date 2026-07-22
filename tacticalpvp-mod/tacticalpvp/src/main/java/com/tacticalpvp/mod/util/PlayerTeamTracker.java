package com.tacticalpvp.mod.util;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Проста мапа гравець -> команда. Оновлюється при вході у зону вибору команди
 * (TeamZone), скидається адміном при /match init.
 */
public class PlayerTeamTracker {

    private static final Map<UUID, Team> TEAMS = new ConcurrentHashMap<>();

    public static Team get(UUID playerId) {
        return TEAMS.getOrDefault(playerId, Team.NEUTRAL);
    }

    public static void set(UUID playerId, Team team) {
        if (team == Team.NEUTRAL) {
            TEAMS.remove(playerId);
        } else {
            TEAMS.put(playerId, team);
        }
    }

    public static int countTeam(Team team) {
        return (int) TEAMS.values().stream().filter(t -> t == team).count();
    }

    public static void clear() {
        TEAMS.clear();
    }
}
