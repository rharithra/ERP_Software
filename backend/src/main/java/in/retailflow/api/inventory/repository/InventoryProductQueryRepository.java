package in.retailflow.api.inventory.repository;

import in.retailflow.api.catalog.domain.Product;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryProductQueryRepository extends JpaRepository<Product, UUID> {

    @EntityGraph(attributePaths = "category")
    @Query(
            """
            SELECT p FROM Product p
            LEFT JOIN InventoryBalance b ON b.product = p
            WHERE (:q IS NULL OR :q = ''
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:status IS NULL
                OR (:status = 'OUT_OF_STOCK' AND COALESCE(b.quantity, 0) = 0)
                OR (:status = 'LOW_STOCK' AND b.quantity > 0 AND b.quantity <= b.reorderLevel)
                OR (:status = 'IN_STOCK' AND COALESCE(b.quantity, 0) > COALESCE(b.reorderLevel, 0)))
            """)
    Page<Product> search(
            @Param("q") String q,
            @Param("categoryId") UUID categoryId,
            @Param("status") String status,
            Pageable pageable);

    @Query(
            """
            SELECT COUNT(p),
                   COALESCE(SUM(CASE WHEN COALESCE(b.quantity, 0) > COALESCE(b.reorderLevel, 0) THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN b.quantity > 0 AND b.quantity <= b.reorderLevel THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN COALESCE(b.quantity, 0) = 0 THEN 1 ELSE 0 END), 0)
            FROM Product p
            LEFT JOIN InventoryBalance b ON b.product = p
            """)
    Object[] summarize();
}
