package in.retailflow.api.sales.repository;

import in.retailflow.api.sales.domain.Payment;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findBySaleIdOrderByPaymentDateAscCreatedAtAsc(UUID saleId);

    List<Payment> findBySalesOrderIdOrderByPaymentDateAscCreatedAtAsc(UUID salesOrderId);

    List<Payment> findByCustomerIdOrderByPaymentDateDescCreatedAtDesc(UUID customerId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.sale.id = :saleId")
    BigDecimal sumBySaleId(@Param("saleId") UUID saleId);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.salesOrder.id = :orderId")
    BigDecimal sumBySalesOrderId(@Param("orderId") UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.salesOrder.id = :orderId")
    List<Payment> findBySalesOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Query(
            """
            SELECT COALESCE(SUM(s.grandTotal - COALESCE((SELECT SUM(p.amount) FROM Payment p WHERE p.sale.id = s.id), 0)), 0)
            FROM in.retailflow.api.sales.domain.Sale s
            WHERE CAST(s.status AS string) = 'COMPLETED'
            """)
    BigDecimal outstandingCompletedSales();
}
