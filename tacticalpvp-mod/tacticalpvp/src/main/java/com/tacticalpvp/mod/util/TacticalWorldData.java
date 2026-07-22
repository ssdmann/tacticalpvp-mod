package com.tacticalpvp.mod.util;

import com.tacticalpvp.mod.match.MatchState;
import com.tacticalpvp.mod.match.TeamZone;
import com.tacticalpvp.mod.points.CapturePoint;
import com.tacticalpvp.mod.punish.PunishEntry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Єдине джерело істини для всіх персистентних даних мода:
 * точки захоплення, зони вибору команди, координата в'язниці, активні покарання,
 * стан матчу. Зберігається у папці world/data/tacticalpvp.dat.
 */
public class TacticalWorldData extends SavedData {

    public static final String ID = "tacticalpvp_data";

    public final Map<Integer, CapturePoint> points = new LinkedHashMap<>();
    public final List<TeamZone> teamZones = new ArrayList<>();
    public BlockPos punishPoint;
    public final List<PunishEntry> punishEntries = new ArrayList<>();
    public MatchState matchState = new MatchState();

    public static TacticalWorldData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                TacticalWorldData::load, TacticalWorldData::new, ID);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag pointsTag = new ListTag();
        points.values().forEach(p -> pointsTag.add(p.serialize()));
        tag.put("points", pointsTag);

        ListTag zonesTag = new ListTag();
        teamZones.forEach(z -> zonesTag.add(z.serialize()));
        tag.put("teamZones", zonesTag);

        if (punishPoint != null) {
            CompoundTag pp = new CompoundTag();
            pp.putInt("x", punishPoint.getX());
            pp.putInt("y", punishPoint.getY());
            pp.putInt("z", punishPoint.getZ());
            tag.put("punishPoint", pp);
        }

        ListTag punishTag = new ListTag();
        punishEntries.forEach(p -> punishTag.add(p.serialize()));
        tag.put("punishEntries", punishTag);

        tag.put("matchState", matchState.serialize());
        return tag;
    }

    public static TacticalWorldData load(CompoundTag tag) {
        TacticalWorldData data = new TacticalWorldData();

        ListTag pointsTag = tag.getList("points", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < pointsTag.size(); i++) {
            CapturePoint p = CapturePoint.deserialize(pointsTag.getCompound(i));
            data.points.put(p.index, p);
        }

        ListTag zonesTag = tag.getList("teamZones", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < zonesTag.size(); i++) {
            data.teamZones.add(TeamZone.deserialize(zonesTag.getCompound(i)));
        }

        if (tag.contains("punishPoint")) {
            CompoundTag pp = tag.getCompound("punishPoint");
            data.punishPoint = new BlockPos(pp.getInt("x"), pp.getInt("y"), pp.getInt("z"));
        }

        ListTag punishTag = tag.getList("punishEntries", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < punishTag.size(); i++) {
            data.punishEntries.add(PunishEntry.deserialize(punishTag.getCompound(i)));
        }

        if (tag.contains("matchState")) {
            data.matchState = MatchState.deserialize(tag.getCompound("matchState"));
        }

        return data;
    }
}
