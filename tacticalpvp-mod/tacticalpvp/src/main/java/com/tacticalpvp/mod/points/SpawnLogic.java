package com.tacticalpvp.mod.points;

import com.tacticalpvp.mod.util.Team;

import java.util.Map;

/**
 * Логіка визначення активного спавнпоінта на лінії фронту.
 */
public class SpawnLogic {

    /** 
     * Повертає крайній захоплений спавнпоінт для відповідної команди.
     * Дозволяє спавнитися на власних точках, нейтральному центрі та захоплених ворожих точках.
     */
    public static TeamSpawnPoint findActiveFrontlineSpawn(Map<String, CapturePoint> points, Team team) {
        if (points.isEmpty() || team == Team.NEUTRAL) return null;

        TeamSpawnPoint bestSpawn = null;
        int maxScore = Integer.MIN_VALUE;

        for (CapturePoint point : points.values()) {
            if (team == Team.RED && point.owner == Team.RED && point.redSpawn != null) {
                int score = calculateScoreForRed(point);
                if (score > maxScore) {
                    maxScore = score;
                    bestSpawn = point.redSpawn;
                }
            } else if (team == Team.BLUE && point.owner == Team.BLUE && point.blueSpawn != null) {
                int score = calculateScoreForBlue(point);
                if (score > maxScore) {
                    maxScore = score;
                    bestSpawn = point.blueSpawn;
                }
            }
        }

        return bestSpawn;
    }

    /**
     * Оцінка просування для Червоних:
     * Власна лінія (RED) -> Центр (1_NEUTRAL) -> Ворожа лінія (BLUE)
     */
    private static int calculateScoreForRed(CapturePoint point) {
        if (point.assignedTeam == Team.RED) {
            // Чим менший індекс на своїй лінії, тим далі точка просунута до центру (напр. index 2 ближче до центру ніж 3)
            return 100 - point.index;
        } else if (point.assignedTeam == Team.NEUTRAL) {
            // Центральна точка (1) має вищий пріоритет за власний тил
            return 500;
        } else if (point.assignedTeam == Team.BLUE) {
            // Точки на території Синіх мають найвищий пріоритет (чим більший індекс, тим глибше у ворожому тилу)
            return 1000 + point.index;
        }
        return 0;
    }

    /**
     * Оцінка просування для Синіх:
     * Власна лінія (BLUE) -> Центр (1_NEUTRAL) -> Ворожа лінія (RED)
     */
    private static int calculateScoreForBlue(CapturePoint point) {
        if (point.assignedTeam == Team.BLUE) {
            return 100 - point.index;
        } else if (point.assignedTeam == Team.NEUTRAL) {
            return 500;
        } else if (point.assignedTeam == Team.RED) {
            return 1000 + point.index;
        }
        return 0;
    }
}