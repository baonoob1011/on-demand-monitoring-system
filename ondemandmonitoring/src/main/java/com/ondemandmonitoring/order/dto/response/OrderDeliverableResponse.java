package com.ondemandmonitoring.order.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Response object for order deliverable details")
public class OrderDeliverableResponse {

    String id;
    String deliverableTypeId;
    String deliverableTypeName;
    String defaultFormat;
    Map<String, Object> requirement;
}
