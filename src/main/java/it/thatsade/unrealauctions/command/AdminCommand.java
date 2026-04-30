package it.thatsade.unrealauctions.command;

import it.thatsade.unrealauctions.config.Config;
import it.thatsade.unrealauctions.config.Messages;
import it.thatsade.unrealauctions.service.AuctionService;
import it.thatsade.unrealauctions.storage.DatabaseManager;
import org.bukkit.entity.Player;

import java.util.Map;

public class AdminCommand {

    private final AuctionService auctionService;
    private final Messages messages;
    private final Config config;
    private final DatabaseManager db;

    public AdminCommand(AuctionService auctionService, Messages messages,
                        Config config, DatabaseManager db) {
        this.auctionService = auctionService;
        this.messages = messages;
        this.config = config;
        this.db = db;
    }

    public boolean handle(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(messages.getNoPrefix("prefix") + "&7Usage: /ah admin <stats|reload>");
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "stats" -> handleStats(player);
            case "reload" -> handleReload(player);
            default -> {
                player.sendMessage(messages.getNoPrefix("prefix") + "&7Unknown admin subcommand.");
                yield true;
            }
        };
    }

    private boolean handleStats(Player player) {
        int queueDepth = auctionService.getQueueDepth();
        String dbType = db.getType().name();

        auctionService.fetchActiveListingCount(count -> {
            messages.send(player, "admin-stats",
                    Map.of("queue", String.valueOf(queueDepth),
                            "listings", String.valueOf(count),
                            "db", dbType));
        });
        return true;
    }

    private boolean handleReload(Player player) {
        config.reload();
        messages.reload();
        messages.send(player, "admin-reloaded");
        return true;
    }
}
