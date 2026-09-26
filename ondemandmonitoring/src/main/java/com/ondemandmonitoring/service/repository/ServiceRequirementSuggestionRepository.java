package com.ondemandmonitoring.service.repository;

import com.ondemandmonitoring.service.domain.ServiceRequirementSuggestion;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ServiceRequirementSuggestionRepository extends JpaRepository<ServiceRequirementSuggestion, String> {

    List<ServiceRequirementSuggestion> findByActiveTrueAndServiceIdInOrderBySortOrderAscCreatedAtAsc(Collection<String> serviceIds);

    List<ServiceRequirementSuggestion> findByActiveTrueAndServiceIsNullOrderBySortOrderAscCreatedAtAsc();

    Optional<ServiceRequirementSuggestion> findByServiceIdAndCategoryIgnoreCaseAndLabelIgnoreCase(
            String serviceId,
            String category,
            String label);
}
