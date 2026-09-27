package com.ondemandmonitoring.mission.dto.response;

import lombok.Value;

/** Public, immutable mission context; does not expose persistence entities. */
@Value
public class MissionMediaContext {
    String id;
    boolean captureAllowed;
}
