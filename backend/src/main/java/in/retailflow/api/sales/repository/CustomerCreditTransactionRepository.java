package in.retailflow.api.sales.repository;

import in.retailflow.api.sales.domain.CreditTransactionType;
import in.retailflow.api.sales.domain.CustomerCreditTransaction;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerCreditTransactionRepository extends JpaRepository<CustomerCreditTransaction, UUID> {

    List<CustomerCreditTransaction> findByCustomer_IdOrderByCreatedAtDesc(UUID customerId);

    List<CustomerCreditTransaction> findByCustomer_IdAndTransactionTypeOrderByCreatedAtAsc(
            UUID customerId, CreditTransactionType transactionType);

    @Query(
            """
            SELECT COALESCE(SUM(CASE WHEN t.transactionType = in.retailflow.api.sales.domain.CreditTransactionType.CREDIT_CREATED THEN t.amount ELSE 0 END), 0)
                 - COALESCE(SUM(CASE WHEN t.transactionType = in.retailflow.api.sales.domain.CreditTransactionType.CREDIT_APPLIED THEN t.amount ELSE 0 END), 0)
                 - COALESCE(SUM(CASE WHEN t.transactionType = in.retailflow.api.sales.domain.CreditTransactionType.CREDIT_REFUNDED THEN t.amount ELSE 0 END), 0)
                 + COALESCE(SUM(CASE WHEN t.transactionType = in.retailflow.api.sales.domain.CreditTransactionType.ADJUSTMENT THEN t.amount ELSE 0 END), 0)
            FROM CustomerCreditTransaction t
            WHERE t.customer.id = :customerId
            """)
    BigDecimal balanceForCustomer(@Param("customerId") UUID customerId);

    @Query(
            """
            SELECT COALESCE(SUM(t.amount), 0)
            FROM CustomerCreditTransaction t
            WHERE t.sale.id = :saleId
              AND t.transactionType = :type
            """)
    BigDecimal sumBySaleAndType(@Param("saleId") UUID saleId, @Param("type") CreditTransactionType type);

    @Query(
            """
            SELECT COALESCE(SUM(t.amount), 0)
            FROM CustomerCreditTransaction t
            WHERE t.sourceSale.id = :saleId
              AND t.transactionType = in.retailflow.api.sales.domain.CreditTransactionType.CREDIT_APPLIED
            """)
    BigDecimal sumAppliedFromSale(@Param("saleId") UUID saleId);

    @Query(
            """
            SELECT COALESCE(SUM(t.amount), 0)
            FROM CustomerCreditTransaction t
            WHERE t.transactionType = in.retailflow.api.sales.domain.CreditTransactionType.CREDIT_CREATED
            """)
    BigDecimal sumCreated();

    @Query(
            """
            SELECT COALESCE(SUM(t.amount), 0)
            FROM CustomerCreditTransaction t
            WHERE t.transactionType = in.retailflow.api.sales.domain.CreditTransactionType.CREDIT_APPLIED
            """)
    BigDecimal sumApplied();

    @Query(
            """
            SELECT COALESCE(SUM(t.amount), 0)
            FROM CustomerCreditTransaction t
            WHERE t.transactionType = in.retailflow.api.sales.domain.CreditTransactionType.CREDIT_REFUNDED
            """)
    BigDecimal sumRefunded();
}
