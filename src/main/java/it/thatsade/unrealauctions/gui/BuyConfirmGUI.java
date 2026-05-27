package it.thatsade.unrealauctions.gui;

import it.thatsade.unrealauctions.config.Config;
import it.thatsade.unrealauctions.config.Messages;
import it.thatsade.unrealauctions.model.Listing;
import it.thatsade.unrealauctions.service.AuctionService;
import it.thatsade.unrealauctions.util.PriceFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import static it.thatsade.unrealauctions.gui.PaginatedGUI.noItalic;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
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

public class BuyConfirmGUI implements InventoryHolder {

    private final Player player;
    private final Messages messages;
    private final AuctionService auctionService;
    private final Config config;
    private final Listing listing;
    private Inventory inventory;

    public BuyConfirmGUI(Player player, Messages messages, AuctionService auctionService,
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
                messages.getNoPrefix("gui-buy-confirm-title"));
        inventory = Bukkit.createInventory(this, 27, title);

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler);

        // Item preview in center
        ItemStack preview = listing.getItem() != null ? listing.getItem().clone() : new ItemStack(Material.BARRIER);
        ItemMeta meta = preview.getItemMeta();
        meta.lore(List.of(
                noItalic(messages.getNoPrefix("lore-seller", Map.of("seller", listing.getSellerName()))),
                noItalic(messages.getNoPrefix("lore-price",
                        Map.of("price", PriceFormatter.format(listing.getPrice(), config.getCurrencySymbol()))))
        ));
        preview.setItemMeta(meta);
        inventory.setItem(13, preview);

        // Confirm (slot 11 - green)
        inventory.setItem(11, makeItem(Material.LIME_CONCRETE,
                messages.getNoPrefix("btn-confirm")));

        // Cancel (slot 15 - red)
        inventory.setItem(15, makeItem(Material.RED_CONCRETE,
                messages.getNoPrefix("btn-cancel")));

        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == 11) {
            // Pre-check inventory space before even submitting the transaction
            if (listing.getItem() != null && !auctionService.canFitItem(player, listing.getItem())) {
                messages.send(player, "inventory-full");
                return;
            }
            player.closeInventory();
            auctionService.buyItem(player, listing.getId(), result -> {
                if (result.isSuccess()) {
                    final ItemStack item = listing.getItem();
                    final ItemMeta meta = item != null ? item.getItemMeta() : null;
                    final Component nameComponent = meta != null
                            ? (meta.customName() != null ? meta.customName() : meta.displayName())
                            : Component.text("META_NOT_FOUND");
                    final String itemName = PlainTextComponentSerializer.plainText().serialize(nameComponent);
                    messages.send(player, "item-purchased",
                            Map.of("item", itemName,
                                    "price", PriceFormatter.format(listing.getPrice(), config.getCurrencySymbol())));
                } else {
                    String key = result.getMessageKey() != null ? result.getMessageKey() : "internal-error";
                    if ("not-enough-funds".equals(key)) {
                        messages.send(player, "not-enough-funds",
                                Map.of("price", PriceFormatter.format(listing.getPrice(), config.getCurrencySymbol())));
                    } else {
                        messages.send(player, key);
                    }
                }
            });
        } else if (slot == 15) {
            player.closeInventory();
            new MainBrowseGUI(player, messages, auctionService, config).open();
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
