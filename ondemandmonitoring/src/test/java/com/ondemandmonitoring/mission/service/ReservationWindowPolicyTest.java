package com.ondemandmonitoring.mission.service;

import com.ondemandmonitoring.mission.service.impl.ReservationWindowPolicy;
import com.ondemandmonitoring.userschedule.domain.UserSchedule;
import com.ondemandmonitoring.userschedule.enums.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReservationWindowPolicyTest {
    Instant at(int hour) { return Instant.parse("2026-10-10T00:00:00Z").plusSeconds(hour*3600L); }
    UserSchedule schedule(UserScheduleType type,int start,int end) {
        return UserSchedule.builder().scheduleType(type).status(UserScheduleStatus.SCHEDULED).startTime(at(start)).endTime(at(end)).build();
    }
    @Test void adjacentIntervalsAreNotOverlappingButOneSecondIs() {
        assertFalse(ReservationWindowPolicy.overlaps(at(9),at(10),at(10),at(11)));
        assertTrue(ReservationWindowPolicy.overlaps(at(9),at(10),at(10).minusSeconds(1),at(11)));
    }
    @Test void oneCoveringShiftIsEnoughAndUnrelatedFutureShiftsDoNotBlock() {
        var rows=List.of(schedule(UserScheduleType.SHIFT,8,12),schedule(UserScheduleType.SHIFT,32,36));
        assertFalse(ReservationWindowPolicy.staffHasConflict(rows,"m",at(9),at(10)));
        assertTrue(ReservationWindowPolicy.staffHasConflict(rows,"m",at(12),at(13)));
    }
    @Test void ownMissionCalendarIsExcludedButSiblingAndLeaveAreExclusive() {
        var owned=schedule(UserScheduleType.MISSION,9,10); owned.setReferenceId("m");
        assertFalse(ReservationWindowPolicy.staffHasConflict(List.of(owned),"m",at(9),at(10)));
        assertTrue(ReservationWindowPolicy.staffHasConflict(List.of(owned),"sibling",at(9),at(10)));
        var leave=schedule(UserScheduleType.LEAVE,9,10);
        assertFalse(ReservationWindowPolicy.staffHasConflict(List.of(leave),"m",at(10),at(11)));
        assertTrue(ReservationWindowPolicy.staffHasConflict(List.of(leave),"m",at(9),at(10)));
        leave.setStatus(UserScheduleStatus.CANCELLED);
        assertFalse(ReservationWindowPolicy.staffHasConflict(List.of(leave),"m",at(9),at(10)));
    }
}
