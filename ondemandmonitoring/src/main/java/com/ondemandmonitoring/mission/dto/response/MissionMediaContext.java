package com.ondemandmonitoring.mission.dto.response;

import com.ondemandmonitoring.mission.enums.MissionStatus;
import lombok.Getter;

/** Public, immutable mission context; does not expose persistence entities. */
@Getter
public final class MissionMediaContext {
    private final String id;
    private final boolean captureAllowed;
    private final MissionStatus status;

    public MissionMediaContext(String id, boolean captureAllowed) {
        this(id, captureAllowed, null);
    }

    public MissionMediaContext(String id, boolean captureAllowed, MissionStatus status) {
        this.id = id;
        this.captureAllowed = captureAllowed;
        this.status = status;
    }
}
