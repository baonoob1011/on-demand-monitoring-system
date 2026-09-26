package com.ondemandmonitoring.userschedule.repository;

import com.ondemandmonitoring.userschedule.domain.UserSchedule;
import com.ondemandmonitoring.userschedule.enums.UserScheduleStatus;
import com.ondemandmonitoring.userschedule.enums.UserScheduleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserScheduleRepository
        extends JpaRepository<UserSchedule, String>, JpaSpecificationExecutor<UserSchedule> {

    List<UserSchedule> findByStaffId(String staffId);

    List<UserSchedule> findByStaffIdAndStatus(String staffId, UserScheduleStatus status);

    List<UserSchedule> findByStaffIdAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(UUID staffId, Instant endTime,
            Instant startTime);

    List<UserSchedule> findByReferenceId(String referenceId);

    List<UserSchedule> findByScheduleType(UserScheduleType scheduleType);

    List<UserSchedule> findByStatus(UserScheduleStatus status);
}
