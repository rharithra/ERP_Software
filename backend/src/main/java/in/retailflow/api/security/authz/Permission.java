package in.retailflow.api.security.authz;

import in.retailflow.api.identity.domain.TenantRole;
import java.util.EnumSet;
import java.util.Set;

/**
 * Fixed role-to-permission map for documentation and server-side checks.
 * Controllers continue to use {@code @PreAuthorize} on roles; this is not a custom-role editor.
 */
public enum Permission {
    PRODUCT_CREATE,
    PRODUCT_UPDATE,
    INVENTORY_ADJUST,
    PURCHASE_CREATE,
    PURCHASE_RECEIVE,
    CUSTOMER_CREATE,
    SALE_CREATE,
    PAYMENT_RECORD,
    LEAD_MANAGE,
    QUOTATION_MANAGE,
    SALES_ORDER_MANAGE,
    USER_MANAGE,
    COMPANY_SETTINGS,
    SALES_CONFIGURATION,
    REPORT_VIEW,
    EXPENSE_MANAGE;

    public static Set<Permission> forRole(TenantRole role) {
        return switch (role) {
            case OWNER -> EnumSet.allOf(Permission.class);
            case MANAGER -> EnumSet.of(
                    PRODUCT_CREATE,
                    PRODUCT_UPDATE,
                    INVENTORY_ADJUST,
                    PURCHASE_CREATE,
                    PURCHASE_RECEIVE,
                    CUSTOMER_CREATE,
                    SALE_CREATE,
                    PAYMENT_RECORD,
                    LEAD_MANAGE,
                    QUOTATION_MANAGE,
                    SALES_ORDER_MANAGE,
                    REPORT_VIEW,
                    EXPENSE_MANAGE);
            case CASHIER -> EnumSet.of(SALE_CREATE, PAYMENT_RECORD);
        };
    }
}
