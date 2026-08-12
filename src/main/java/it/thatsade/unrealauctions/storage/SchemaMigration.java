package it.thatsade.unrealauctions.storage;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

public class SchemaMigration {

    private static final Logger log = Logger.getLogger("UnrealAuctions");
    private static final int CURRENT_VERSION = 3;

    private final DatabaseManager db;

    public SchemaMigration(DatabaseManager db) {
        this.db = db;
    }

    public void run() throws SQLException {
        try (Connection conn = db.getConnection()) {
            conn.setAutoCommit(false);
            try {
                ensureVersionTable(conn);
                int version = getCurrentVersion(conn);
                if (version < 1) migrateV1(conn);
                if (version < 2) migrateV2(conn);
                if (version < 3) migrateV3(conn);
                conn.commit();
                log.info("Database schema is up to date (v" + CURRENT_VERSION + ").");
            } catch (SQLException e) {
                conn.rollback();
                throw new SQLException("Schema migration failed: " + e.getMessage(), e);
            }
        }
    }

    private void ensureVersionTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS schema_version (
                        version INTEGER PRIMARY KEY,
                        applied_at BIGINT NOT NULL
                    )
                    """);
        }
    }

    private int getCurrentVersion(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM schema_version")) {
            if (rs.next()) {
                int v = rs.getInt(1);
                return rs.wasNull() ? 0 : v;
            }
            return 0;
        }
    }

    private void migrateV1(Connection conn) throws SQLException {
        log.info("Applying database migration v1 (initial schema)...");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS listings (
                        id           CHAR(36)     NOT NULL PRIMARY KEY,
                        seller_uuid  CHAR(36)     NOT NULL,
                        seller_name  VARCHAR(16)  NOT NULL,
                        item_data    BLOB         NOT NULL,
                        item_type    VARCHAR(64)  NOT NULL,
                        price        BIGINT       NOT NULL,
                        listing_type TINYINT      NOT NULL DEFAULT 0,
                        status       TINYINT      NOT NULL DEFAULT 0,
                        created_at   BIGINT       NOT NULL,
                        expires_at   BIGINT       NOT NULL,
                        buyer_uuid   CHAR(36),
                        sold_at      BIGINT
                    )
                    """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_status_expires ON listings(status, expires_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_seller ON listings(seller_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_item_type ON listings(item_type)");

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS collection_box (
                        id         CHAR(36)  NOT NULL PRIMARY KEY,
                        owner_uuid CHAR(36)  NOT NULL,
                        item_data  BLOB      NOT NULL,
                        source     TINYINT   NOT NULL DEFAULT 0,
                        source_id  CHAR(36),
                        added_at   BIGINT    NOT NULL
                    )
                    """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_owner ON collection_box(owner_uuid)");

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS transaction_log (
                        id               CHAR(36)     NOT NULL PRIMARY KEY,
                        transaction_type VARCHAR(32)  NOT NULL,
                        actor_uuid       CHAR(36)     NOT NULL,
                        listing_id       CHAR(36),
                        amount           BIGINT,
                        result           VARCHAR(16)  NOT NULL,
                        error_message    VARCHAR(255),
                        occurred_at      BIGINT       NOT NULL
                    )
                    """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_actor_time ON transaction_log(actor_uuid, occurred_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_log_listing ON transaction_log(listing_id)");

            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_stats (
                        player_uuid     CHAR(36)  NOT NULL PRIMARY KEY,
                        total_listings  INT       NOT NULL DEFAULT 0,
                        total_sales     INT       NOT NULL DEFAULT 0,
                        total_purchases INT       NOT NULL DEFAULT 0,
                        total_earned    BIGINT    NOT NULL DEFAULT 0,
                        total_spent     BIGINT    NOT NULL DEFAULT 0,
                        last_listing_at BIGINT
                    )
                    """);

            stmt.execute("INSERT INTO schema_version (version, applied_at) VALUES (1, " + System.currentTimeMillis() + ")");
        }
    }

    private void migrateV2(Connection conn) throws SQLException {
        log.info("Applying database migration v2 (collection_box expiry)...");
        try (Statement stmt = conn.createStatement()) {
            // SQLite supports ADD COLUMN; NULL means no expiry (legacy rows are unaffected)
            stmt.execute("ALTER TABLE collection_box ADD COLUMN expires_at BIGINT");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_collection_expires ON collection_box(expires_at)");
            stmt.execute("INSERT INTO schema_version (version, applied_at) VALUES (2, " + System.currentTimeMillis() + ")");
        }
    }

    private void migrateV3(Connection conn) throws SQLException {
        log.info("Applying database migration v3 (item orders)...");
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS orders (
                        id             CHAR(36)     NOT NULL PRIMARY KEY,
                        creator_uuid   CHAR(36)     NOT NULL,
                        creator_name   VARCHAR(16)  NOT NULL,
                        item_type      VARCHAR(64)  NOT NULL,
                        quantity       INT          NOT NULL,
                        total_price    BIGINT       NOT NULL,
                        status         TINYINT      NOT NULL DEFAULT 0,
                        created_at     BIGINT       NOT NULL,
                        expires_at     BIGINT       NOT NULL,
                        fulfiller_uuid CHAR(36),
                        fulfiller_name VARCHAR(16),
                        fulfilled_at   BIGINT
                    )
                    """);
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_orders_status_expires ON orders(status, expires_at)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_orders_creator ON orders(creator_uuid)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_orders_item_status ON orders(item_type, status)");

            stmt.execute("INSERT INTO schema_version (version, applied_at) VALUES (3, " + System.currentTimeMillis() + ")");
        }
    }
}
