package in.retailflow.api.tenant.service;

import in.retailflow.api.tenant.domain.BusinessType;
import in.retailflow.api.tenant.domain.SalesMode;

/**
 * Recommends a sales <em>entry workflow</em> from business type. Quick Sale and Pipeline
 * later feed the same Sale → Invoice → Inventory engine; they are not separate ledgers.
 */
public final class SalesModeRecommendation {

    private SalesModeRecommendation() {}

    public static SalesMode recommend(BusinessType businessType) {
        if (businessType == null) {
            return SalesMode.HYBRID;
        }
        return switch (businessType) {
            case GROCERY_SUPERMARKET -> SalesMode.QUICK_SALE;
            case APPLIANCES_WATER_PURIFIER, FURNITURE -> SalesMode.PIPELINE;
            case ELECTRONICS_COMPUTER,
                    MOBILE_ACCESSORIES,
                    HARDWARE_BUILDING_MATERIALS,
                    OTHER -> SalesMode.HYBRID;
        };
    }
}
