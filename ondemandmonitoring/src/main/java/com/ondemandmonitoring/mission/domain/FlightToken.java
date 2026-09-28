package com.ondemandmonitoring.mission.domain;

import com.ondemandmonitoring.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

/**
 * Flight-Access Token issued by the system after a successful preflight check.
 *
 * Per the activity diagram:
 *   preflight all OK → Issue Flight Access Token → staff clicks "Start Mission"
 *   → system validates token → opens WebSocket telemetry + RTSP/WebRTC video stream.
 *
 * Token expires after TTL_SECONDS. If expired the staff member must re-run preflight.
 */
@Getter
@Setter
@Entity
@Table(name = "flight_tokens")
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FlightToken extends BaseEntity {

    @Column(name = "mission_id", nullable = false, length = 100)
    String missionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_device_assignment_id", nullable = false)
    MissionDeviceAssignment deviceAssignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mission_staff_assignment_id", nullable = false)
    MissionStaffAssignment staffAssignment;

    /** Unique, random token value checked before take-off. */
    @Column(name = "token_value", nullable = false, unique = true, length = 128)
    String tokenValue;

    @Column(name = "issued_at", nullable = false)
    Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    Instant expiresAt;

    /** True after the staff member clicks "Start Mission" and the token has been consumed. */
    @Column(name = "used", nullable = false)
    boolean used = false;

    /** True if the system explicitly revoked the token (e.g. operator missed the window). */
    @Column(name = "revoked", nullable = false)
    boolean revoked = false;



    public boolean isValid() {
        return !used && !revoked && Instant.now().isBefore(expiresAt);
    }
}
