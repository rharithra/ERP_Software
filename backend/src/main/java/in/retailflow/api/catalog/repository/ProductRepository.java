package in.retailflow.api.catalog.repository;

import in.retailflow.api.catalog.domain.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    @EntityGraph(attributePaths = "category")
    @Query(
            """
            SELECT p FROM Product p
            WHERE (:q IS NULL OR :q = ''
                OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(p.sku) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(p.barcode, '')) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:categoryId IS NULL OR p.category.id = :categoryId)
              AND (:active IS NULL OR p.active = :active)
            """)
    Page<Product> search(
            @Param("q") String q,
            @Param("categoryId") UUID categoryId,
            @Param("active") Boolean active,
            Pageable pageable);

    @Query(
            """
            SELECT COUNT(p) > 0 FROM Product p
            WHERE LOWER(p.sku) = LOWER(:sku)
              AND (:excludeId IS NULL OR p.id <> :excludeId)
            """)
    boolean existsSku(@Param("sku") String sku, @Param("excludeId") UUID excludeId);

    @Query(
            """
            SELECT COUNT(p) > 0 FROM Product p
            WHERE LOWER(p.barcode) = LOWER(:barcode)
              AND (:excludeId IS NULL OR p.id <> :excludeId)
            """)
    boolean existsBarcode(@Param("barcode") String barcode, @Param("excludeId") UUID excludeId);

    @EntityGraph(attributePaths = "category")
    Optional<Product> findById(UUID id);

    Optional<Product> findByIdAndTenant_Id(UUID id, UUID tenantId);
}
