package it.thatsade.unrealauctions.gui;

import it.thatsade.unrealauctions.config.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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

public abstract class PaginatedGUI implements InventoryHolder {

    protected final Player player;
    protected final Messages messages;
    protected Inventory inventory;
    protected int currentPage = 0;
    protected int totalPages = 1;

    // Content slots: 0-44 (5 rows × 9)
    public static final int CONTENT_SLOTS = 45;
    // Control row: 45-53
    public static final int SLOT_PREV = 45;
    public static final int SLOT_FILTER = 46;
    public static final int SLOT_SORT = 47;
    public static final int SLOT_INFO = 48;
    public static final int SLOT_SEARCH = 49;
    public static final int SLOT_SPACER = 50;
    public static final int SLOT_MY_LISTINGS = 51;
    public static final int SLOT_COLLECTION = 52;
    public static final int SLOT_NEXT = 53;

    protected PaginatedGUI(Player player, Messages messages) {
        this.player = player;
        this.messages = messages;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    protected abstract String getTitleKey();
    protected abstract void populate();

    public void open() {
        String titleStr = messages.getNoPrefix(getTitleKey(),
                Map.of("page", String.valueOf(currentPage + 1),
                        "total", String.valueOf(totalPages)));
        Component title = LegacyComponentSerializer.legacySection().deserialize(titleStr);
        inventory = Bukkit.createInventory(this, 54, title);
        clearContent();
        populate();
        player.openInventory(inventory);
    }

    public void refresh() {
        if (!isOpen()) return;
        clearContent();
        populate();
    }

    protected boolean isOpen() {
        return player.isOnline()
                && player.getOpenInventory().getTopInventory().equals(inventory);
    }

    protected void clearContent() {
        for (int i = 0; i < 54; i++) inventory.setItem(i, null);
        fillControls();
    }

    protected void fillControls() {
        // Solid glass backing across the whole control row, so no slot ever shows
        // as an empty hole while the page loads.
        ItemStack filler = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int s = SLOT_PREV; s <= SLOT_NEXT; s++) inventory.setItem(s, filler);

        // Navigation arrows only appear when there is somewhere to go.
        if (currentPage > 0) {
            inventory.setItem(SLOT_PREV, buildNavItem(Material.ARROW,
                    messages.getNoPrefix("btn-prev-page"), true));
        }
        if (currentPage < totalPages - 1) {
            inventory.setItem(SLOT_NEXT, buildNavItem(Material.ARROW,
                    messages.getNoPrefix("btn-next-page"), true));
        }
    }

    public abstract void handleClick(InventoryClickEvent event);

    /**
     * Deserializes a legacy-formatted string and wraps it with italic=false,
     * so Minecraft's default italic on custom names is suppressed.
     * Use &o in the message string to opt into italic explicitly.
     */
    public static Component noItalic(String legacyText) {
        return Component.empty()
                .decoration(TextDecoration.ITALIC, false)
                .append(LegacyComponentSerializer.legacySection().deserialize(legacyText));
    }

    protected ItemStack buildNavItem(Material mat, String name, boolean enabled) {
        ItemStack item = new ItemStack(enabled ? mat : Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(name));
        item.setItemMeta(meta);
        return item;
    }

    protected ItemStack buildItem(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(name));
        if (loreLines.length > 0) {
            meta.lore(List.of(loreLines).stream()
                    .map(l -> noItalic(l))
                    .toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    public Player getPlayer() {
        return player;
    }

    public int getCurrentPage() {
        return currentPage;
    }
}
