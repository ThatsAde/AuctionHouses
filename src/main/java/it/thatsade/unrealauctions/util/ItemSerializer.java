package it.thatsade.unrealauctions.util;

import org.bukkit.inventory.ItemStack;

import java.util.logging.Logger;

public final class ItemSerializer {

    private static final Logger log = Logger.getLogger("UnrealAuctions");

    private ItemSerializer() {}

    public static byte[] serialize(ItemStack item) {
        try {
            return item.serializeAsBytes();
        } catch (Exception e) {
            log.severe("Failed to serialize ItemStack: " + e.getMessage());
            return new byte[0];
        }
    }

    public static ItemStack deserialize(byte[] data, String context) {
        if (data == null || data.length == 0) return null;
        try {
            return ItemStack.deserializeBytes(data);
        } catch (Exception e) {
            log.warning("Failed to deserialize ItemStack [" + context + "]: " + e.getMessage());
            return null;
        }
    }
}
