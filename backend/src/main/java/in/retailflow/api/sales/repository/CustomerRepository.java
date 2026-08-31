package in.retailflow.api.sales.repository;

import in.retailflow.api.sales.domain.Customer;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    @Query(
            """
            SELECT c FROM Customer c
            WHERE (:q IS NULL OR :q = ''
                OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(c.phone, '')) LIKE LOWER(CONCAT('%', :q, '%'))
                OR LOWER(COALESCE(c.email, '')) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:active IS NULL OR c.active = :active)
            """)
    Page<Customer> search(@Param("q") String q, @Param("active") Boolean active, Pageable pageable);

    @Query(
            """
            SELECT COUNT(c) > 0 FROM Customer c
            WHERE c.phone = :phone
              AND (:excludeId IS NULL OR c.id <> :excludeId)
            """)
    boolean existsPhone(@Param("phone") String phone, @Param("excludeId") UUID excludeId);

    java.util.Optional<Customer> findFirstByPhone(String phone);

    List<Customer> findByActiveTrueOrderByNameAsc();

    @Query(
            """
            SELECT COUNT(c),
                   COALESCE(SUM(CASE WHEN c.active = TRUE THEN 1 ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN c.active = FALSE THEN 1 ELSE 0 END), 0)
            FROM Customer c
            """)
    Object[] summarize();
}
