package in.retailflow.api.inventory.domain;

import java.math.BigDecimal;

public final class InventoryStatuses {

    private InventoryStatuses() {}

    public static InventoryStatus from(BigDecimal quantity, BigDecimal reorderLevel) {
        BigDecimal qty = quantity == null ? BigDecimal.ZERO : quantity;
        BigDecimal reorder = reorderLevel == null ? BigDecimal.ZERO : reorderLevel;
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            return InventoryStatus.OUT_OF_STOCK;
        }
        if (qty.compareTo(reorder) <= 0) {
            return InventoryStatus.LOW_STOCK;
        }
        return InventoryStatus.IN_STOCK;
    }
}
