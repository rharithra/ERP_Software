package in.retailflow.api.procurement.repository;

import in.retailflow.api.procurement.domain.Supplier;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    @Query(
            """
            SELECT s FROM Supplier s
            WHERE (:q IS NULL OR :q = ''
                OR LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(s.contactPerson, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(s.phone, '')) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:active IS NULL OR s.active = :active)
            """)
    Page<Supplier> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);

    @Query(
            """
            SELECT COUNT(s) > 0 FROM Supplier s
            WHERE LOWER(s.name) = LOWER(:name)
              AND (:excludeId IS NULL OR s.id <> :excludeId)
            """)
    boolean existsName(@Param("name") String name, @Param("excludeId") UUID excludeId);

    List<Supplier> findByActiveTrueOrderByNameAsc();

    @Query(
            """
            SELECT COUNT(s),
                   COALESCE(SUM(CASE WHEN s.active = TRUE THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN s.active = FALSE THEN 1 ELSE 0 END), 0)
            FROM Supplier s
            """)
    Object[] summarize();
}
