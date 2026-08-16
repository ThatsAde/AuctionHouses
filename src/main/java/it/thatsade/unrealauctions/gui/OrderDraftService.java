package it.thatsade.unrealauctions.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the order a player is currently drafting, so the /ah order command and
 * the chat price capture can feed values back into the open creation screen.
 * The item goes through a command because only commands get Minecraft's
 * autocomplete dropdown; the price is typed in chat, which needs no dropdown.
 */
public class OrderDraftService implements Listener {

    private final Map<UUID, OrderCreationGUI> drafts = new ConcurrentHashMap<>();

    /** Players whose next chat line should be read as the offer on their draft. */
    private final Set<UUID> awaitingPrice = ConcurrentHashMap.newKeySet();

    public void register(Player player, OrderCreationGUI gui) {
        drafts.put(player.getUniqueId(), gui);
    }

    public void awaitPrice(Player player) {
        awaitingPrice.add(player.getUniqueId());
    }

    /** Read from the async chat thread, hence the concurrent set. */
    public boolean isAwaitingPrice(Player player) {
        return awaitingPrice.contains(player.getUniqueId());
    }

    public void stopAwaitingPrice(Player player) {
        awaitingPrice.remove(player.getUniqueId());
    }

    /** The draft this player is filling in, or null if they have none open. */
    public OrderCreationGUI get(Player player) {
        return drafts.get(player.getUniqueId());
    }

    public void clear(Player player) {
        drafts.remove(player.getUniqueId());
        awaitingPrice.remove(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        drafts.remove(event.getPlayer().getUniqueId());
        awaitingPrice.remove(event.getPlayer().getUniqueId());
    }
}
