package it.thatsade.unrealauctions;

import it.thatsade.unrealauctions.command.AdminCommand;
import it.thatsade.unrealauctions.command.AuctionCommand;
import it.thatsade.unrealauctions.config.Config;
import it.thatsade.unrealauctions.config.Messages;
import it.thatsade.unrealauctions.economy.VaultEconomy;
import it.thatsade.unrealauctions.gui.GUIListener;
import it.thatsade.unrealauctions.gui.OrderDraftService;
import it.thatsade.unrealauctions.gui.OrderPriceChatListener;
import it.thatsade.unrealauctions.service.AuctionService;
import it.thatsade.unrealauctions.service.FeeCalculator;
import it.thatsade.unrealauctions.service.RateLimiter;
import it.thatsade.unrealauctions.service.ValidationService;
import it.thatsade.unrealauctions.storage.*;
import it.thatsade.unrealauctions.transaction.ExpireListingsTransaction;
import it.thatsade.unrealauctions.transaction.ExpireOrdersTransaction;
import it.thatsade.unrealauctions.transaction.TransactionQueue;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;

public class UnrealAuctionsPlugin extends JavaPlugin {

    private Config config;
    private Messages messages;
    private DatabaseManager databaseManager;
    private TransactionQueue transactionQueue;
    private OrderDraftService orderDraftService;

    public OrderDraftService getOrderDraftService() {
        return orderDraftService;
    }

    @Override
    public void onEnable() {
        // Step 1: Config + messages
        config = new Config(this);
        messages = new Messages(this);

        // Step 2: Database
        databaseManager = new DatabaseManager();
        try {
            databaseManager.initialize(config, getDataFolder());
        } catch (SQLException e) {
            getLogger().severe("Failed to connect to database: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Step 3: Schema migration
        SchemaMigration migration = new SchemaMigration(databaseManager);
        try {
            migration.run();
        } catch (SQLException e) {
            getLogger().severe("Database schema migration failed: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Step 4: Repositories
        ListingRepository listingRepo = new ListingRepository(databaseManager);
        CollectionRepository collectionRepo = new CollectionRepository(databaseManager);
        OrderRepository orderRepo = new OrderRepository(databaseManager);
        TransactionLogRepository logRepo = new TransactionLogRepository(databaseManager);
        PlayerStatsRepository statsRepo = new PlayerStatsRepository(databaseManager);

        // Step 5: Economy
        VaultEconomy economy = new VaultEconomy();
        if (!economy.initialize(this)) {
            getLogger().severe("Failed to hook into an economy provider. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Step 6: Services
        ValidationService validator = new ValidationService(config);
        RateLimiter rateLimiter = new RateLimiter(config);
        FeeCalculator feeCalculator = new FeeCalculator(
                config.getListingFeePercent(), config.getCancelFeePercent());

        // Step 7: Transaction queue
        transactionQueue = new TransactionQueue(this, config.getMaxQueueDepth());

        // Step 8: Auction service
        AuctionService auctionService = new AuctionService(
                this, config, transactionQueue, databaseManager,
                listingRepo, collectionRepo, orderRepo, logRepo, statsRepo,
                economy, validator, rateLimiter
        );

        // Step 9: Commands
        AdminCommand adminCmd = new AdminCommand(auctionService, messages, config, databaseManager);
        AuctionCommand auctionCmd = new AuctionCommand(auctionService, messages, config, adminCmd);

        var ahCmd = getCommand("ah");
        if (ahCmd != null) {
            ahCmd.setExecutor(auctionCmd);
            ahCmd.setTabCompleter(auctionCmd);
        }

        // Step 10: GUI listener + order draft tracking
        getServer().getPluginManager().registerEvents(new GUIListener(), this);
        orderDraftService = new OrderDraftService();
        getServer().getPluginManager().registerEvents(orderDraftService, this);
        getServer().getPluginManager().registerEvents(
                new OrderPriceChatListener(this, orderDraftService, messages, config), this);

        long collectionExpiryMillis = config.getCollectionBoxExpiryMillis();

        // Step 11: Listing expiry scheduler — runs every 60 seconds
        getServer().getScheduler().runTaskTimer(this, () -> {
            ExpireListingsTransaction expireTx = new ExpireListingsTransaction(
                    databaseManager, logRepo, listingRepo, collectionRepo, collectionExpiryMillis);
            transactionQueue.submit(expireTx, result -> {});

            if (config.isOrdersEnabled()) {
                ExpireOrdersTransaction expireOrdersTx = new ExpireOrdersTransaction(
                        databaseManager, logRepo, orderRepo, economy);
                transactionQueue.submit(expireOrdersTx, result -> {});
            }
        }, 20L * 60, 20L * 60);

        // Step 12: Collection box purge scheduler — runs every 5 minutes
        getServer().getScheduler().runTaskTimer(this,
                auctionService::purgeExpiredCollectionItems,
                20L * 60 * 5, 20L * 60 * 5);

        getLogger().info("AuctionHouse enabled successfully! Database: "
                + databaseManager.getType().name());
    }

    @Override
    public void onDisable() {
        if (config != null) {
            config.unregisterLimitPermissions();
        }

        // Drain the transaction queue (up to 10 seconds)
        if (transactionQueue != null) {
            transactionQueue.shutdown();
        }

        // Close DB connections
        if (databaseManager != null) {
            databaseManager.close();
        }

        getLogger().info("AuctionHouse disabled.");
    }
}
