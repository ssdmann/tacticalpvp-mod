package com.tacticalpvp.mod.points;

import com.tacticalpvp.mod.util.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * Точка відродження, прив'язана до індексу контрольної точки та команди.
 * Радіус відродження навколо координати — 7 блоків (SPAWN_RADIUS).
 */
public class TeamSpawnPoint {

    public static final int SPAWN_RADIUS = 7;

    public final int attachedIndex;
    public final Team team;
    public BlockPos pos;

    public TeamSpawnPoint(int attachedIndex, Team team, BlockPos pos) {
        this.attachedIndex = attachedIndex;
        this.team = team;
        this.pos = pos;
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("attachedIndex", attachedIndex);
        tag.putString("team", team.name());
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        return tag;
    }

    public static TeamSpawnPoint deserialize(CompoundTag tag) {
        return new TeamSpawnPoint(tag.getInt("attachedIndex"), Team.valueOf(tag.getString("team")),
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")));
    }
}
