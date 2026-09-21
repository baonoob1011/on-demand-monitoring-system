package com.ondemandmonitoring.service.repository;

import com.ondemandmonitoring.service.domain.ServiceDeliverable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceDeliverableRepository extends JpaRepository<ServiceDeliverable, String> {

    List<ServiceDeliverable> findAllByServiceId(String serviceId);

    List<ServiceDeliverable> findAllByDeliverableTypeId(String deliverableTypeId);

    boolean existsByServiceIdAndDeliverableTypeId(String serviceId, String deliverableTypeId);
}
