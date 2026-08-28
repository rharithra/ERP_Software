package in.retailflow.api.sales.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class SaleMoney {

    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private SaleMoney() {}

    public static LineTotals line(BigDecimal quantity, BigDecimal unitPrice, BigDecimal gstPercent, BigDecimal lineDiscount) {
        BigDecimal gross = quantity.multiply(unitPrice).setScale(2, ROUNDING);
        BigDecimal discount = lineDiscount == null ? BigDecimal.ZERO.setScale(2) : lineDiscount.setScale(2, ROUNDING);
        if (discount.compareTo(BigDecimal.ZERO) < 0 || discount.compareTo(gross) > 0) {
            throw new IllegalArgumentException("Line discount must be between 0 and the line subtotal");
        }
        BigDecimal taxable = gross.subtract(discount);
        BigDecimal tax = taxable.multiply(gstPercent).divide(new BigDecimal("100"), 2, ROUNDING);
        return new LineTotals(taxable, tax, taxable.add(tax).setScale(2, ROUNDING));
    }

    public record LineTotals(BigDecimal taxableAmount, BigDecimal taxAmount, BigDecimal total) {}
}
