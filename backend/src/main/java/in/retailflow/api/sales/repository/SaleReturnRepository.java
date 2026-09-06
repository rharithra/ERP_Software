package in.retailflow.api.sales.repository;

import in.retailflow.api.sales.domain.SaleReturn;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, UUID> {

    @EntityGraph(attributePaths = {"sale", "customer", "items", "items.product", "items.saleItem"})
    Optional<SaleReturn> findById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM SaleReturn r WHERE r.id = :id")
    Optional<SaleReturn> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"sale", "customer"})
    List<SaleReturn> findBySale_IdOrderByCreatedAtDesc(UUID saleId);

    @EntityGraph(attributePaths = {"sale", "customer"})
    List<SaleReturn> findByCustomer_IdOrderByCreatedAtDesc(UUID customerId);

    @Query(
            """
            SELECT COALESCE(SUM(i.quantity), 0)
            FROM SaleReturnItem i
            WHERE i.saleItem.id = :saleItemId
              AND CAST(i.saleReturn.status AS string) = 'COMPLETED'
            """)
    BigDecimal sumCompletedQuantityForSaleItem(@Param("saleItemId") UUID saleItemId);

    @Query(
            """
            SELECT COALESCE(SUM(r.totalAmount), 0)
            FROM SaleReturn r
            WHERE r.sale.id = :saleId
              AND CAST(r.status AS string) = 'COMPLETED'
            """)
    BigDecimal sumCompletedTotalForSale(@Param("saleId") UUID saleId);

    @EntityGraph(attributePaths = {"sale", "customer"})
    @Query(
            """
            SELECT r FROM SaleReturn r
            WHERE (:q IS NULL OR :q = ''
                OR LOWER(r.returnNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(r.sale.saleNumber) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(r.sale.invoiceNumber, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(r.sale.customerName, '')) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:customerId IS NULL OR r.customer.id = :customerId)
              AND (:saleId IS NULL OR r.sale.id = :saleId)
              AND (:status IS NULL OR :status = '' OR CAST(r.status AS string) = :status)
              AND (:fromDate IS NULL OR r.returnDate >= :fromDate)
              AND (:toDate IS NULL OR r.returnDate <= :toDate)
            """)
    Page<SaleReturn> search(
            @Param("q") String q,
            @Param("customerId") UUID customerId,
            @Param("saleId") UUID saleId,
            @Param("status") String status,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable);

    @Query(
            """
            SELECT COUNT(r), COALESCE(SUM(r.totalAmount), 0)
            FROM SaleReturn r
            WHERE CAST(r.status AS string) = 'COMPLETED'
              AND r.returnDate = :returnDate
            """)
    Object[] summarizeCompletedOn(@Param("returnDate") LocalDate returnDate);
}
