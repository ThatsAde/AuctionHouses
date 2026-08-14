package it.thatsade.unrealauctions.gui;

import it.thatsade.unrealauctions.config.Config;
import it.thatsade.unrealauctions.config.Messages;
import it.thatsade.unrealauctions.model.CollectionItem;
import it.thatsade.unrealauctions.service.AuctionService;
import it.thatsade.unrealauctions.util.TimeFormatter;
import static it.thatsade.unrealauctions.gui.PaginatedGUI.noItalic;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CollectionBoxGUI extends PaginatedGUI {

    private static final int SLOT_RETURN = 49;

    private final AuctionService auctionService;
    private final Config config;
    private final Map<Integer, CollectionItem> slotToItem = new HashMap<>();

    public CollectionBoxGUI(Player player, Messages messages, AuctionService auctionService, Config config) {
        super(player, messages);
        this.auctionService = auctionService;
        this.config = config;
    }

    @Override
    protected String getTitleKey() {
        return "gui-collection-box-title";
    }

    @Override
    protected void populate() {
        slotToItem.clear();
        auctionService.fetchCollectionBox(player.getUniqueId(), items -> {
            if (!isOpen()) return;
            totalPages = Math.max(1, (int) Math.ceil((double) items.size() / CONTENT_SLOTS));
            clearContent();

            int start = currentPage * CONTENT_SLOTS;
            int end = Math.min(start + CONTENT_SLOTS, items.size());
            for (int i = start; i < end; i++) {
                CollectionItem ci = items.get(i);
                int slot = i - start;
                inventory.setItem(slot, buildCollectionDisplay(ci));
                slotToItem.put(slot, ci);
            }

            // Solid glass backing so unused control slots never show as empty holes.
            ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ");
            for (int s = 45; s <= 53; s++) inventory.setItem(s, filler);

            if (currentPage > 0) {
                inventory.setItem(SLOT_PREV, buildNavItem(Material.ARROW,
                        messages.getNoPrefix("btn-prev-page"), true));
            }
            if (currentPage < totalPages - 1) {
                inventory.setItem(SLOT_NEXT, buildNavItem(Material.ARROW,
                        messages.getNoPrefix("btn-next-page"), true));
            }
            inventory.setItem(SLOT_RETURN, buildItem(Material.ARROW,
                    messages.getNoPrefix("btn-return")));
        });
    }

    private ItemStack buildCollectionDisplay(CollectionItem ci) {
        ItemStack display = ci.getItem() != null
                ? ci.getItem().clone()
                : new ItemStack(Material.BARRIER);
        ItemMeta meta = display.getItemMeta();

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        String sourceKey = switch (ci.getSource()) {
            case PURCHASE -> "lore-source-purchase";
            case CANCELLATION -> "lore-source-cancelled";
            case EXPIRY -> "lore-source-expired";
            case ORDER_FULFILLED -> "lore-source-order";
        };
        lore.add(noItalic(messages.getNoPrefix(sourceKey)));
        if (ci.getExpiresAt() > 0) {
            lore.add(noItalic(messages.getNoPrefix("lore-expires-in",
                    Map.of("time", TimeFormatter.formatDuration(ci.getMillisUntilExpiry())))));
        }
        lore.add(noItalic(messages.getNoPrefix("lore-click-collect")));
        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == SLOT_PREV && currentPage > 0) { currentPage--; refresh(); return; }
        if (slot == SLOT_NEXT && currentPage < totalPages - 1) { currentPage++; refresh(); return; }
        if (slot == SLOT_RETURN) {
            new MainBrowseGUI(player, messages, auctionService, config).open();
            return;
        }

        CollectionItem ci = slotToItem.get(slot);
        if (ci == null || ci.getItem() == null) return;

        if (player.getInventory().firstEmpty() == -1) {
            messages.send(player, "inventory-full");
            return;
        }

        // Remove from slot immediately to prevent double-clicks
        inventory.setItem(slot, null);
        slotToItem.remove(slot);

        auctionService.collectItem(player, ci.getId(), result -> {
            if (result.isSuccess()) {
                messages.send(player, "item-collected");
                refresh();
            } else {
                messages.send(player, result.getMessageKey() != null
                        ? result.getMessageKey() : "internal-error");
                // Re-populate to restore the item display
                refresh();
            }
        });
    }
}
