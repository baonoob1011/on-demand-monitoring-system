package com.ondemandmonitoring.zone.repository;

import com.ondemandmonitoring.zone.domain.Zone;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ZoneRepository extends JpaRepository<Zone, String> {

    Optional<Zone> findByCode(String code);

    boolean existsByCode(String code);

    List<Zone> findAllByRestrictedTrueOrderByCodeAsc();

    @Modifying
    @Query("delete from Zone z where z.sourceWorld = :sourceWorld and z.code not in :codes")
    int deleteStaleSimulationZones(
            @Param("sourceWorld") String sourceWorld,
            @Param("codes") Collection<String> codes);
}
