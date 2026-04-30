package it.thatsade.unrealauctions.transaction;

import it.thatsade.unrealauctions.economy.VaultEconomy;
import it.thatsade.unrealauctions.model.Listing;
import it.thatsade.unrealauctions.model.ListingStatus;
import it.thatsade.unrealauctions.storage.*;

import java.sql.Connection;
import java.util.UUID;

public class BuyItemTransaction extends Transaction {

    private final ListingRepository listingRepo;
    private final PlayerStatsRepository statsRepo;
    private final VaultEconomy economy;
    private final UUID buyerUuid;
    private final UUID listingId;

    private Listing listing;

    public BuyItemTransaction(DatabaseManager db, TransactionLogRepository logRepo,
                              ListingRepository listingRepo, PlayerStatsRepository statsRepo,
                              VaultEconomy economy, UUID buyerUuid, UUID listingId) {
        super(db, logRepo);
        this.listingRepo = listingRepo;
        this.statsRepo = statsRepo;
        this.economy = economy;
        this.buyerUuid = buyerUuid;
        this.listingId = listingId;
    }

    @Override
    protected TransactionResult doExecute(Connection conn) throws Exception {
        var opt = listingRepo.findById(conn, listingId);
        if (opt.isEmpty() || opt.get().getStatus() != ListingStatus.ACTIVE) {
            return TransactionResult.failure("listing-not-found");
        }
        listing = opt.get();

        if (listing.isExpired()) {
            return TransactionResult.failure("listing-expired-buy");
        }
        if (listing.getSellerUuid().equals(buyerUuid)) {
            return TransactionResult.failure("cannot-buy-own");
        }
        if (!economy.hasBalance(buyerUuid, listing.getPrice())) {
            return TransactionResult.failure("not-enough-funds");
        }

        listingRepo.updateStatus(conn, listingId, ListingStatus.SOLD, buyerUuid, System.currentTimeMillis());
        statsRepo.incrementPurchase(conn, buyerUuid, listing.getPrice());
        statsRepo.incrementSale(conn, listing.getSellerUuid(), listing.getPrice());

        return TransactionResult.success(listing);
    }

    @Override
    protected TransactionResult executePostCommit(TransactionResult dbResult) {
        long price = listing.getPrice();

        if (!economy.withdraw(buyerUuid, price)) {
            log.severe("ECONOMY ERROR: Failed to withdraw " + price + " from buyer " + buyerUuid
                    + " for listing " + listingId + ". DB already committed!");
            try (Connection conn = db.getConnection()) {
                logRepo.log(conn, "BUY_ITEM", buyerUuid, listingId, price, "ECONOMY_FAIL",
                        "Buyer withdraw failed post-commit");
            } catch (Exception e) {
                log.warning("Failed to write transaction log: " + e.getMessage());
            }
            return dbResult;
        }

        if (!economy.deposit(listing.getSellerUuid(), price)) {
            log.severe("ECONOMY ERROR: Failed to deposit " + price + " to seller "
                    + listing.getSellerUuid() + " for listing " + listingId + ". Refunding buyer.");
            economy.deposit(buyerUuid, price);
        }

        try (Connection conn = db.getConnection()) {
            logRepo.log(conn, "BUY_ITEM", buyerUuid, listingId, price, "SUCCESS", null);
        } catch (Exception e) {
            log.warning("Failed to write transaction log: " + e.getMessage());
        }

        return dbResult;
    }
}
