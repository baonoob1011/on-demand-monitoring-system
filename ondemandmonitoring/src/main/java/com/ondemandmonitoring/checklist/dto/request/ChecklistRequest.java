package com.ondemandmonitoring.checklist.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import com.ondemandmonitoring.pricing.enums.ChecklistDifficulty;

@Getter
@Setter
@NoArgsConstructor
public class ChecklistRequest {
    @NotBlank
    @Size(max = 500)
    private String content;
    private ChecklistDifficulty difficulty;
}
