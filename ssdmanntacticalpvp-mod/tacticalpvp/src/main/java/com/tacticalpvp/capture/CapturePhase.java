package com.tacticalpvp.capture;

/**
 * A point being taken from an enemy owner goes through two stages:
 * first the enemy's control is driven down to neutral (NEUTRALIZING), then the
 * attacker builds their own control up from 0 (CAPTURING). Taking an already-neutral
 * point skips straight to CAPTURING.
 */
public enum CapturePhase {
    IDLE,
    NEUTRALIZING,
    CAPTURING
}
