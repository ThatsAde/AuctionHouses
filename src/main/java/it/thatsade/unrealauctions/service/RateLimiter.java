package it.thatsade.unrealauctions.service;

import it.thatsade.unrealauctions.config.Config;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Main-thread-only rate limiter. Not thread-safe by design — always call from Bukkit main thread.
 */
public class RateLimiter {

    private final int listingsPerMinute;
    private final int purchasesPerMinute;
    private final long cooldownMillis;

    private final Map<UUID, Deque<Long>> listingTimestamps = new HashMap<>();
    private final Map<UUID, Deque<Long>> purchaseTimestamps = new HashMap<>();
    private final Map<UUID, Long> listingCooldowns = new HashMap<>();

    public RateLimiter(Config config) {
        this.listingsPerMinute = config.getListingsPerMinute();
        this.purchasesPerMinute = config.getPurchasesPerMinute();
        this.cooldownMillis = config.getListingCooldownSeconds() * 1000L;
    }

    public boolean checkListingAllowed(UUID uuid) {
        long now = System.currentTimeMillis();

        // Per-listing cooldown check
        Long lastListing = listingCooldowns.get(uuid);
        if (lastListing != null && now - lastListing < cooldownMillis) {
            return false;
        }

        // Per-minute rate check
        Deque<Long> timestamps = listingTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        pruneOld(timestamps, now, 60_000);
        return timestamps.size() < listingsPerMinute;
    }

    public long getCooldownRemaining(UUID uuid) {
        Long lastListing = listingCooldowns.get(uuid);
        if (lastListing == null) return 0;
        long remaining = cooldownMillis - (System.currentTimeMillis() - lastListing);
        return Math.max(0, remaining / 1000);
    }

    public void recordListing(UUID uuid) {
        long now = System.currentTimeMillis();
        listingCooldowns.put(uuid, now);
        listingTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>()).addLast(now);
    }

    public boolean checkPurchaseAllowed(UUID uuid) {
        long now = System.currentTimeMillis();
        Deque<Long> timestamps = purchaseTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        pruneOld(timestamps, now, 60_000);
        return timestamps.size() < purchasesPerMinute;
    }

    public void recordPurchase(UUID uuid) {
        purchaseTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>())
                .addLast(System.currentTimeMillis());
    }

    private void pruneOld(Deque<Long> timestamps, long now, long windowMillis) {
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMillis) {
            timestamps.pollFirst();
        }
    }
}
