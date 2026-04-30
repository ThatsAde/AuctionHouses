package it.thatsade.unrealauctions.transaction;

import it.thatsade.unrealauctions.economy.VaultEconomy;
import it.thatsade.unrealauctions.model.CollectionItem;
import it.thatsade.unrealauctions.model.CollectionSource;
import it.thatsade.unrealauctions.model.Listing;
import it.thatsade.unrealauctions.model.ListingStatus;
import it.thatsade.unrealauctions.storage.*;

import java.sql.Connection;
import java.util.UUID;

public class CancelListingTransaction extends Transaction {

    private final ListingRepository listingRepo;
    private final CollectionRepository collectionRepo;
    private final VaultEconomy economy;
    private final UUID requesterUuid;
    private final UUID listingId;
    private final boolean isAdmin;
    private final double cancelFeePercent;
    private final long collectionExpiryMillis;

    private Listing listing;
    private long cancelFee;

    public CancelListingTransaction(DatabaseManager db, TransactionLogRepository logRepo,
                                    ListingRepository listingRepo, CollectionRepository collectionRepo,
                                    VaultEconomy economy, UUID requesterUuid, UUID listingId,
                                    boolean isAdmin, double cancelFeePercent, long collectionExpiryMillis) {
        super(db, logRepo);
        this.listingRepo = listingRepo;
        this.collectionRepo = collectionRepo;
        this.economy = economy;
        this.requesterUuid = requesterUuid;
        this.listingId = listingId;
        this.isAdmin = isAdmin;
        this.cancelFeePercent = cancelFeePercent;
        this.collectionExpiryMillis = collectionExpiryMillis;
    }

    @Override
    protected TransactionResult doExecute(Connection conn) throws Exception {
        var opt = listingRepo.findById(conn, listingId);
        if (opt.isEmpty() || opt.get().getStatus() != ListingStatus.ACTIVE) {
            return TransactionResult.failure("listing-not-found");
        }
        listing = opt.get();

        if (!isAdmin && !listing.getSellerUuid().equals(requesterUuid)) {
            return TransactionResult.failure("no-permission");
        }

        cancelFee = Math.round(listing.getPrice() * cancelFeePercent / 100.0);
        if (cancelFee > 0 && !economy.hasBalance(listing.getSellerUuid(), cancelFee)) {
            return TransactionResult.failure("not-enough-funds");
        }

        listingRepo.updateStatus(conn, listingId, ListingStatus.CANCELLED, null, null);

        long now = System.currentTimeMillis();
        CollectionItem returned = CollectionItem.builder()
                .id(UUID.randomUUID())
                .ownerUuid(listing.getSellerUuid())
                .item(listing.getItem())
                .itemData(listing.getItemData())
                .source(CollectionSource.CANCELLATION)
                .sourceListingId(listingId)
                .addedAt(now)
                .expiresAt(now + collectionExpiryMillis)
                .build();
        collectionRepo.insert(conn, returned);

        return TransactionResult.success(listing);
    }

    @Override
    protected TransactionResult executePostCommit(TransactionResult dbResult) {
        if (cancelFee > 0) {
            economy.withdraw(listing.getSellerUuid(), cancelFee);
        }

        try (Connection conn = db.getConnection()) {
            logRepo.log(conn, "CANCEL_LISTING", requesterUuid, listingId, null, "SUCCESS", null);
        } catch (Exception e) {
            log.warning("Failed to write transaction log: " + e.getMessage());
        }

        return dbResult;
    }
}
