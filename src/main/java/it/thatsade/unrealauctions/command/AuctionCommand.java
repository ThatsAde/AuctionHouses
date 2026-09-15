package it.thatsade.unrealauctions.command;

import it.thatsade.unrealauctions.UnrealAuctionsPlugin;
import it.thatsade.unrealauctions.config.Config;
import it.thatsade.unrealauctions.config.Messages;
import it.thatsade.unrealauctions.gui.CollectionBoxGUI;
import it.thatsade.unrealauctions.gui.MainBrowseGUI;
import it.thatsade.unrealauctions.gui.MyListingsGUI;
import it.thatsade.unrealauctions.gui.OrderBrowseGUI;
import it.thatsade.unrealauctions.gui.OrderCreationGUI;
import it.thatsade.unrealauctions.gui.OrderDraftService;
import it.thatsade.unrealauctions.service.AuctionService;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;
import it.thatsade.unrealauctions.util.PriceFormatter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AuctionCommand implements CommandExecutor, TabCompleter {

    private final AuctionService auctionService;
    private final Messages messages;
    private final Config config;
    private final AdminCommand adminCommand;

    public AuctionCommand(AuctionService auctionService, Messages messages,
                          Config config, AdminCommand adminCommand) {
        this.auctionService = auctionService;
        this.messages = messages;
        this.config = config;
        this.adminCommand = adminCommand;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.getNoPrefix("player-only"));
            return true;
        }

        if (!player.hasPermission("unrealauctions.use")) {
            messages.send(player, "no-permission");
            return true;
        }

        if (args.length == 0) {
            new MainBrowseGUI(player, messages, auctionService, config).open();
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "sell" -> handleSell(player, args);
            case "collect" -> handleCollect(player);
            case "cancel" -> handleCancel(player);
            case "orders" -> handleOrders(player);
            case "order" -> handleOrderItem(player, args);
            case "orderprice" -> handleOrderPrice(player, args);
            case "admin" -> handleAdmin(player, args);
            case "help" -> handleHelp(player);
            default -> {
                messages.send(player, "unknown-subcommand");
                yield true;
            }
        };
    }

    private boolean handleSell(Player player, String[] args) {
        if (args.length < 2 || args.length > 3) {
            player.sendMessage(messages.color(messages.getRaw("prefix")
                    + "&7Usage: /ah sell <price> [amount]"));
            return true;
        }

        long price;
        try {
            price = PriceFormatter.parsePriceArg(args[1]);
        } catch (NumberFormatException e) {
            messages.send(player, "invalid-price");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            messages.send(player, "no-item-in-hand");
            return true;
        }

        int amount = item.getAmount();
        if (args.length == 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                messages.send(player, "invalid-sell-amount",
                        Map.of("max", String.valueOf(item.getAmount())));
                return true;
            }
        }
        if (amount < 1 || amount > item.getAmount()) {
            messages.send(player, "invalid-sell-amount",
                    Map.of("max", String.valueOf(item.getAmount())));
            return true;
        }

        boolean submitted = auctionService.listItem(player, item, amount, price, result -> {
            if (result.isSuccess()) {
                messages.send(player, "listing-created",
                        Map.of("price", PriceFormatter.format(price, config.getCurrencySymbol()),
                                "fee", PriceFormatter.format(
                                        Math.round(price * config.getListingFeePercent() / 100.0),
                                        config.getCurrencySymbol())));
            } else {
                String key = result.getMessageKey() != null ? result.getMessageKey() : "internal-error";
                if ("min-price".equals(key)) {
                    messages.send(player, key,
                            Map.of("min", PriceFormatter.format(config.getMinPrice(), config.getCurrencySymbol())));
                } else if ("max-price".equals(key)) {
                    messages.send(player, key,
                            Map.of("max", PriceFormatter.format(config.getMaxPrice(), config.getCurrencySymbol())));
                } else if ("max-listings".equals(key)) {
                    int effectiveLimit = auctionService.resolveListingLimit(player);
                    String limitStr = effectiveLimit == -1 ? "∞" : String.valueOf(effectiveLimit);
                    messages.send(player, key, Map.of("max", limitStr));
                } else if ("cooldown".equals(key)) {
                    messages.send(player, key, Map.of("seconds", "?"));
                } else {
                    messages.send(player, key);
                }
            }
        });

        if (!submitted) {
            messages.send(player, "queue-full");
        }
        return true;
    }

    private boolean handleCollect(Player player) {
        new CollectionBoxGUI(player, messages, auctionService, config).open();
        return true;
    }

    private boolean handleCancel(Player player) {
        new MyListingsGUI(player, messages, auctionService, config).open();
        return true;
    }

    private boolean handleOrders(Player player) {
        if (!config.isOrdersEnabled()) {
            messages.send(player, "orders-disabled");
            return true;
        }
        if (!player.hasPermission("unrealauctions.orders")) {
            messages.send(player, "no-permission");
            return true;
        }
        new OrderBrowseGUI(player, messages, auctionService, config, draftService()).open();
        return true;
    }

    private OrderDraftService draftService() {
        return JavaPlugin.getPlugin(UnrealAuctionsPlugin.class).getOrderDraftService();
    }

    /**
     * The player's in-progress draft, or a fresh one. Reusing an open draft means
     * running the command again keeps whatever they had already filled in.
     */
    private OrderCreationGUI orderDraft(Player player) {
        OrderCreationGUI draft = draftService().get(player);
        return draft != null ? draft
                : new OrderCreationGUI(player, messages, auctionService, config, draftService());
    }

    /**
     * Opens the order creation menu, with the item + quantity pre-filled when they
     * were given. Starts a fresh draft if the menu was never opened. The
     * orders-enabled and permission checks live here too, since this is an entry
     * point in its own right rather than a follow-up to /ah orders.
     */
    private boolean handleOrderItem(Player player, String[] args) {
        if (!config.isOrdersEnabled()) {
            messages.send(player, "orders-disabled");
            return true;
        }
        if (!player.hasPermission("unrealauctions.orders")) {
            messages.send(player, "no-permission");
            return true;
        }
        // Bare /ah order just opens the creation menu; the buttons explain themselves.
        if (args.length < 2) {
            orderDraft(player).open();
            return true;
        }

        Material material = Material.matchMaterial(args[1]);
        if (material == null || !material.isItem() || material.isAir()) {
            messages.send(player, "invalid-item", Map.of("input", args[1]));
            return true;
        }
        if (config.getBlacklist().contains(material)) {
            messages.send(player, "blacklisted-item");
            return true;
        }

        int quantity = 1;
        if (args.length > 2) {
            try {
                quantity = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                messages.send(player, "invalid-quantity",
                        Map.of("max", String.valueOf(config.getOrderMaxQuantity())));
                return true;
            }
        }
        if (quantity < 1 || quantity > config.getOrderMaxQuantity()) {
            messages.send(player, "invalid-quantity",
                    Map.of("max", String.valueOf(config.getOrderMaxQuantity())));
            return true;
        }

        OrderCreationGUI draft = orderDraft(player);
        draft.setItem(material, quantity);
        draft.open();
        return true;
    }

    /** Sets the offer on the order the player is currently drafting. */
    private boolean handleOrderPrice(Player player, String[] args) {
        OrderCreationGUI draft = draftService().get(player);
        if (draft == null) {
            messages.send(player, "no-active-order");
            return true;
        }
        if (args.length < 2) {
            player.sendMessage(messages.color(messages.getRaw("prefix")
                    + "&7Usage: /ah orderprice <amount>"));
            return true;
        }

        long price;
        try {
            price = PriceFormatter.parsePriceArg(args[1]);
        } catch (NumberFormatException e) {
            messages.send(player, "invalid-price");
            return true;
        }
        if (price < config.getMinPrice()) {
            messages.send(player, "min-price", Map.of("min",
                    PriceFormatter.format(config.getMinPrice(), config.getCurrencySymbol())));
            return true;
        }
        if (price > config.getMaxPrice()) {
            messages.send(player, "max-price", Map.of("max",
                    PriceFormatter.format(config.getMaxPrice(), config.getCurrencySymbol())));
            return true;
        }

        draft.setPrice(price);
        draft.open();
        return true;
    }

    private boolean handleAdmin(Player player, String[] args) {
        if (!player.hasPermission("unrealauctions.admin")) {
            messages.send(player, "no-permission");
            return true;
        }
        return adminCommand.handle(player, args);
    }

    private boolean handleHelp(Player player) {
        sendHelpLine(player, "&6/ah &7- Open auction house");
        sendHelpLine(player, "&6/ah sell <price> [amount] &7- List items from the held stack");
        sendHelpLine(player, "&6/ah collect &7- Collect purchased items");
        sendHelpLine(player, "&6/ah cancel &7- Cancel your listings");
        if (config.isOrdersEnabled()) {
            sendHelpLine(player, "&6/ah orders &7- Browse and create item orders");
            sendHelpLine(player, "&6/ah order <item> [amount] &7- Start an order for an item");
        }
        return true;
    }

    private void sendHelpLine(Player player, String line) {
        player.sendMessage(messages.color(messages.getRaw("prefix") + line));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("sell", "collect", "cancel", "orders", "order", "orderprice",
                            "help", "admin")
                    .stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && "sell".equalsIgnoreCase(args[0])) {
            return List.of("<price>");
        }
        if (args.length == 3 && "sell".equalsIgnoreCase(args[0])) {
            if (sender instanceof Player player) {
                int heldAmount = player.getInventory().getItemInMainHand().getAmount();
                return List.of(String.valueOf(heldAmount));
            }
            return List.of("<amount>");
        }
        // Feeds Minecraft's autocomplete dropdown with every listable item.
        if (args.length == 2 && "order".equalsIgnoreCase(args[0])) {
            return completeMaterials(args[1]);
        }
        if (args.length == 3 && "order".equalsIgnoreCase(args[0])) {
            return List.of("1", "16", "32", "64");
        }
        if (args.length == 2 && "orderprice".equalsIgnoreCase(args[0])) {
            return List.of("<amount>");
        }
        if (args.length == 2 && "admin".equalsIgnoreCase(args[0])) {
            return List.of("stats", "reload")
                    .stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return List.of();
    }

    /**
     * Suggests namespaced item ids (minecraft:diamond_block). Matches on either the
     * full id or the part after the colon, so typing "diamond" still finds it.
     */
    private List<String> completeMaterials(String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Material material : Material.values()) {
            if (material.isLegacy() || !material.isItem() || material.isAir()) continue;
            if (config.getBlacklist().contains(material)) continue;

            String id = material.getKey().toString();
            String path = material.getKey().getKey();
            if (id.startsWith(prefix) || path.startsWith(prefix)) {
                matches.add(id);
            }
        }
        return matches;
    }
}
