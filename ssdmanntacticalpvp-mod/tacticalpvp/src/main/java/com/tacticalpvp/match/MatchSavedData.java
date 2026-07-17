package com.tacticalpvp.match;

import com.tacticalpvp.capture.CapturePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * World-persisted data: all capture points, waiting lobby locations, match configuration
 * (duration / score limit / lobby timer) and live match state (scores, remaining time).
 * Stored via vanilla's SavedData mechanism -> serialized into the level's data folder,
 * so it survives server restarts automatically.
 */
public class MatchSavedData extends SavedData {

    public static final String DATA_NAME = "tacticalpvp_match";

    // Keyed by "DESIGNATION#index" so a Red-side and Blue-side point can never collide,
    // and /point delete can target an exact point unambiguously.
    private final Map<String, CapturePoint> points = new LinkedHashMap<>();

    private BlockPos redLobby = null;
    private BlockPos blueLobby = null;

    private int configuredDurationSeconds = 600; // 0 == infinite
    private int scoreLimit = 50;
    private int lobbyTimerSeconds = 10;

    // live match state (also persisted so a crash/restart mid-match doesn't lose progress)
    private MatchState state = MatchState.IDLE;
    private int remainingTicks = 0;
    private final Map<TeamColor, Integer> scores = new HashMap<>();

    public MatchSavedData() {
        scores.put(TeamColor.RED, 0);
        scores.put(TeamColor.BLUE, 0);
    }

