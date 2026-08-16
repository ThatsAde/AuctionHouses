package it.thatsade.unrealauctions.gui;

import it.thatsade.unrealauctions.config.Config;
import it.thatsade.unrealauctions.config.Messages;
import it.thatsade.unrealauctions.service.AuctionService;
import it.thatsade.unrealauctions.util.PriceFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static it.thatsade.unrealauctions.gui.PaginatedGUI.noItalic;

/**
 * Order draft screen: the player fills in the item, quantity and offer by typing
 * in chat, then confirms. The draft lives here so it survives the close/reopen
 * round-trip each chat prompt requires.
 */
public class OrderCreationGUI implements InventoryHolder {

    // All four controls sit on the middle row, spaced one slot apart.
    private static final int SLOT_ITEM = 10;
    private static final int SLOT_PRICE = 12;
    private static final int SLOT_CONFIRM = 14;
    private static final int SLOT_CANCEL = 16;

    private final Player player;
    private final Messages messages;
    private final AuctionService auctionService;
    private final Config config;
    private final OrderDraftService draftService;
    private Inventory inventory;

    private Material item;
    private int quantity;
    private long price = -1;

    public OrderCreationGUI(Player player, Messages messages, AuctionService auctionService,
                            Config config, OrderDraftService draftService) {
        this.player = player;
        this.messages = messages;
        this.auctionService = auctionService;
        this.config = config;
        this.draftService = draftService;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void setItem(Material item, int quantity) {
        this.item = item;
        this.quantity = quantity;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    private boolean isComplete() {
        return item != null && quantity > 0 && price >= 0;
    }

    public void open() {
        // Keep this draft reachable from /ah order and /ah orderprice.
        draftService.register(player, this);

        Component title = LegacyComponentSerializer.legacySection().deserialize(
                messages.getNoPrefix("gui-order-create-title"));
        inventory = Bukkit.createInventory(this, 27, title);

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler);

        inventory.setItem(SLOT_ITEM, buildItemButton());
        inventory.setItem(SLOT_PRICE, buildPriceButton());
        inventory.setItem(SLOT_CONFIRM, makeItem(
                isComplete() ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE,
                messages.getNoPrefix("btn-order-confirm")));
        inventory.setItem(SLOT_CANCEL, makeItem(Material.RED_CONCRETE,
                messages.getNoPrefix("btn-cancel")));

        player.openInventory(inventory);
    }

    private ItemStack buildItemButton() {
        ItemStack button = item != null
                ? new ItemStack(item, Math.min(quantity, item.getMaxStackSize()))
                : new ItemStack(Material.CHEST);
        ItemMeta meta = button.getItemMeta();
        meta.displayName(noItalic(messages.getNoPrefix("btn-set-item")));

        List<Component> lore = new ArrayList<>();
        if (item != null) {
            lore.add(noItalic(messages.getNoPrefix("lore-order-quantity",
                    Map.of("quantity", String.valueOf(quantity)))));
        } else {
            lore.add(noItalic(messages.getNoPrefix("lore-order-not-set")));
        }
        meta.lore(lore);
        button.setItemMeta(meta);
        return button;
    }

    private ItemStack buildPriceButton() {
        ItemStack button = new ItemStack(Material.PAPER);
        ItemMeta meta = button.getItemMeta();
        meta.displayName(noItalic(messages.getNoPrefix("btn-set-price")));

        List<Component> lore = new ArrayList<>();
        if (price >= 0) {
            lore.add(noItalic(messages.getNoPrefix("lore-order-offer",
                    Map.of("price", PriceFormatter.format(price, config.getCurrencySymbol())))));
        } else {
            lore.add(noItalic(messages.getNoPrefix("lore-order-not-set")));
        }
        meta.lore(lore);
        button.setItemMeta(meta);
        return button;
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        switch (slot) {
            case SLOT_ITEM -> promptFor("prompt-item-quantity", "/ah order ");
            case SLOT_PRICE -> promptForPrice();
            case SLOT_CONFIRM -> confirm();
            case SLOT_CANCEL -> {
                draftService.clear(player);
                player.closeInventory();
                new OrderBrowseGUI(player, messages, auctionService, config, draftService).open();
            }
            default -> { }
        }
    }

    /**
     * Closes the menu and offers a click-to-fill command. Typing the command is
     * what gives the player Minecraft's item autocomplete dropdown.
     */
    private void promptFor(String promptKey, String command) {
        player.closeInventory();
        player.sendMessage(messages.getComponent(promptKey)
                .clickEvent(ClickEvent.suggestCommand(command)));
    }

    /**
     * Closes the menu and reads the offer straight from chat. No command needed:
     * a price gets no autocomplete anyway, so typing it is the shorter path.
     */
    private void promptForPrice() {
        draftService.awaitPrice(player);
        player.closeInventory();
        messages.send(player, "prompt-price");
    }

    private void confirm() {
        if (!isComplete()) {
            messages.send(player, "order-incomplete");
            return;
        }

        final Material orderedItem = item;
        final int orderedQuantity = quantity;
        final long orderedPrice = price;

        draftService.clear(player);
        player.closeInventory();
        boolean submitted = auctionService.createOrder(player, orderedItem, orderedQuantity,
                orderedPrice, result -> {
                    if (result.isSuccess()) {
                        messages.send(player, "order-created", Map.of(
                                "quantity", String.valueOf(orderedQuantity),
                                "item", orderedItem.name().toLowerCase(),
                                "price", PriceFormatter.format(orderedPrice, config.getCurrencySymbol())));
                    } else {
                        sendFailure(result.getMessageKey(), orderedPrice);
                    }
                });

        if (!submitted) {
            messages.send(player, "queue-full");
        }
    }

    private void sendFailure(String messageKey, long orderedPrice) {
        String key = messageKey != null ? messageKey : "internal-error";
        switch (key) {
            case "not-enough-funds" -> messages.send(player, key, Map.of(
                    "price", PriceFormatter.format(orderedPrice, config.getCurrencySymbol())));
            case "max-orders" -> {
                int limit = auctionService.resolveOrderLimit(player);
                messages.send(player, key, Map.of(
                        "max", limit == -1 ? "∞" : String.valueOf(limit)));
            }
            case "min-price" -> messages.send(player, key, Map.of(
                    "min", PriceFormatter.format(config.getMinPrice(), config.getCurrencySymbol())));
            case "max-price" -> messages.send(player, key, Map.of(
                    "max", PriceFormatter.format(config.getMaxPrice(), config.getCurrencySymbol())));
            default -> messages.send(player, key);
        }
    }

    private ItemStack makeItem(Material mat, String name) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(name));
        stack.setItemMeta(meta);
        return stack;
    }
}
