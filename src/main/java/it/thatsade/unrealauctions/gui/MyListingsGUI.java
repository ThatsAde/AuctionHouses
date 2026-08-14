package it.thatsade.unrealauctions.gui;

import it.thatsade.unrealauctions.config.Config;
import it.thatsade.unrealauctions.config.Messages;
import it.thatsade.unrealauctions.model.Listing;
import it.thatsade.unrealauctions.service.AuctionService;
import it.thatsade.unrealauctions.util.PriceFormatter;
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

public class MyListingsGUI extends PaginatedGUI {

    private static final int SLOT_RETURN = 49;

    private final AuctionService auctionService;
    private final Config config;
    private final Map<Integer, Listing> slotToListing = new HashMap<>();

    public MyListingsGUI(Player player, Messages messages, AuctionService auctionService, Config config) {
        super(player, messages);
        this.auctionService = auctionService;
        this.config = config;
    }

    @Override
    protected String getTitleKey() {
        return "gui-my-listings-title";
    }

    @Override
    protected void populate() {
        slotToListing.clear();
        auctionService.fetchPlayerListings(player.getUniqueId(), listings -> {
            if (!isOpen()) return;
            totalPages = Math.max(1, (int) Math.ceil((double) listings.size() / CONTENT_SLOTS));
            clearContent();

            int start = currentPage * CONTENT_SLOTS;
            int end = Math.min(start + CONTENT_SLOTS, listings.size());
            for (int i = start; i < end; i++) {
                Listing listing = listings.get(i);
                int slot = i - start;
                inventory.setItem(slot, buildListingDisplay(listing));
                slotToListing.put(slot, listing);
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

    private ItemStack buildListingDisplay(Listing listing) {
        ItemStack display = listing.getItem() != null
                ? listing.getItem().clone()
                : new ItemStack(Material.BARRIER);
        ItemMeta meta = display.getItemMeta();

        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(noItalic(messages.getNoPrefix("lore-price",
                Map.of("price", PriceFormatter.format(listing.getPrice(), config.getCurrencySymbol())))));
        lore.add(noItalic(messages.getNoPrefix("lore-time-left",
                Map.of("time", TimeFormatter.formatDuration(listing.getMillisRemaining())))));
        lore.add(noItalic(messages.getNoPrefix("lore-right-click-cancel")));

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
        if (slot == SLOT_RETURN) { new MainBrowseGUI(player, messages, auctionService, config).open(); return; }

        Listing listing = slotToListing.get(slot);
        if (listing == null) return;

        new ListingCancelConfirmGUI(player, messages, auctionService, config, listing).open();
    }
}
