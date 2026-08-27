package in.retailflow.api.procurement.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class PurchaseMoney {

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private PurchaseMoney() {}

    public static LineTotals line(BigDecimal quantity, BigDecimal unitCost, BigDecimal gstPercent) {
        BigDecimal subtotal = quantity.multiply(unitCost).setScale(2, ROUNDING);
        BigDecimal tax = subtotal.multiply(gstPercent).divide(new BigDecimal("100"), 2, ROUNDING);
        return new LineTotals(subtotal, tax, subtotal.add(tax).setScale(2, ROUNDING));
    }

    public record LineTotals(BigDecimal subtotal, BigDecimal taxAmount, BigDecimal total) {}
}
