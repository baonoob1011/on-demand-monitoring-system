package com.ondemandmonitoring.checklist.repository;

import com.ondemandmonitoring.checklist.domain.ServiceChecklist;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;

public interface ServiceChecklistRepository extends JpaRepository<ServiceChecklist, String> {
    @Query("select sc.checklist.id from ServiceChecklist sc where sc.service.id = :serviceId order by sc.displayOrder, sc.id")
    List<String> findTemplateChecklistIds(@org.springframework.data.repository.query.Param("serviceId") String serviceId);
    boolean existsByServiceIdAndChecklistId(String serviceId, String checklistId);
    Optional<ServiceChecklist> findByServiceIdAndChecklistId(String serviceId, String checklistId);
    @EntityGraph(attributePaths = {"service", "checklist"})
    List<ServiceChecklist> findAllByServiceIdOrderByDisplayOrderAscIdAsc(String serviceId);
    @EntityGraph(attributePaths = {"service", "checklist"})
    List<ServiceChecklist> findAllByServiceIdAndChecklistIsActiveTrueOrderByDisplayOrderAscIdAsc(String serviceId);
    @EntityGraph(attributePaths = {"service", "checklist"})
    List<ServiceChecklist> findAllByChecklistIdOrderByServiceNameAscServiceIdAsc(String checklistId);
}
