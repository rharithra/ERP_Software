package in.retailflow.api.security.authz;

import static org.assertj.core.api.Assertions.assertThat;

import in.retailflow.api.identity.domain.TenantRole;
import org.junit.jupiter.api.Test;

class PermissionMappingTest {

    @Test
    void ownerHasUserManageAndSalesConfiguration() {
        assertThat(Permission.forRole(TenantRole.OWNER))
                .contains(Permission.USER_MANAGE, Permission.COMPANY_SETTINGS, Permission.SALES_CONFIGURATION);
    }

    @Test
    void managerCannotManageUsersOrSalesConfiguration() {
        assertThat(Permission.forRole(TenantRole.MANAGER))
                .contains(Permission.PRODUCT_CREATE, Permission.LEAD_MANAGE, Permission.SALE_CREATE)
                .doesNotContain(Permission.USER_MANAGE, Permission.SALES_CONFIGURATION, Permission.COMPANY_SETTINGS);
    }

    @Test
    void cashierIsLimitedToSalesAndPayments() {
        assertThat(Permission.forRole(TenantRole.CASHIER))
                .containsExactlyInAnyOrder(Permission.SALE_CREATE, Permission.PAYMENT_RECORD);
    }
}
