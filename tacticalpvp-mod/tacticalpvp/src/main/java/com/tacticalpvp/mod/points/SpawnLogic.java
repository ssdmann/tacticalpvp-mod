package com.tacticalpvp.mod.points;

import com.tacticalpvp.mod.util.Team;

import java.util.Map;

/**
 * Логіка визначення активного спавнпоінта на лінії фронту.
 */
public class SpawnLogic {

    /** 
     * Повертає крайній захоплений спавнпоінт для відповідної команди.
     */
    public static TeamSpawnPoint findActiveFrontlineSpawn(Map<String, CapturePoint> points, Team team) {
        if (points.isEmpty() || team == Team.NEUTRAL) return null;

        TeamSpawnPoint bestSpawn = null;

        if (team == Team.RED) {
            // Для Червоних: шукаємо точку з НАЙБІЛЬШИМ індексом (серед своєї лінії RED або нейтральної центральної),
            // яку захопила Червона команда і яка має створений redSpawn.
            int maxIndex = Integer.MIN_VALUE;
            for (CapturePoint point : points.values()) {
                if (point.owner == Team.RED && point.redSpawn != null) {
                    if (point.assignedTeam == Team.RED || point.assignedTeam == Team.NEUTRAL) {
                        if (point.index > maxIndex) {
                            maxIndex = point.index;
                            bestSpawn = point.redSpawn;
                        }
                    }
                }
            }
        } else if (team == Team.BLUE) {
            // Для Синіх: шукаємо точку з НАЙБІЛЬШИМ індексом на лінії BLUE (або нейтральній центральній 1_neutral),
            // яку захопила Синя команда і яка має створений blueSpawn.
            int maxIndex = Integer.MIN_VALUE;
            for (CapturePoint point : points.values()) {
                if (point.owner == Team.BLUE && point.blueSpawn != null) {
                    if (point.assignedTeam == Team.BLUE || point.assignedTeam == Team.NEUTRAL) {
                        if (point.index > maxIndex) {
                            maxIndex = point.index;
                            bestSpawn = point.blueSpawn;
                        }
                    }
                }
            }
        }

        return bestSpawn;
    }
}