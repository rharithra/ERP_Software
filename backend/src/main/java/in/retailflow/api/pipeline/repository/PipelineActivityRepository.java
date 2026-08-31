package in.retailflow.api.pipeline.repository;

import in.retailflow.api.pipeline.domain.PipelineActivity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineActivityRepository extends JpaRepository<PipelineActivity, UUID> {

    List<PipelineActivity> findByLeadIdOrderByCreatedAtAsc(UUID leadId);
}
