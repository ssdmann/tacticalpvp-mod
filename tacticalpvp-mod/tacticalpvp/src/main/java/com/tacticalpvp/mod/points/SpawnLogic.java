package com.tacticalpvp.mod.points;

import com.tacticalpvp.mod.util.Team;

import java.util.Map;

/**
 * Визначає, який спавн команди наразі АКТИВНИЙ (фронтовий).
 *
 * Правило: спавн команди T, прив'язаний до точки N, активний, ЯКЩО точка (N-1)
 * НЕ контролюється протилежною командою. Якщо (N-1) захоплена ворогом —
 * спавн команди T на точці N деактивується (гравці не можуть відроджуватись
 * позаду ворожих ліній), і натомість активним стає спавн ворожої команди на точці N.
 *
 * Якщо точки (N-1) не існує (наприклад N — крайня/базова точка), спавн вважається
 * завжди активним для власної команди.
 */
public class SpawnLogic {

    /** Повертає найбільш "просунуту" (найближчу до фронту) активну точку відродження команди. */
    public static TeamSpawnPoint findActiveFrontlineSpawn(Map<Integer, CapturePoint> points, Team team) {
        TeamSpawnPoint best = null;
        int bestIndex = Integer.MIN_VALUE;

        for (CapturePoint point : points.values()) {
            TeamSpawnPoint candidate = team == Team.RED ? point.redSpawn : point.blueSpawn;
            if (candidate == null) continue;
            if (!isActive(points, point, team)) continue;
            if (point.index > bestIndex) {
                bestIndex = point.index;
                best = candidate;
            }
        }
        return best;
    }

    public static boolean isActive(Map<Integer, CapturePoint> points, CapturePoint point, Team team) {
        CapturePoint previous = points.get(point.index - 1);
        if (previous == null) return true; // немає попередньої точки - завжди активний
        return previous.owner != team.opposite();
    }
}
