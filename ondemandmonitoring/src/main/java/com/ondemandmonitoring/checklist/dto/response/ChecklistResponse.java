package com.ondemandmonitoring.checklist.dto.response;

import java.time.Instant;
import lombok.*;
import com.ondemandmonitoring.pricing.enums.ChecklistDifficulty;

@Getter
@Setter
@NoArgsConstructor
public class ChecklistResponse {
    private String id;
    private String content;
    private Boolean isActive;
    private ChecklistDifficulty difficulty;
    private Instant createdAt;
    private Instant updatedAt;
    private Long version;
}
