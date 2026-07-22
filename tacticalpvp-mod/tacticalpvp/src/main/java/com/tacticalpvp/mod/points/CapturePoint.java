package com.tacticalpvp.mod.points;

import com.tacticalpvp.mod.util.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

public class CapturePoint {

    public final int index;
    public final Team assignedTeam; // До якої лінії/команди належить точка (RED чи BLUE)
    public BlockPos center;
    public int length;
    public int width;

    // На старті будь-яка точка завжди НЕЙТРАЛЬНА
    public Team owner = Team.NEUTRAL;

    public double captureProgress = 0.0;
    public Team capturingTeam = Team.NEUTRAL;

    public TeamSpawnPoint redSpawn;
    public TeamSpawnPoint blueSpawn;

    public CapturePoint(int index, Team assignedTeam, BlockPos center, int length, int width) {
        this.index = index;
        this.assignedTeam = assignedTeam;
        this.center = center.below(); // Поправка Y - 1
        this.length = length;
        this.width = width;
        this.owner = Team.NEUTRAL; // Завжди нейтральна при створенні!
    }

    private CapturePoint(int index, Team assignedTeam, BlockPos center, int length, int width, Team owner, boolean isRaw) {
        this.index = index;
        this.assignedTeam = assignedTeam;
        this.center = center;
        this.length = length;
        this.width = width;
        this.owner = owner;
    }

    /**
     * Унікальний ID точки для карти/збережень (наприклад: "2_red" або "2_blue")
     */
    public String UniqueId() {
        return index + "_" + assignedTeam.name().toLowerCase();
    }

    public boolean isInside(BlockPos pos) {
        int halfL = length / 2;
        int halfW = width / 2;
        return pos.getX() >= center.getX() - halfL && pos.getX() <= center.getX() + halfL
                && pos.getZ() >= center.getZ() - halfW && pos.getZ() <= center.getZ() + halfW;
    }

    public void tickCaptureProgress(Team team, int totalCaptureTimeSeconds) {
        if (totalCaptureTimeSeconds <= 0) totalCaptureTimeSeconds = 10;
        double speedPerTick = 100.0 / (totalCaptureTimeSeconds * 20.0);

        if (capturingTeam != team) {
            captureProgress -= speedPerTick;
            if (captureProgress <= 0.0) {
                captureProgress = 0.0;
                capturingTeam = team;
            }
        } else {
            captureProgress += speedPerTick;
            if (captureProgress >= 100.0) {
                captureProgress = 100.0;
                this.owner = team;
            }
        }
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("index", index);
        tag.putString("assignedTeam", assignedTeam.name());
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
        Team assigned = tag.contains("assignedTeam") ? Team.valueOf(tag.getString("assignedTeam")) : Team.NEUTRAL;
        CapturePoint p = new CapturePoint(
                tag.getInt("index"),
                assigned,
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                tag.getInt("length"), tag.getInt("width"),
                Team.valueOf(tag.getString("owner")),
                true);
        p.captureProgress = tag.getDouble("progress");
        p.capturingTeam = Team.valueOf(tag.getString("capturingTeam"));
        if (tag.contains("redSpawn")) p.redSpawn = TeamSpawnPoint.deserialize(tag.getCompound("redSpawn"));
        if (tag.contains("blueSpawn")) p.blueSpawn = TeamSpawnPoint.deserialize(tag.getCompound("blueSpawn"));
        return p;
    }
}