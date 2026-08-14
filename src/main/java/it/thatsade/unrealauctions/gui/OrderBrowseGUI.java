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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrderBrowseGUI extends PaginatedGUI {

    private static final int SLOT_PREV_PAGE = 45;
    // Action buttons sit in the middle three slots of the control row.
    private static final int SLOT_CREATE = 48;
    private static final int SLOT_RETURN = 49;
    private static final int SLOT_MY_ORDERS = 50;
    private static final int SLOT_NEXT_PAGE = 53;

    private final AuctionService auctionService;
    private final Config config;
    private final OrderDraftService draftService;
    private final Map<Integer, Order> slotToOrder = new HashMap<>();
    private boolean loading;

    public OrderBrowseGUI(Player player, Messages messages, AuctionService auctionService,
                          Config config, OrderDraftService draftService) {
        super(player, messages);
        this.auctionService = auctionService;
        this.config = config;
        this.draftService = draftService;
    }

    @Override
    protected String getTitleKey() {
        return "gui-orders-title";
    }

    @Override
    protected void populate() {
        slotToOrder.clear();
        loading = true;

        int perPage = config.getItemsPerPage();
        auctionService.fetchOpenOrderCount(count -> {
            if (!isOpen()) return;
            totalPages = Math.max(1, (int) Math.ceil((double) count / perPage));
            if (currentPage >= totalPages) currentPage = totalPages - 1;

            auctionService.fetchOpenOrders(currentPage, perPage, orders -> {
                if (!isOpen()) return;
                for (int i = 0; i < CONTENT_SLOTS; i++) inventory.setItem(i, null);

                for (int i = 0; i < orders.size() && i < CONTENT_SLOTS; i++) {
                    Order order = orders.get(i);
                    inventory.setItem(i, buildOrderDisplay(order));
                    slotToOrder.put(i, order);
                }

                fillBottomRow();
                loading = false;
            });
        });
    }

    private void fillBottomRow() {
        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = 45; s <= 53; s++) inventory.setItem(s, filler);

        if (currentPage > 0) {
            inventory.setItem(SLOT_PREV_PAGE, buildNavItem(Material.ARROW,
                    messages.getNoPrefix("btn-prev-page"), true));
        }
        if (currentPage < totalPages - 1) {
            inventory.setItem(SLOT_NEXT_PAGE, buildNavItem(Material.ARROW,
                    messages.getNoPrefix("btn-next-page"), true));
        }

        inventory.setItem(SLOT_CREATE, buildItem(Material.WRITABLE_BOOK,
                messages.getNoPrefix("btn-create-order")));
        inventory.setItem(SLOT_RETURN, buildItem(Material.ARROW,
                messages.getNoPrefix("btn-return")));
        inventory.setItem(SLOT_MY_ORDERS, buildItem(Material.BOOK,
                messages.getNoPrefix("btn-my-orders")));
    }

    private ItemStack buildOrderDisplay(Order order) {
        Material material = order.getMaterial();
        ItemStack display = material != null
                ? new ItemStack(material, Math.min(order.getQuantity(), material.getMaxStackSize()))
                : new ItemStack(Material.BARRIER);
        ItemMeta meta = display.getItemMeta();

        List<Component> lore = new ArrayList<>();
        lore.add(noItalic(messages.getNoPrefix("lore-order-by",
                Map.of("player", order.getCreatorName()))));
        lore.add(noItalic(messages.getNoPrefix("lore-order-quantity",
                Map.of("quantity", String.valueOf(order.getQuantity())))));
        lore.add(noItalic(messages.getNoPrefix("lore-order-offer",
                Map.of("price", PriceFormatter.format(order.getTotalPrice(), config.getCurrencySymbol())))));
        lore.add(noItalic(messages.getNoPrefix("lore-time-left",
                Map.of("time", TimeFormatter.formatDuration(order.getMillisRemaining())))));

        boolean canFill = material != null
                && auctionService.countMatchingItems(player, material) >= order.getQuantity();
        if (canFill) {
            lore.add(noItalic(messages.getNoPrefix("lore-click-fulfil")));
        } else {
            lore.add(noItalic(messages.getNoPrefix("lore-order-need-items", Map.of(
                    "quantity", String.valueOf(order.getQuantity()),
                    "item", material != null ? material.name().toLowerCase() : order.getItemType()))));
        }

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (loading) return;

        int slot = event.getRawSlot();
        if (slot < 0 || slot > 53) return;

        if (slot == SLOT_PREV_PAGE && currentPage > 0) { currentPage--; refresh(); return; }
        if (slot == SLOT_NEXT_PAGE && currentPage < totalPages - 1) { currentPage++; refresh(); return; }
        if (slot == SLOT_RETURN) {
            new MainBrowseGUI(player, messages, auctionService, config).open();
            return;
        }
        if (slot == SLOT_CREATE) {
            new OrderCreationGUI(player, messages, auctionService, config, draftService).open();
            return;
        }
        if (slot == SLOT_MY_ORDERS) {
            new MyOrdersGUI(player, messages, auctionService, config, draftService).open();
            return;
        }

        Order order = slotToOrder.get(slot);
        if (order == null || event.getClick() != ClickType.LEFT) return;

        new OrderFulfillConfirmGUI(player, messages, auctionService, config, draftService, order).open();
    }
}
