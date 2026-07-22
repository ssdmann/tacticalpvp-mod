package com.tacticalpvp.mod.points;

import com.tacticalpvp.mod.util.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Точка захоплення (Capture Point).
 * Координати виконання адмін-команди — це ЦЕНТР зони. Висота зони нескінченна
 * (перевіряється лише по X/Z в межах length/width).
 *
 * Точка сама по собі більше НЕ є точкою відродження — вона лише "тригер/ключ".
 * Реальні точки відродження — це прив'язані TeamSpawnPoint (red/blue).
 */
public class CapturePoint {

    public final int index;
    public BlockPos center;
    public int length;
    public int width;

    public Team owner = Team.NEUTRAL;

    // Прогрес захоплення 0-100. capturingTeam — хто зараз намагається захопити.
    public double captureProgress = 0.0;
    public Team capturingTeam = Team.NEUTRAL;

    // Прив'язані спавнпоінти (можуть бути null, якщо ще не створені)
    public TeamSpawnPoint redSpawn;
    public TeamSpawnPoint blueSpawn;

    public CapturePoint(int index, BlockPos center, int length, int width, Team initialOwner) {
        this.index = index;
        this.center = center;
        this.length = length;
        this.width = width;
        this.owner = initialOwner;
    }

    /** Перевірка чи знаходиться позиція всередині зони (X/Z, висота нескінченна). */
    public boolean isInside(BlockPos pos) {
        int halfL = length / 2;
        int halfW = width / 2;
        return pos.getX() >= center.getX() - halfL && pos.getX() <= center.getX() + halfL
                && pos.getZ() >= center.getZ() - halfW && pos.getZ() <= center.getZ() + halfW;
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("index", index);
        tag.putInt("x", center.getX());
        tag.putInt("y", center.getY());
        tag.putInt("z", center.getZ());
        tag.putInt("length", length);
        tag.putInt("width", width);
        tag.putString("owner", owner.name());
        tag.putDouble("progress", captureProgress);
        tag.putString("capturingTeam", capturingTeam.name());
        if (redSpawn != null) tag.put("redSpawn", redSpawn.serialize());
        if (blueSpawn != null) tag.put("blueSpawn", blueSpawn.serialize());
        return tag;
    }

    public static CapturePoint deserialize(CompoundTag tag) {
        CapturePoint p = new CapturePoint(
                tag.getInt("index"),
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                tag.getInt("length"), tag.getInt("width"),
                Team.valueOf(tag.getString("owner")));
        p.captureProgress = tag.getDouble("progress");
        p.capturingTeam = Team.valueOf(tag.getString("capturingTeam"));
        if (tag.contains("redSpawn")) p.redSpawn = TeamSpawnPoint.deserialize(tag.getCompound("redSpawn"));
        if (tag.contains("blueSpawn")) p.blueSpawn = TeamSpawnPoint.deserialize(tag.getCompound("blueSpawn"));
        return p;
    }
}
