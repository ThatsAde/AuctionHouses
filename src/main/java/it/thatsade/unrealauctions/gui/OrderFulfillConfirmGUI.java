package it.thatsade.unrealauctions.gui;

import it.thatsade.unrealauctions.config.Config;
import it.thatsade.unrealauctions.config.Messages;
import it.thatsade.unrealauctions.model.Order;
import it.thatsade.unrealauctions.service.AuctionService;
import it.thatsade.unrealauctions.util.PriceFormatter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

import static it.thatsade.unrealauctions.gui.PaginatedGUI.noItalic;

public class OrderFulfillConfirmGUI implements InventoryHolder {

    private static final int SLOT_CONFIRM = 11;
    private static final int SLOT_PREVIEW = 13;
    private static final int SLOT_CANCEL = 15;

    private final Player player;
    private final Messages messages;
    private final AuctionService auctionService;
    private final Config config;
    private final OrderDraftService draftService;
    private final Order order;
    private Inventory inventory;

    public OrderFulfillConfirmGUI(Player player, Messages messages, AuctionService auctionService,
                                  Config config, OrderDraftService draftService, Order order) {
        this.player = player;
        this.messages = messages;
        this.auctionService = auctionService;
        this.config = config;
        this.draftService = draftService;
        this.order = order;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        Component title = LegacyComponentSerializer.legacySection().deserialize(
                messages.getNoPrefix("gui-order-fulfil-title"));
        inventory = Bukkit.createInventory(this, 27, title);

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler);

        Material material = order.getMaterial();
        ItemStack preview = material != null
                ? new ItemStack(material, Math.min(order.getQuantity(), material.getMaxStackSize()))
                : new ItemStack(Material.BARRIER);
        ItemMeta meta = preview.getItemMeta();
        meta.lore(List.of(
                noItalic(messages.getNoPrefix("lore-order-by",
                        Map.of("player", order.getCreatorName()))),
                noItalic(messages.getNoPrefix("lore-order-quantity",
                        Map.of("quantity", String.valueOf(order.getQuantity())))),
                noItalic(messages.getNoPrefix("lore-order-offer",
                        Map.of("price", PriceFormatter.format(order.getTotalPrice(), config.getCurrencySymbol()))))
        ));
        preview.setItemMeta(meta);
        inventory.setItem(SLOT_PREVIEW, preview);

        inventory.setItem(SLOT_CONFIRM, makeItem(Material.LIME_CONCRETE,
                messages.getNoPrefix("btn-confirm")));
        inventory.setItem(SLOT_CANCEL, makeItem(Material.RED_CONCRETE,
                messages.getNoPrefix("btn-cancel")));

        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (slot == SLOT_CONFIRM) {
            Material material = order.getMaterial();
            if (material == null
                    || auctionService.countMatchingItems(player, material) < order.getQuantity()) {
                messages.send(player, "order-missing-items", Map.of(
                        "quantity", String.valueOf(order.getQuantity()),
                        "item", material != null ? material.name().toLowerCase() : order.getItemType()));
                return;
            }

            player.closeInventory();
            boolean submitted = auctionService.fulfillOrder(player, order, result -> {
                if (result.isSuccess()) {
                    messages.send(player, "order-fulfilled", Map.of(
                            "price", PriceFormatter.format(order.getTotalPrice(), config.getCurrencySymbol())));
                    notifyCreator(material);
                } else {
                    String key = result.getMessageKey() != null ? result.getMessageKey() : "internal-error";
                    if ("order-missing-items".equals(key)) {
                        messages.send(player, key, Map.of(
                                "quantity", String.valueOf(order.getQuantity()),
                                "item", material != null ? material.name().toLowerCase() : order.getItemType()));
                    } else {
                        messages.send(player, key);
                    }
                }
            });

            if (!submitted) {
                messages.send(player, "queue-full");
            }
        } else if (slot == SLOT_CANCEL) {
            player.closeInventory();
            new OrderBrowseGUI(player, messages, auctionService, config, draftService).open();
        }
    }

    private void notifyCreator(Material material) {
        Player creator = Bukkit.getPlayer(order.getCreatorUuid());
        if (creator == null || !creator.isOnline()) return;
        messages.send(creator, "order-filled-notify", Map.of(
                "quantity", String.valueOf(order.getQuantity()),
                "item", material != null ? material.name().toLowerCase() : order.getItemType()));
    }

    private ItemStack makeItem(Material mat, String name) {
        ItemStack stack = new ItemStack(mat);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(noItalic(name));
        stack.setItemMeta(meta);
        return stack;
    }
}
