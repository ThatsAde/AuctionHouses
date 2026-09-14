package it.thatsade.unrealauctions.config;

import it.thatsade.unrealauctions.UnrealAuctionsPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Config {

    private final UnrealAuctionsPlugin plugin;

    private String databaseType;
    private String sqliteFile;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private int mysqlPoolSize;

    private int defaultDurationHours;
    private int maxActivePerPlayer;
    private Map<String, Integer> permissionLimits;
    private long minPrice;
    private long maxPrice;
    private double listingFeePercent;
    private double cancelFeePercent;
    private int collectionBoxExpiryDays;
    private Set<Material> blacklist;

    private boolean ordersEnabled;
    private int maxOpenOrdersPerPlayer;
    private Map<String, Integer> orderPermissionLimits;
    private int orderDurationHours;
    private int orderMaxQuantity;

    private int maxQueueDepth;

    private int listingsPerMinute;
    private int purchasesPerMinute;
    private int listingCooldownSeconds;

    private int itemsPerPage;
    private int refreshIntervalSeconds;

    private String currencySymbol;

    /** Limit nodes added dynamically so permission plugins can discover them. */
    private final Set<String> registeredLimitPermissions = new HashSet<>();

    public Config(UnrealAuctionsPlugin plugin) {
        this.plugin = plugin;
        plugin.saveDefaultConfig();
        addMissingOrderPermissionLimits();
        load();
    }

    public void reload() {
        plugin.reloadConfig();
        addMissingOrderPermissionLimits();
        load();
    }

    /**
     * Adds the order permission limits introduced after orders were first
     * released. An explicitly configured (including empty) section is preserved.
     */
    private void addMissingOrderPermissionLimits() {
        var cfg = plugin.getConfig();
        String path = "orders.permission-limits";
        if (cfg.contains(path, true)) {
            return;
        }

        Configuration defaults = cfg.getDefaults();
        ConfigurationSection defaultLimits = defaults == null
                ? null
                : defaults.getConfigurationSection(path);
        if (defaultLimits == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : defaultLimits.getValues(false).entrySet()) {
            cfg.set(path + "." + entry.getKey(), entry.getValue());
        }
        plugin.saveConfig();
        plugin.getLogger().info("Added missing orders.permission-limits defaults to config.yml.");
    }

    private void load() {
        var cfg = plugin.getConfig();

        databaseType = cfg.getString("database.type", "sqlite");
        sqliteFile = cfg.getString("database.sqlite.file", "auctionhouse.db");
        mysqlHost = cfg.getString("database.mysql.host", "localhost");
        mysqlPort = cfg.getInt("database.mysql.port", 3306);
        mysqlDatabase = cfg.getString("database.mysql.database", "auctionhouse");
        mysqlUsername = cfg.getString("database.mysql.username", "root");
        mysqlPassword = cfg.getString("database.mysql.password", "");
        mysqlPoolSize = cfg.getInt("database.mysql.pool-size", 10);

        defaultDurationHours = cfg.getInt("listings.default-duration-hours", 48);
        maxActivePerPlayer = cfg.getInt("listings.max-active-per-player", 10);

        permissionLimits = loadPermissionLimits(cfg, "listings.permission-limits");
        minPrice = cfg.getLong("listings.min-price", 1);
        maxPrice = cfg.getLong("listings.max-price", 1_000_000_000L);
        listingFeePercent = cfg.getDouble("listings.listing-fee-percent", 2.5);
        cancelFeePercent = cfg.getDouble("listings.cancel-fee-percent", 0.0);
        collectionBoxExpiryDays = cfg.getInt("listings.collection-box-expiry-days", 7);

        blacklist = new HashSet<>();
        List<String> blacklistRaw = cfg.getStringList("listings.blacklist");
        for (String name : blacklistRaw) {
            Material mat = Material.matchMaterial(name);
            if (mat != null) {
                blacklist.add(mat);
            } else {
                plugin.getLogger().warning("Unknown material in blacklist: " + name);
            }
        }

        ordersEnabled = cfg.getBoolean("orders.enabled", true);
        maxOpenOrdersPerPlayer = cfg.getInt("orders.max-open-per-player", 5);
        orderPermissionLimits = loadPermissionLimits(cfg, "orders.permission-limits");
        registerLimitPermissions();
        orderDurationHours = cfg.getInt("orders.duration-hours", 48);
        orderMaxQuantity = cfg.getInt("orders.max-quantity", 2304);

        maxQueueDepth = cfg.getInt("queue.max-depth", 500);

        listingsPerMinute = cfg.getInt("rate-limits.listings-per-minute", 5);
        purchasesPerMinute = cfg.getInt("rate-limits.purchases-per-minute", 20);
        listingCooldownSeconds = cfg.getInt("rate-limits.listing-cooldown-seconds", 10);

        itemsPerPage = cfg.getInt("gui.items-per-page", 45);
        refreshIntervalSeconds = cfg.getInt("gui.refresh-interval-seconds", 30);

        currencySymbol = cfg.getString("economy.currency-symbol", "$");
    }

    /**
     * Reads a "&lt;permission node&gt;: &lt;limit&gt;" section. A limit of -1 means
     * unlimited. A missing section yields an empty map, which disables overrides.
     */
    private Map<String, Integer> loadPermissionLimits(ConfigurationSection cfg, String path) {
        Map<String, Integer> limits = new HashMap<>();
        ConfigurationSection section = cfg.getConfigurationSection(path);
        if (section != null) {
            collectPermissionLimits(section, "", path, limits);
        }
        return limits;
    }

    private void collectPermissionLimits(ConfigurationSection section, String prefix,
                                         String configPath, Map<String, Integer> limits) {
        // Reading immediate values preserves literal dots from YAML. Recursing also
        // supports sections produced by Bukkit's path-based config writer.
        for (Map.Entry<String, Object> entry : section.getValues(false).entrySet()) {
            String permission = prefix + entry.getKey();
            Object raw = entry.getValue();
            if (raw instanceof ConfigurationSection child) {
                collectPermissionLimits(child, permission + ".", configPath, limits);
                continue;
            }

            Integer limit = parseLimit(raw);
            if (permission.isBlank() || limit == null || limit < -1) {
                plugin.getLogger().warning("Ignoring invalid permission limit at "
                        + configPath + "." + permission + ": " + raw);
                continue;
            }
            limits.put(permission, limit);
        }
    }

    private Integer parseLimit(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(raw));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /**
     * Registers config-defined limit nodes with Bukkit. This makes them visible
     * to permission-manager autocomplete without hard-coding configurable nodes
     * in plugin.yml. Existing permissions owned by another plugin are preserved.
     */
    private void registerLimitPermissions() {
        var pluginManager = plugin.getServer().getPluginManager();
        unregisterLimitPermissions();

        registerLimitPermissions(permissionLimits.keySet(), "auction listing limit");
        registerLimitPermissions(orderPermissionLimits.keySet(), "open order limit");
    }

    /** Removes only the dynamic permissions this config instance added. */
    public void unregisterLimitPermissions() {
        var pluginManager = plugin.getServer().getPluginManager();
        for (String permission : registeredLimitPermissions) {
            pluginManager.removePermission(permission);
        }
        registeredLimitPermissions.clear();
    }

    private void registerLimitPermissions(Set<String> permissions, String description) {
        var pluginManager = plugin.getServer().getPluginManager();
        for (String permissionName : permissions) {
            if (pluginManager.getPermission(permissionName) != null) {
                continue;
            }
            try {
                pluginManager.addPermission(new Permission(
                        permissionName,
                        "Grants a configured UnrealAuctions " + description,
                        PermissionDefault.FALSE));
                registeredLimitPermissions.add(permissionName);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Could not register permission '"
                        + permissionName + "': " + e.getMessage());
            }
        }
    }

    public String getDatabaseType() { return databaseType; }
    public String getSqliteFile() { return sqliteFile; }
    public String getMysqlHost() { return mysqlHost; }
    public int getMysqlPort() { return mysqlPort; }
    public String getMysqlDatabase() { return mysqlDatabase; }
    public String getMysqlUsername() { return mysqlUsername; }
    public String getMysqlPassword() { return mysqlPassword; }
    public int getMysqlPoolSize() { return mysqlPoolSize; }
    public int getDefaultDurationHours() { return defaultDurationHours; }
    public int getMaxActivePerPlayer() { return maxActivePerPlayer; }
    public Map<String, Integer> getPermissionLimits() { return permissionLimits; }
    public long getMinPrice() { return minPrice; }
    public long getMaxPrice() { return maxPrice; }
    public double getListingFeePercent() { return listingFeePercent; }
    public double getCancelFeePercent() { return cancelFeePercent; }
    public int getCollectionBoxExpiryDays() { return collectionBoxExpiryDays; }
    public long getCollectionBoxExpiryMillis() { return collectionBoxExpiryDays * 24L * 60 * 60 * 1000; }
    public Set<Material> getBlacklist() { return blacklist; }
    public boolean isOrdersEnabled() { return ordersEnabled; }
    public int getMaxOpenOrdersPerPlayer() { return maxOpenOrdersPerPlayer; }
    public Map<String, Integer> getOrderPermissionLimits() { return orderPermissionLimits; }
    public int getOrderDurationHours() { return orderDurationHours; }
    public int getOrderMaxQuantity() { return orderMaxQuantity; }
    public int getMaxQueueDepth() { return maxQueueDepth; }
    public int getListingsPerMinute() { return listingsPerMinute; }
    public int getPurchasesPerMinute() { return purchasesPerMinute; }
    public int getListingCooldownSeconds() { return listingCooldownSeconds; }
    public int getItemsPerPage() { return itemsPerPage; }
    public int getRefreshIntervalSeconds() { return refreshIntervalSeconds; }
    public String getCurrencySymbol() { return currencySymbol; }
}
