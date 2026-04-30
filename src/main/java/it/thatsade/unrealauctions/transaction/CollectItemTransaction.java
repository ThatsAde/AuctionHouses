package it.thatsade.unrealauctions.transaction;

import it.thatsade.unrealauctions.model.CollectionItem;
import it.thatsade.unrealauctions.storage.CollectionRepository;
import it.thatsade.unrealauctions.storage.DatabaseManager;
import it.thatsade.unrealauctions.storage.TransactionLogRepository;

import java.sql.Connection;
import java.util.UUID;

public class CollectItemTransaction extends Transaction {

    private final CollectionRepository collectionRepo;
    private final UUID ownerUuid;
    private final UUID collectionItemId;

    private CollectionItem collectionItem;

    public CollectItemTransaction(DatabaseManager db, TransactionLogRepository logRepo,
                                  CollectionRepository collectionRepo,
                                  UUID ownerUuid, UUID collectionItemId) {
        super(db, logRepo);
        this.collectionRepo = collectionRepo;
        this.ownerUuid = ownerUuid;
        this.collectionItemId = collectionItemId;
    }

    @Override
    protected TransactionResult doExecute(Connection conn) throws Exception {
        // Load all items for owner and find the one we want to delete
        var items = collectionRepo.findByOwner(conn, ownerUuid);
        collectionItem = items.stream()
                .filter(i -> i.getId().equals(collectionItemId))
                .findFirst()
                .orElse(null);

        if (collectionItem == null) {
            return TransactionResult.failure("listing-not-found");
        }

        boolean deleted = collectionRepo.deleteById(conn, collectionItemId, ownerUuid);
        if (!deleted) {
            return TransactionResult.failure("listing-not-found");
        }

        return TransactionResult.success(collectionItem);
    }

    @Override
    protected TransactionResult executePostCommit(TransactionResult dbResult) {
        try (Connection conn = db.getConnection()) {
            logRepo.log(conn, "COLLECT_ITEM", ownerUuid, collectionItem.getSourceListingId(),
                    null, "SUCCESS", null);
        } catch (Exception e) {
            log.warning("Failed to write transaction log: " + e.getMessage());
        }
        return dbResult;
    }
}
