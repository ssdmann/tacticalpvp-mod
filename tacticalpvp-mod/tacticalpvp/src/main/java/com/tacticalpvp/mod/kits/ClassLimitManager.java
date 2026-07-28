package com.tacticalpvp.mod.kits;

import com.tacticalpvp.mod.util.Team;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Відстежує, скільки гравців кожної команди вже обрали кожен клас, і перевіряє
 * чи не вичерпано відсотковий ліміт класу з JSON-конфігу.
 */
public class ClassLimitManager {

    // team -> class -> кількість гравців, що обрали цей клас
    private final Map<Team, Map<PlayerClass, Integer>> counts = new EnumMap<>(Team.class);
    // playerId -> обраний клас (для очищення при виході/зміні команди)
    private final Map<UUID, PlayerClass> playerClass = new HashMap<>();
    private final Map<UUID, Team> playerTeam = new HashMap<>();

    public ClassLimitManager() {
        counts.put(Team.RED, new EnumMap<>(PlayerClass.class));
        counts.put(Team.BLUE, new EnumMap<>(PlayerClass.class));
    }

    /**
     * Перевіряє, чи є вільний слот для класу в команді на основі % з JSON конфігу.
     */
    public boolean hasFreeSlot(Team team, PlayerClass cls, int currentTeamSize) {
        if (team == null || team == Team.NEUTRAL || cls == null) return false;

        // Гарантуємо, що розраховуємо ліміт принаймні для 1 гравця (якщо команда щойно сформована)
        int effectiveTeamSize = Math.max(currentTeamSize, 1);

        // Отримуємо відсоток з JSON конфігу через KitConfigLoader
        double percent = KitConfigLoader.percentLimit(team, cls);
        int limit = (int) Math.ceil((effectiveTeamSize * percent) / 100.0);
        limit = Math.max(limit, 1); // Завжди як мінімум 1 слот

        Map<PlayerClass, Integer> teamCounts = counts.get(team);
        if (teamCounts == null) return true; // Резервний захист

        int used = teamCounts.getOrDefault(cls, 0);
        return used < limit;
    }

    public void assign(UUID playerId, Team team, PlayerClass cls) {
        if (playerId == null || team == null || team == Team.NEUTRAL || cls == null) return;

        release(playerId); // Прибрати попередній вибір, якщо був
        
        Map<PlayerClass, Integer> teamCounts = counts.get(team);
        if (teamCounts != null) {
            teamCounts.merge(cls, 1, Integer::sum);
        }
        
        playerClass.put(playerId, cls);
        playerTeam.put(playerId, team);
    }

    public void release(UUID playerId) {
        if (playerId == null) return;

        PlayerClass prev = playerClass.remove(playerId);
        Team prevTeam = playerTeam.remove(playerId);
        
        if (prev != null && prevTeam != null && counts.containsKey(prevTeam)) {
            Map<PlayerClass, Integer> teamCounts = counts.get(prevTeam);
            if (teamCounts != null) {
                teamCounts.computeIfPresent(prev, (k, v) -> Math.max(0, v - 1));
            }
        }
    }

    public PlayerClass getSelected(UUID playerId) {
        return playerId != null ? playerClass.get(playerId) : null;
    }

    public void reset() {
        for (Map<PlayerClass, Integer> map : counts.values()) {
            if (map != null) map.clear();
        }
        playerClass.clear();
        playerTeam.clear();
    }
}