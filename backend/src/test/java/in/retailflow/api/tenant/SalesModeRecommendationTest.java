package in.retailflow.api.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import in.retailflow.api.tenant.domain.BusinessType;
import in.retailflow.api.tenant.domain.SalesMode;
import in.retailflow.api.tenant.service.SalesModeRecommendation;
import org.junit.jupiter.api.Test;

class SalesModeRecommendationTest {

    @Test
    void recommendsExpectedModeForEachBusinessType() {
        assertThat(SalesModeRecommendation.recommend(BusinessType.GROCERY_SUPERMARKET)).isEqualTo(SalesMode.QUICK_SALE);
        assertThat(SalesModeRecommendation.recommend(BusinessType.ELECTRONICS_COMPUTER)).isEqualTo(SalesMode.HYBRID);
        assertThat(SalesModeRecommendation.recommend(BusinessType.MOBILE_ACCESSORIES)).isEqualTo(SalesMode.HYBRID);
        assertThat(SalesModeRecommendation.recommend(BusinessType.APPLIANCES_WATER_PURIFIER)).isEqualTo(SalesMode.PIPELINE);
        assertThat(SalesModeRecommendation.recommend(BusinessType.FURNITURE)).isEqualTo(SalesMode.PIPELINE);
        assertThat(SalesModeRecommendation.recommend(BusinessType.HARDWARE_BUILDING_MATERIALS)).isEqualTo(SalesMode.HYBRID);
        assertThat(SalesModeRecommendation.recommend(BusinessType.OTHER)).isEqualTo(SalesMode.HYBRID);
    }
}
