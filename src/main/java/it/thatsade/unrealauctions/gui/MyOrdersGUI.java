package it.thatsade.unrealauctions.gui;

import it.thatsade.unrealauctions.config.Config;
import it.thatsade.unrealauctions.config.Messages;
import it.thatsade.unrealauctions.model.Order;
import it.thatsade.unrealauctions.service.AuctionService;
import it.thatsade.unrealauctions.util.PriceFormatter;
import it.thatsade.unrealauctions.util.TimeFormatter;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MyOrdersGUI extends PaginatedGUI {

    private static final int SLOT_RETURN = 49;

    private final AuctionService auctionService;
    private final Config config;
    private final OrderDraftService draftService;
    private final Map<Integer, Order> slotToOrder = new HashMap<>();

    public MyOrdersGUI(Player player, Messages messages, AuctionService auctionService,
                       Config config, OrderDraftService draftService) {
        super(player, messages);
        this.auctionService = auctionService;
        this.config = config;
        this.draftService = draftService;
    }

    @Override
    protected String getTitleKey() {
        return "gui-my-orders-title";
    }

    @Override
    protected void populate() {
        slotToOrder.clear();
        auctionService.fetchPlayerOrders(player.getUniqueId(), orders -> {
            if (!isOpen()) return;
            totalPages = Math.max(1, (int) Math.ceil((double) orders.size() / CONTENT_SLOTS));
            clearContent();

            int start = currentPage * CONTENT_SLOTS;
            int end = Math.min(start + CONTENT_SLOTS, orders.size());
            for (int i = start; i < end; i++) {
                Order order = orders.get(i);
                int slot = i - start;
                inventory.setItem(slot, buildOrderDisplay(order));
                slotToOrder.put(slot, order);
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

    private ItemStack buildOrderDisplay(Order order) {
        Material material = order.getMaterial();
        ItemStack display = material != null
                ? new ItemStack(material, Math.min(order.getQuantity(), material.getMaxStackSize()))
                : new ItemStack(Material.BARRIER);
        ItemMeta meta = display.getItemMeta();

        List<Component> lore = new ArrayList<>();
        lore.add(noItalic(messages.getNoPrefix("lore-order-quantity",
                Map.of("quantity", String.valueOf(order.getQuantity())))));
        lore.add(noItalic(messages.getNoPrefix("lore-order-offer",
                Map.of("price", PriceFormatter.format(order.getTotalPrice(), config.getCurrencySymbol())))));
        lore.add(noItalic(messages.getNoPrefix("lore-time-left",
                Map.of("time", TimeFormatter.formatDuration(order.getMillisRemaining())))));
        lore.add(noItalic(messages.getNoPrefix("lore-right-click-cancel-order")));

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
            new OrderBrowseGUI(player, messages, auctionService, config, draftService).open();
            return;
        }

        Order order = slotToOrder.get(slot);
        if (order == null || !event.isRightClick()) return;

        player.closeInventory();
        auctionService.cancelOrder(player, order.getId(), result -> {
            if (result.isSuccess()) {
                messages.send(player, "order-cancelled", Map.of(
                        "price", PriceFormatter.format(order.getTotalPrice(), config.getCurrencySymbol())));
            } else {
                messages.send(player, result.getMessageKey() != null
                        ? result.getMessageKey() : "internal-error");
            }
        });
    }
}
