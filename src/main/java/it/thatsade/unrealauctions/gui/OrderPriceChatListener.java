package it.thatsade.unrealauctions.gui;

import io.papermc.paper.event.player.AsyncChatEvent;
import it.thatsade.unrealauctions.config.Config;
import it.thatsade.unrealauctions.config.Messages;
import it.thatsade.unrealauctions.util.PriceFormatter;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;

import java.util.Map;

/**
 * Reads the offer for an order draft from chat. Chat is used here rather than a
 * command because a price needs no autocomplete — only the item picker does,
 * which is why that half stays on /ah order.
 */
public class OrderPriceChatListener implements Listener {

    private final Plugin plugin;
    private final OrderDraftService draftService;
    private final Messages messages;
    private final Config config;

    public OrderPriceChatListener(Plugin plugin, OrderDraftService draftService,
                                  Messages messages, Config config) {
        this.plugin = plugin;
        this.draftService = draftService;
        this.messages = messages;
        this.config = config;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!draftService.isAwaitingPrice(player)) return;

        // Swallow the line so the player's offer never hits public chat.
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText()
                .serialize(event.message()).trim();

        // This fires off the main thread; opening the GUI must not.
        plugin.getServer().getScheduler().runTask(plugin, () -> apply(player, input));
    }

    private void apply(Player player, String input) {
        OrderCreationGUI draft = draftService.get(player);
        if (draft == null) {
            // Draft vanished while they were typing (confirmed, cancelled, quit).
            draftService.stopAwaitingPrice(player);
            return;
        }

        if (input.equalsIgnoreCase("cancel")) {
            draftService.stopAwaitingPrice(player);
            messages.send(player, "order-input-cancelled");
            draft.open();
            return;
        }

        long price;
        try {
            price = PriceFormatter.parsePriceArg(input);
        } catch (NumberFormatException e) {
            // Stay in capture mode so they can simply type another number.
            messages.send(player, "invalid-price");
            return;
        }

        if (price < config.getMinPrice()) {
            messages.send(player, "min-price", Map.of("min",
                    PriceFormatter.format(config.getMinPrice(), config.getCurrencySymbol())));
            return;
        }
        if (price > config.getMaxPrice()) {
            messages.send(player, "max-price", Map.of("max",
                    PriceFormatter.format(config.getMaxPrice(), config.getCurrencySymbol())));
            return;
        }

        draftService.stopAwaitingPrice(player);
        draft.setPrice(price);
        draft.open();
    }
}
