package com.ondemandmonitoring.Consultation.services.impl;

import com.ondemandmonitoring.Consultation.domains.ConsultationMessage;
import com.ondemandmonitoring.Consultation.domains.CustomerConsultation;
import com.ondemandmonitoring.Consultation.dtos.requests.SendConsultationMessageRequest;
import com.ondemandmonitoring.Consultation.dtos.responses.CustomerConsultationResponse;
import com.ondemandmonitoring.Consultation.enums.ConsultationSenderType;
import com.ondemandmonitoring.Consultation.enums.ConsultationStatus;
import com.ondemandmonitoring.Consultation.mappers.CustomerConsultationMapper;
import com.ondemandmonitoring.Consultation.repositories.ConsultationMessageRepository;
import com.ondemandmonitoring.Consultation.repositories.CustomerConsultationRepository;
import com.ondemandmonitoring.Consultation.services.CustomerConsultationService;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerConsultationServiceImpl
        implements CustomerConsultationService {

    private final CustomerConsultationRepository consultationRepository;
    private final ConsultationMessageRepository messageRepository;
    private final CustomerConsultationMapper consultationMapper;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public CustomerConsultationResponse startConsultation() {

        User customer = getCurrentCustomer();

        CustomerConsultation consultation =
                CustomerConsultation.builder()
                        .customer(customer)
                        .status(ConsultationStatus.ACTIVE)
                        .build();

        CustomerConsultation saved =
                consultationRepository.save(consultation);

        return consultationMapper.toResponse(
                saved,
                List.of()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerConsultationResponse getConsultation(
            String consultationId
    ) {

        User customer = getCurrentCustomer();

        CustomerConsultation consultation =
                getOwnedConsultation(
                        consultationId,
                        customer.getId()
                );

        List<ConsultationMessage> messages =
                messageRepository
                        .findByConsultationIdOrderByCreatedAtAsc(
                                consultationId
                        );

        return consultationMapper.toResponse(
                consultation,
                messages
        );
    }

    @Override
    @Transactional
    public CustomerConsultationResponse sendMessage(
            String consultationId,
            SendConsultationMessageRequest request
    ) {

        User customer = getCurrentCustomer();

        CustomerConsultation consultation =
                getOwnedConsultation(
                        consultationId,
                        customer.getId()
                );

        if (consultation.getStatus()
                != ConsultationStatus.ACTIVE) {

            throw new IllegalStateException(
                    "Consultation is not active"
            );
        }

        ConsultationMessage message =
                ConsultationMessage.builder()
                        .consultation(consultation)
                        .senderType(
                                ConsultationSenderType.CUSTOMER
                        )
                        .message(request.getMessage().trim())
                        .build();

        messageRepository.save(message);

        List<ConsultationMessage> messages =
                messageRepository
                        .findByConsultationIdOrderByCreatedAtAsc(
                                consultationId
                        );

        return consultationMapper.toResponse(
                consultation,
                messages
        );
    }

    private CustomerConsultation getOwnedConsultation(
            String consultationId,
            String customerId
    ) {

        CustomerConsultation consultation =
                consultationRepository
                        .findById(consultationId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Consultation not found"
                                )
                        );

        if (!consultation
                .getCustomer()
                .getId()
                .equals(customerId)) {

            throw new IllegalArgumentException(
                    "Consultation not found"
            );
        }

        return consultation;
    }

    private User getCurrentCustomer() {

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new IllegalStateException(
                    "User is not authenticated"
            );
        }

        String email = authentication.getName();

        return userRepository
                .findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Authenticated user not found"
                        )
                );
    }
}