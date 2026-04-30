package it.thatsade.unrealauctions.service;

public class FeeCalculator {

    private final double listingFeePercent;
    private final double cancelFeePercent;

    public FeeCalculator(double listingFeePercent, double cancelFeePercent) {
        this.listingFeePercent = listingFeePercent;
        this.cancelFeePercent = cancelFeePercent;
    }

    public long calculateListingFee(long price) {
        return Math.round(price * listingFeePercent / 100.0);
    }

    public long calculateCancelFee(long price) {
        return Math.round(price * cancelFeePercent / 100.0);
    }

    public long calculateSellerProceeds(long price) {
        return price; // Seller gets the full price; listing fee is charged separately at listing time
    }
}
