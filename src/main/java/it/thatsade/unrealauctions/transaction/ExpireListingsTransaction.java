package it.thatsade.unrealauctions.transaction;

import it.thatsade.unrealauctions.model.CollectionItem;
import it.thatsade.unrealauctions.model.CollectionSource;
import it.thatsade.unrealauctions.model.Listing;
import it.thatsade.unrealauctions.model.ListingStatus;
import it.thatsade.unrealauctions.storage.*;

import java.sql.Connection;
import java.util.List;
import java.util.UUID;

public class ExpireListingsTransaction extends Transaction {

    private static final int BATCH_SIZE = 100;

    private final ListingRepository listingRepo;
    private final CollectionRepository collectionRepo;
    private final long collectionExpiryMillis;

    public ExpireListingsTransaction(DatabaseManager db, TransactionLogRepository logRepo,
                                     ListingRepository listingRepo, CollectionRepository collectionRepo,
                                     long collectionExpiryMillis) {
        super(db, logRepo);
        this.listingRepo = listingRepo;
        this.collectionRepo = collectionRepo;
        this.collectionExpiryMillis = collectionExpiryMillis;
    }

    @Override
    protected TransactionResult doExecute(Connection conn) throws Exception {
        List<Listing> expired = listingRepo.findExpired(conn);
        if (expired.isEmpty()) return TransactionResult.success(0);

        long now = System.currentTimeMillis();
        int count = 0;

        // Process in batches to avoid long-held locks
        for (int i = 0; i < expired.size(); i += BATCH_SIZE) {
            List<Listing> batch = expired.subList(i, Math.min(i + BATCH_SIZE, expired.size()));
            for (Listing listing : batch) {
                listingRepo.updateStatus(conn, listing.getId(), ListingStatus.EXPIRED, null, null);

                CollectionItem returned = CollectionItem.builder()
                        .id(UUID.randomUUID())
                        .ownerUuid(listing.getSellerUuid())
                        .item(listing.getItem())
                        .itemData(listing.getItemData())
                        .source(CollectionSource.EXPIRY)
                        .sourceListingId(listing.getId())
                        .addedAt(now)
                        .expiresAt(now + collectionExpiryMillis)
                        .build();
                collectionRepo.insert(conn, returned);
                count++;
            }
        }

        if (count > 0) {
            log.info("Expired " + count + " auction listing(s).");
        }
        return TransactionResult.success(count);
    }
}
