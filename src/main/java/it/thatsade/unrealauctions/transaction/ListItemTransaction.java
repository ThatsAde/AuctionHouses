package it.thatsade.unrealauctions.transaction;

import it.thatsade.unrealauctions.config.Config;
import it.thatsade.unrealauctions.economy.VaultEconomy;
import it.thatsade.unrealauctions.model.Listing;
import it.thatsade.unrealauctions.model.ListingStatus;
import it.thatsade.unrealauctions.model.ListingType;
import it.thatsade.unrealauctions.storage.DatabaseManager;
import it.thatsade.unrealauctions.storage.ListingRepository;
import it.thatsade.unrealauctions.storage.PlayerStatsRepository;
import it.thatsade.unrealauctions.storage.TransactionLogRepository;
import it.thatsade.unrealauctions.util.ItemSerializer;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ListItemTransaction extends Transaction {

    private final ListingRepository listingRepo;
    private final PlayerStatsRepository statsRepo;
    private final VaultEconomy economy;
    private final UUID sellerUuid;
    private final String sellerName;
    private final ItemStack item;
    private final long price;
    private final Config config;
    /** Effective max active listings for this player. -1 = unlimited. */
    private final int maxListings;

    private long feeCharged = 0;
    private Listing createdListing;

    public ListItemTransaction(DatabaseManager db, TransactionLogRepository logRepo,
                               ListingRepository listingRepo, PlayerStatsRepository statsRepo,
                               VaultEconomy economy, UUID sellerUuid, String sellerName,
                               ItemStack item, long price, Config config, int maxListings) {
        super(db, logRepo);
        this.listingRepo = listingRepo;
        this.statsRepo = statsRepo;
        this.economy = economy;
        this.sellerUuid = sellerUuid;
        this.sellerName = sellerName;
        this.item = item.clone();
        this.price = price;
        this.config = config;
        this.maxListings = maxListings;
    }

    @Override
    protected TransactionResult doExecute(Connection conn) throws Exception {
        // Check active listing count (maxListings == -1 means unlimited)
        int activeCount = listingRepo.countActiveByPlayer(conn, sellerUuid);
        if (maxListings != -1 && activeCount >= maxListings) {
            return TransactionResult.failure("max-listings");
        }

        // Calculate and validate listing fee
        feeCharged = Math.round(price * config.getListingFeePercent() / 100.0);
        if (feeCharged > 0 && !economy.hasBalance(sellerUuid, feeCharged)) {
            return TransactionResult.failure("not-enough-funds");
        }

        // Build and insert listing
        long now = System.currentTimeMillis();
        long expires = now + TimeUnit.HOURS.toMillis(config.getDefaultDurationHours());
        byte[] itemData = ItemSerializer.serialize(item);

        createdListing = Listing.builder()
                .id(UUID.randomUUID())
                .sellerUuid(sellerUuid)
                .sellerName(sellerName)
                .item(item)
                .itemData(itemData)
                .itemType(item.getType().name())
                .price(price)
                .listingType(ListingType.BUY_NOW)
                .status(ListingStatus.ACTIVE)
                .createdAt(now)
                .expiresAt(expires)
                .build();

        listingRepo.insert(conn, createdListing);
        statsRepo.incrementListings(conn, sellerUuid);

        return TransactionResult.success(createdListing);
    }

    @Override
    protected TransactionResult executePostCommit(TransactionResult dbResult) {
        // Charge listing fee after DB commit
        if (feeCharged > 0) {
            if (!economy.withdraw(sellerUuid, feeCharged)) {
                log.warning("Failed to charge listing fee of " + feeCharged + " to " + sellerUuid
                        + " for listing " + createdListing.getId() + ". Fee skipped.");
            }
        }

        try (Connection conn = db.getConnection()) {
            logRepo.log(conn, "LIST_ITEM", sellerUuid, createdListing.getId(),
                    price, "SUCCESS", null);
        } catch (Exception e) {
            log.warning("Failed to write transaction log: " + e.getMessage());
        }

        return dbResult;
    }
}
