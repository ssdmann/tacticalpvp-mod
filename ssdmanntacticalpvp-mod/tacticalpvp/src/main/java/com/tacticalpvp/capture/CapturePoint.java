package com.tacticalpvp.capture;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.AABB;
import com.tacticalpvp.match.TeamColor;

/**
 * A single sector in the mirrored linear tug-of-war layout. Points are identified
 * by (designation, index): "designation" is the FIXED home side the point was
 * created on (RED, BLUE, or NONE for the single shared/neutral center point at
 * index 1), and never changes. "owner" is the DYNAMIC current controller, which
 * starts equal to designation and can flip via capture during play.
 *
 * The overall front-line is one continuous chain running from the Red base through
 * the neutral center out to the Blue base, e.g.:
 *   Red30 ... Red2, Neutral1, Blue2 ... Blue30
 * getChainPosition() maps a point onto a single signed integer axis along that
 * chain (negative = Red side, 0 = center, positive = Blue side) so tug-of-war
 * adjacency and frontline spawning can be computed generically.
 */
public class CapturePoint {

    public static final boolean DECAY_ON_LEAVE = false;

    private final int index;
    private final TeamColor designation; // fixed home side, set at creation, never changes
    private final int captureTimeTicks;  // full 0->100% capture duration
    private BlockPos corner1;
    private BlockPos corner2;

    private TeamColor owner;             // dynamic current controller
    private TeamColor capturingTeam = TeamColor.NONE;
    private CapturePhase phase = CapturePhase.IDLE;
    private int progressTicks = 0;
    private boolean contested = false;

    public CapturePoint(int index, TeamColor designation, int captureTimeSeconds, BlockPos corner1, BlockPos corner2) {
        this.index = index;
        this.designation = designation;
        this.captureTimeTicks = Math.max(1, captureTimeSeconds * 20);
        this.corner1 = corner1;
        this.corner2 = corner2;
        this.owner = designation; // starts owned by whichever side it was built on (NONE for the neutral center)
    }

    public int getIndex() {
        return index;
    }

    public TeamColor getDesignation() {
        return designation;
    }

    public int getCaptureTimeTicks() {
        return captureTimeTicks;
    }

    /** Half the full capture time - the duration required to neutralize an enemy-held point. */
    public int getNeutralizeTimeTicks() {
        return Math.max(1, captureTimeTicks / 2);
    }

    public TeamColor getOwner() {
        return owner;
    }

    public void setOwner(TeamColor owner) {
        this.owner = owner;
    }

    public TeamColor getCapturingTeam() {
        return capturingTeam;
    }

    public CapturePhase getPhase() {
        return phase;
    }

    public int getProgressTicks() {
        return progressTicks;
    }

    public boolean isContested() {
        return contested;
    }

    public void setContested(boolean contested) {
        this.contested = contested;
    }

    /**
     * Maps this point onto the single signed chain axis: the neutral center point is 0,
     * Red-side points are negative (RedN -> -(N-1)), Blue-side points are positive
     * (BlueN -> +(N-1)). By convention index 1 is reserved for the shared neutral center.
     */
    public int getChainPosition() {
        if (designation == TeamColor.NONE) return 0;
        int magnitude = Math.max(0, index - 1);
        return designation == TeamColor.RED ? -magnitude : magnitude;
    }

    /** Percentage 0-100 of progress within the CURRENT phase (neutralizing or capturing). */
    public int getProgressPercent() {
        int totalForPhase = phase == CapturePhase.NEUTRALIZING ? getNeutralizeTimeTicks() : captureTimeTicks;
        if (totalForPhase <= 0) return 0;
        return Math.min(100, (int) Math.round((progressTicks * 100.0) / totalForPhase));
    }

    public BlockPos getMinCorner() {
        return new BlockPos(
                Math.min(corner1.getX(), corner2.getX()),
                Math.min(corner1.getY(), corner2.getY()),
                Math.min(corner1.getZ(), corner2.getZ())
        );
    }

    public BlockPos getMaxCorner() {
        return new BlockPos(
                Math.max(corner1.getX(), corner2.getX()),
                Math.max(corner1.getY(), corner2.getY()),
                Math.max(corner1.getZ(), corner2.getZ())
        );
    }

    public AABB getBoundingBox() {
        BlockPos min = getMinCorner();
        BlockPos max = getMaxCorner();
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    /**
     * Advance capture/neutralize progress for one tick with the given attacker present.
     * @return true if the point just finished capturing this tick (owner changed to attacker).
     */
    public boolean tickProgress(TeamColor attacker) {
        if (attacker == TeamColor.NONE || attacker == owner) return false;

        if (capturingTeam != attacker) {
            // a new team started contesting this point - figure out which phase to begin in
            capturingTeam = attacker;
            progressTicks = 0;
            phase = (owner == TeamColor.NONE) ? CapturePhase.CAPTURING : CapturePhase.NEUTRALIZING;
        }

        progressTicks++;

        if (phase == CapturePhase.NEUTRALIZING) {
            if (progressTicks >= getNeutralizeTimeTicks()) {
                owner = TeamColor.NONE;
                phase = CapturePhase.CAPTURING;
                progressTicks = 0;
            }
            return false;
        }

        // phase == CAPTURING
        if (progressTicks >= captureTimeTicks) {
            owner = attacker;
            capturingTeam = TeamColor.NONE;
            phase = CapturePhase.IDLE;
            progressTicks = 0;
            return true;
        }
        return false;
    }

    /** Called when nobody from the capturing team remains and progress should stop advancing. */
    public void haltProgress() {
        if (DECAY_ON_LEAVE) {
            progressTicks = Math.max(0, progressTicks - 2);
            if (progressTicks == 0) {
                capturingTeam = TeamColor.NONE;
                phase = CapturePhase.IDLE;
            }
        }
        // default: freeze in place, keep phase/capturingTeam/progressTicks as-is
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Index", index);
        tag.putString("Designation", designation.getKey());
        tag.putInt("CaptureTimeTicks", captureTimeTicks);
        tag.putLong("Corner1", corner1.asLong());
        tag.putLong("Corner2", corner2.asLong());
        tag.putString("Owner", owner.getKey());
        tag.putString("CapturingTeam", capturingTeam.getKey());
        tag.putString("Phase", phase.name());
        tag.putInt("ProgressTicks", progressTicks);
        tag.putBoolean("Contested", contested);
        return tag;
    }

    public static CapturePoint deserialize(CompoundTag tag) {
        int index = tag.getInt("Index");
        TeamColor designation = TeamColor.fromString(tag.getString("Designation"));
        int captureTimeTicks = tag.getInt("CaptureTimeTicks");
        BlockPos c1 = BlockPos.of(tag.getLong("Corner1"));
        BlockPos c2 = BlockPos.of(tag.getLong("Corner2"));
        CapturePoint p = new CapturePoint(index, designation, Math.max(1, captureTimeTicks / 20), c1, c2);
        p.owner = TeamColor.fromString(tag.getString("Owner"));
        p.capturingTeam = TeamColor.fromString(tag.getString("CapturingTeam"));
        p.phase = tag.contains("Phase") ? CapturePhase.valueOf(tag.getString("Phase")) : CapturePhase.IDLE;
        p.progressTicks = tag.getInt("ProgressTicks");
        p.contested = tag.getBoolean("Contested");
        return p;
    }
}
