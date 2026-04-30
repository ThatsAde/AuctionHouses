package it.thatsade.unrealauctions.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public class VaultEconomy {

    private Economy economy;

    public boolean initialize(Plugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault not found! Economy features will be disabled.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp =
                plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().severe("No economy provider found! Install an economy plugin (e.g. EssentialsX).");
            return false;
        }
        economy = rsp.getProvider();
        plugin.getLogger().info("Hooked into economy: " + economy.getName());
        return true;
    }

    public boolean hasBalance(UUID uuid, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return economy.has(player, amount);
    }

    public boolean withdraw(UUID uuid, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        EconomyResponse resp = economy.withdrawPlayer(player, amount);
        return resp.transactionSuccess();
    }

    public boolean deposit(UUID uuid, double amount) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        EconomyResponse resp = economy.depositPlayer(player, amount);
        return resp.transactionSuccess();
    }

    public double getBalance(UUID uuid) {
        return economy.getBalance(Bukkit.getOfflinePlayer(uuid));
    }

    public String format(double amount) {
        return economy.format(amount);
    }

    public Economy getEconomy() {
        return economy;
    }
}
