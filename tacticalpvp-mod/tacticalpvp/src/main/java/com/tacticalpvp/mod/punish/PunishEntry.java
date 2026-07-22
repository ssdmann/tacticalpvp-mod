package com.tacticalpvp.mod.punish;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

/**
 * Запис про активне покарання гравця. remainingTicks зменшується щотіку і
 * зберігається у World Save Data, тому переживає рестарт сервера.
 */
public class PunishEntry {
    public UUID playerId;
    public int remainingTicks;

    public PunishEntry(UUID playerId, int remainingTicks) {
        this.playerId = playerId;
        this.remainingTicks = remainingTicks;
    }

    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("player", playerId);
        tag.putInt("remaining", remainingTicks);
        return tag;
    }

    public static PunishEntry deserialize(CompoundTag tag) {
        return new PunishEntry(tag.getUUID("player"), tag.getInt("remaining"));
    }
}
