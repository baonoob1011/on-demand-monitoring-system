package com.ondemandmonitoring.Consultation.mappers;

import com.ondemandmonitoring.Consultation.domains.ConsultationMessage;
import com.ondemandmonitoring.Consultation.domains.CustomerConsultation;
import com.ondemandmonitoring.Consultation.dtos.responses.ConsultationMessageResponse;
import com.ondemandmonitoring.Consultation.dtos.responses.CustomerConsultationResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CustomerConsultationMapper {

    ConsultationMessageResponse toMessageResponse(
            ConsultationMessage message
    );

    List<ConsultationMessageResponse> toMessageResponses(
            List<ConsultationMessage> messages
    );

    @Mapping(
            target = "customerId",
            source = "consultation.customer.id"
    )
    @Mapping(
            target = "orderId",
            source = "consultation.order.id"
    )
    @Mapping(
            target = "recommendedServiceId",
            source = "consultation.recommendedService.id"
    )
    @Mapping(
            target = "recommendedServiceName",
            source = "consultation.recommendedService.name"
    )
    @Mapping(
            target = "messages",
            source = "messages"
    )
    CustomerConsultationResponse toResponse(
            CustomerConsultation consultation,
            List<ConsultationMessage> messages
    );
}