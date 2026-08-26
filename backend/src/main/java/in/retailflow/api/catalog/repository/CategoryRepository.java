package in.retailflow.api.catalog.repository;

import in.retailflow.api.catalog.domain.Category;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    @Query(
            """
            SELECT c FROM Category c
            WHERE (:q IS NULL OR :q = '' OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:active IS NULL OR c.active = :active)
            """)
    Page<Category> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);

    @Query(
            """
            SELECT COUNT(c) > 0 FROM Category c
            WHERE LOWER(c.name) = LOWER(:name)
              AND (:excludeId IS NULL OR c.id <> :excludeId)
            """)
    boolean existsName(@Param("name") String name, @Param("excludeId") UUID excludeId);

    List<Category> findByActiveTrueOrderByNameAsc();

    Optional<Category> findByIdAndTenant_Id(UUID id, UUID tenantId);
}
