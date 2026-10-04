package com.ondemandmonitoring.checklist.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
public class ServiceChecklistResponse {
    private String id;
    private String serviceId;
    private String serviceName;
    private Boolean serviceActive;
    private String checklistId;
    private String content;
    private Boolean checklistActive;
    private int displayOrder;
    private Long version;
}
