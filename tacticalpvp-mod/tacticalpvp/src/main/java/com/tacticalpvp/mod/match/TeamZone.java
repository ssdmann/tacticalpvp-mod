package com.tacticalpvp.mod.match;

import com.tacticalpvp.mod.util.Team;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/** Фізична зона вибору команди — гравець заходить у неї і отримує команду. */
public class TeamZone {
    public Team team;
    public BlockPos center;
    public int length;
    public int width;

    public TeamZone(Team team, BlockPos center, int length, int width) {
        this.team = team;
        this.center = center;
        this.length = length;
        this.width = width;
    }

    public boolean isInside(BlockPos pos) {
        int halfL = length / 2;
        int halfW = width / 2;
        return pos.getX() >= center.getX() - halfL && pos.getX() <= center.getX() + halfL
                && pos.getZ() >= center.getZ() - halfW && pos.getZ() <= center.getZ() + halfW;
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString("team", team.name());
        tag.putInt("x", center.getX());
        tag.putInt("y", center.getY());
        tag.putInt("z", center.getZ());
        tag.putInt("length", length);
        tag.putInt("width", width);
        return tag;
    }

    public static TeamZone deserialize(CompoundTag tag) {
        return new TeamZone(Team.valueOf(tag.getString("team")),
                new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")),
                tag.getInt("length"), tag.getInt("width"));
    }
}