    public static MatchSavedData get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MatchSavedData::new, MatchSavedData::load),
                DATA_NAME
        );
    }

    public static MatchSavedData get(MinecraftServer server) {
        return get(server.overworld());
    }

    // ---------- points ----------

    private static String pointKey(TeamColor designation, int index) {
        return designation.getKey() + "#" + index;
    }

    public void addPoint(CapturePoint point) {
        points.put(pointKey(point.getDesignation(), point.getIndex()), point);
        setDirty();
    }

    public CapturePoint getPoint(TeamColor designation, int index) {
        return points.get(pointKey(designation, index));
    }

    /** @return true if a matching point existed and was removed. */
    public boolean removePoint(TeamColor designation, int index) {
        boolean removed = points.remove(pointKey(designation, index)) != null;
        if (removed) setDirty();
        return removed;
    }

    public java.util.Collection<CapturePoint> getPoints() {
        return points.values();
    }

    /** All configured points ordered along the Red-base -> center -> Blue-base chain. */
    public java.util.List<CapturePoint> getPointsOrderedByPosition() {
        java.util.List<CapturePoint> list = new java.util.ArrayList<>(points.values());
        list.sort(java.util.Comparator.comparingInt(CapturePoint::getChainPosition));
        return list;
    }

    private TreeMap<Integer, CapturePoint> byPosition() {
        TreeMap<Integer, CapturePoint> map = new TreeMap<>();
        for (CapturePoint p : points.values()) {
            map.put(p.getChainPosition(), p);
        }
        return map;
    }

    /** The existing point immediately closer to the Red base along the chain, if any. */
    public CapturePoint getNeighborTowardRed(int position) {
        Map.Entry<Integer, CapturePoint> e = byPosition().lowerEntry(position);
        return e == null ? null : e.getValue();
    }

    /** The existing point immediately closer to the Blue base along the chain, if any. */
    public CapturePoint getNeighborTowardBlue(int position) {
        Map.Entry<Integer, CapturePoint> e = byPosition().higherEntry(position);
        return e == null ? null : e.getValue();
    }

    /**
     * The given team's current frontline: the point they currently own that sits
     * furthest INTO the chain towards the opponent (i.e. closest to the action).
     * Red pushes towards increasing chain position; Blue pushes towards decreasing.
     */
    public CapturePoint getFrontlinePointFor(TeamColor team) {
        CapturePoint best = null;
        for (CapturePoint p : points.values()) {
            if (p.getOwner() != team) continue;
            if (best == null) {
                best = p;
                continue;
            }
            if (team == TeamColor.RED) {
                if (p.getChainPosition() > best.getChainPosition()) best = p;
            } else if (team == TeamColor.BLUE) {
                if (p.getChainPosition() < best.getChainPosition()) best = p;
            }
        }
        return best;
    }

    public void purgeAll() {
        points.clear();
        redLobby = null;
        blueLobby = null;
        configuredDurationSeconds = 600;
        scoreLimit = 50;
        lobbyTimerSeconds = 10;
        state = MatchState.IDLE;
        remainingTicks = 0;
        scores.put(TeamColor.RED, 0);
        scores.put(TeamColor.BLUE, 0);
        setDirty();
    }

    // ---------- lobbies ----------

    public void setLobby(TeamColor team, BlockPos pos) {
        if (team == TeamColor.RED) redLobby = pos;
        else if (team == TeamColor.BLUE) blueLobby = pos;
        setDirty();
    }

    public BlockPos getLobby(TeamColor team) {
        return team == TeamColor.RED ? redLobby : (team == TeamColor.BLUE ? blueLobby : null);
    }

    // ---------- config ----------

    public int getConfiguredDurationSeconds() {
        return configuredDurationSeconds;
    }

    public void setConfiguredDurationSeconds(int s) {
        this.configuredDurationSeconds = s;
        setDirty();
    }

    public int getScoreLimit() {
        return scoreLimit;
    }

    public void setScoreLimit(int scoreLimit) {
        this.scoreLimit = scoreLimit;
        setDirty();
    }

    public int getLobbyTimerSeconds() {
        return lobbyTimerSeconds;
    }

    public void setLobbyTimerSeconds(int lobbyTimerSeconds) {
        this.lobbyTimerSeconds = lobbyTimerSeconds;
        setDirty();
    }

    // ---------- live state ----------

    public MatchState getState() {
        return state;
    }

    public void setState(MatchState state) {
        this.state = state;
        setDirty();
    }

    /** true when duration was configured as 0 -> infinite time, score-limit-only victory. */
    public boolean isInfiniteTime() {
        return configuredDurationSeconds <= 0;
    }

    public int getRemainingTicks() {
        return remainingTicks;
    }

    public void setRemainingTicks(int ticks) {
        this.remainingTicks = ticks;
        setDirty();
    }

    public void decrementRemainingTicks() {
        if (!isInfiniteTime() && remainingTicks > 0) {
            remainingTicks--;
            setDirty();
        }
    }

    public int getScore(TeamColor team) {
        return scores.getOrDefault(team, 0);
    }

    public void addScore(TeamColor team, int amount) {
        scores.put(team, getScore(team) + amount);
        setDirty();
    }

    public void resetScores() {
        scores.put(TeamColor.RED, 0);
        scores.put(TeamColor.BLUE, 0);
        setDirty();
    }

    // ---------- NBT ----------

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag pointList = new ListTag();
        for (CapturePoint p : points.values()) {
            pointList.add(p.serialize());
        }
        tag.put("Points", pointList);

        if (redLobby != null) tag.putLong("RedLobby", redLobby.asLong());
        if (blueLobby != null) tag.putLong("BlueLobby", blueLobby.asLong());

        tag.putInt("ConfiguredDuration", configuredDurationSeconds);
        tag.putInt("ScoreLimit", scoreLimit);
        tag.putInt("LobbyTimer", lobbyTimerSeconds);

        tag.putString("State", state.name());
        tag.putInt("RemainingTicks", remainingTicks);
        tag.putInt("ScoreRed", getScore(TeamColor.RED));
        tag.putInt("ScoreBlue", getScore(TeamColor.BLUE));
        return tag;
    }

    public static MatchSavedData load(CompoundTag tag) {
        MatchSavedData data = new MatchSavedData();

        ListTag pointList = tag.getList("Points", 10); // 10 = CompoundTag id
        for (int i = 0; i < pointList.size(); i++) {
            CapturePoint p = CapturePoint.deserialize(pointList.getCompound(i));
            data.points.put(pointKey(p.getDesignation(), p.getIndex()), p);
        }

        if (tag.contains("RedLobby")) data.redLobby = BlockPos.of(tag.getLong("RedLobby"));
        if (tag.contains("BlueLobby")) data.blueLobby = BlockPos.of(tag.getLong("BlueLobby"));

        data.configuredDurationSeconds = tag.getInt("ConfiguredDuration");
        data.scoreLimit = tag.getInt("ScoreLimit");
        data.lobbyTimerSeconds = tag.getInt("LobbyTimer");

        data.state = MatchState.valueOf(tag.getString("State").isEmpty() ? "IDLE" : tag.getString("State"));
        data.remainingTicks = tag.getInt("RemainingTicks");
        data.scores.put(TeamColor.RED, tag.getInt("ScoreRed"));
        data.scores.put(TeamColor.BLUE, tag.getInt("ScoreBlue"));
        return data;
    }
}
