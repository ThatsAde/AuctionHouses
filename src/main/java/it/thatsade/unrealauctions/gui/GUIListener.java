package it.thatsade.unrealauctions.gui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

public class GUIListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();

        if (holder instanceof PaginatedGUI gui) {
            event.setCancelled(true);
            // Also cancel bottom inventory clicks to prevent shift-clicking out
            gui.handleClick(event);
            return;
        }

        if (holder instanceof BuyConfirmGUI gui) {
            event.setCancelled(true);
            gui.handleClick(event);
            return;
        }

        if (holder instanceof ListingCancelConfirmGUI gui) {
            event.setCancelled(true);
            gui.handleClick(event);
            return;
        }

        if (holder instanceof OrderCreationGUI gui) {
            event.setCancelled(true);
            gui.handleClick(event);
            return;
        }

        if (holder instanceof OrderFulfillConfirmGUI gui) {
            event.setCancelled(true);
            gui.handleClick(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof PaginatedGUI
                || holder instanceof BuyConfirmGUI
                || holder instanceof ListingCancelConfirmGUI
                || holder instanceof OrderCreationGUI
                || holder instanceof OrderFulfillConfirmGUI) {
            event.setCancelled(true);
        }
    }
}
