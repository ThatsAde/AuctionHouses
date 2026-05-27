package it.thatsade.unrealauctions.gui;

import it.thatsade.unrealauctions.config.Config;
import it.thatsade.unrealauctions.config.Messages;
import it.thatsade.unrealauctions.model.Listing;
import it.thatsade.unrealauctions.service.AuctionService;
import it.thatsade.unrealauctions.util.PriceFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import static it.thatsade.unrealauctions.gui.PaginatedGUI.noItalic;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;

public class ListingCancelConfirmGUI implements InventoryHolder {

    private final Player player;
    private final Messages messages;
    private final AuctionService auctionService;
    private final Config config;
    private final Listing listing;
    private Inventory inventory;

    public ListingCancelConfirmGUI(Player player, Messages messages, AuctionService auctionService,
                                   Config config, Listing listing) {
        this.player = player;
        this.messages = messages;
        this.auctionService = auctionService;
        this.config = config;
        this.listing = listing;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        Component title = LegacyComponentSerializer.legacySection().deserialize(
                messages.getNoPrefix("gui-listing-confirm-title"));
        inventory = Bukkit.createInventory(this, 27, title);

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler);

        // Item preview
        ItemStack preview = listing.getItem() != null ? listing.getItem().clone() : new ItemStack(Material.BARRIER);
        ItemMeta meta = preview.getItemMeta();
        meta.lore(List.of(
                noItalic(messages.getNoPrefix("lore-price",
                        Map.of("price", PriceFormatter.format(listing.getPrice(), config.getCurrencySymbol()))))
        ));
        preview.setItemMeta(meta);
        inventory.setItem(13, preview);

        inventory.setItem(11, makeItem(Material.LIME_CONCRETE, messages.getNoPrefix("btn-confirm")));
        inventory.setItem(15, makeItem(Material.RED_CONCRETE, messages.getNoPrefix("btn-cancel")));

        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == 11) {
            player.closeInventory();
            auctionService.cancelListing(player, listing.getId(), result -> {
                if (result.isSuccess()) {
                    messages.send(player, "listing-cancelled");
                } else {
                    messages.send(player, result.getMessageKey() != null
                            ? result.getMessageKey() : "internal-error");
                }
            });
        } else if (slot == 15) {
            new MyListingsGUI(player, messages, auctionService, config).open();
        }
    }

    private ItemStack makeItem(Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(name));
        item.setItemMeta(meta);
        return item;
    }
}
