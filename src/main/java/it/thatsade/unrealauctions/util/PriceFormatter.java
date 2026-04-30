package it.thatsade.unrealauctions.util;

import java.text.NumberFormat;
import java.util.Locale;

public final class PriceFormatter {

    private static final NumberFormat FORMAT = NumberFormat.getNumberInstance(Locale.US);

    private PriceFormatter() {}

    public static String format(long price, String currencySymbol) {
        return currencySymbol + FORMAT.format(price);
    }

    public static long parsePriceArg(String input) throws NumberFormatException {
        if (input == null || input.isEmpty()) throw new NumberFormatException("empty");
        String cleaned = input.replace(",", "");
        if (cleaned.endsWith("k") || cleaned.endsWith("K")) {
            return (long) (Double.parseDouble(cleaned.substring(0, cleaned.length() - 1)) * 1000);
        }
        if (cleaned.endsWith("m") || cleaned.endsWith("M")) {
            return (long) (Double.parseDouble(cleaned.substring(0, cleaned.length() - 1)) * 1_000_000);
        }
        double val = Double.parseDouble(cleaned);
        if (val < 0) throw new NumberFormatException("negative");
        return (long) val;
    }
}
