package it.thatsade.unrealauctions.service;

import it.thatsade.unrealauctions.config.Config;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class ValidationService {

    private final Config config;

    public ValidationService(Config config) {
        this.config = config;
    }

    public ValidationResult validateListing(ItemStack item, long price) {
        if (item == null || item.getType() == Material.AIR) {
            return ValidationResult.fail("cannot-sell-air");
        }
        if (config.getBlacklist().contains(item.getType())) {
            return ValidationResult.fail("blacklisted-item");
        }
        return validatePrice(price);
    }

    public ValidationResult validatePrice(long price) {
        if (price < config.getMinPrice()) {
            return ValidationResult.fail("min-price");
        }
        if (price > config.getMaxPrice()) {
            return ValidationResult.fail("max-price");
        }
        return ValidationResult.ok();
    }
}
