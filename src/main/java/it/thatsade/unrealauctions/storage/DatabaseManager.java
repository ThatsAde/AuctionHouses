package it.thatsade.unrealauctions.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import it.thatsade.unrealauctions.config.Config;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {

    public enum DatabaseType { SQLITE, MYSQL }

    private HikariDataSource dataSource;
    private DatabaseType type;

    public void initialize(Config config, File dataFolder) throws SQLException {
        type = "mysql".equalsIgnoreCase(config.getDatabaseType())
                ? DatabaseType.MYSQL
                : DatabaseType.SQLITE;

        HikariConfig hc = new HikariConfig();

        if (type == DatabaseType.SQLITE) {
            File dbFile = new File(dataFolder, config.getSqliteFile());
            hc.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            hc.setDriverClassName("org.sqlite.JDBC");
            hc.setMaximumPoolSize(1);
            hc.setMinimumIdle(1);
            hc.setConnectionTestQuery("SELECT 1");
            hc.setConnectionInitSql(
                    "PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON; PRAGMA synchronous=NORMAL;"
            );
        } else {
            String url = "jdbc:mysql://" + config.getMysqlHost() + ":"
                    + config.getMysqlPort() + "/" + config.getMysqlDatabase()
                    + "?useSSL=false&characterEncoding=utf8"
                    + "&allowPublicKeyRetrieval=true";
            hc.setJdbcUrl(url);
            hc.setUsername(config.getMysqlUsername());
            hc.setPassword(config.getMysqlPassword());
            hc.setMaximumPoolSize(config.getMysqlPoolSize());
            hc.setTransactionIsolation("TRANSACTION_SERIALIZABLE");
            // No connectionTestQuery for MySQL: Connector/J supports JDBC4
            // Connection.isValid(), which HikariCP uses by default and is the
            // recommended (faster) validation path.
            // Ping idle connections every 30 s so MySQL/proxies don't close them
            // before maxLifetime — must be less than the server's wait_timeout.
            hc.setKeepaliveTime(30_000);
        }

        hc.setPoolName("UnrealAuctions-Pool");
        // Bound how long a borrow blocks when the DB is unreachable. Harmless for
        // SQLite (local file); for MySQL it stops the transaction worker from
        // stalling 30 s per attempt during a network/server outage.
        hc.setConnectionTimeout(10_000);
        hc.setIdleTimeout(120_000);
        // Recycle connections within typical managed-MySQL/proxy idle windows so
        // they are retired before the server can close them out from under the pool.
        // Ordering invariant: keepaliveTime (30 s) < idleTimeout (120 s) < maxLifetime (300 s).
        hc.setMaxLifetime(300_000);

        dataSource = new HikariDataSource(hc);

        // Verify connectivity
        try (Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(5)) {
                throw new SQLException("Database connection validation failed.");
            }
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public DatabaseType getType() {
        return type;
    }

    public boolean isMysql() {
        return type == DatabaseType.MYSQL;
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
