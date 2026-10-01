package com.ondemandmonitoring.support.service;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.support.dto.*;

import java.util.List;

/**
 * Service interface defining business contract for Customer Support Ticket lifecycle management.
 * Provides capabilities to create support tickets, list tickets by filter, append conversation messages,
 * and update ticket status or assigned operational staff.
 */
public interface ISupportTicketService {

    /**
     * Creates a new customer support ticket with associated order and mission operational context.
     * Automatically generates a unique ticket tracking code and saves the initial customer description message.
     *
     * @param request Payload containing ticket details, category, priority, customer info, and optional order/mission IDs.
     * @return DTO representation of the created support ticket.
     * @throws ApiException if request payload validation fails or invalid references are provided.
     */
    SupportTicketDto createTicket(CreateSupportTicketRequest request);

    /**
     * Retrieves all support tickets associated with a specific customer account.
     *
     * @param customerId Unique identifier of the customer account.
     * @return List of support ticket DTOs ordered by opening timestamp descending.
     */
    List<SupportTicketDto> getCustomerTickets(String customerId);

    /**
     * Retrieves support tickets for operational staff and system administrators, filtered by status.
     *
     * @param statusFilter Optional status filter (e.g., OPEN, IN_PROGRESS, WAITING_FOR_CUSTOMER, RESOLVED). Pass "ALL" or null for unfiltered queue.
     * @return List of support ticket DTOs matching the given criteria.
     */
    List<SupportTicketDto> getAllTickets(String statusFilter);

    /**
     * Retrieves a specific support ticket by its database identifier along with its full conversation message history.
     *
     * @param id Unique identifier of the support ticket.
     * @return Support ticket DTO with nested list of conversation message DTOs.
     * @throws ApiException with ErrorCode.SUPPORT_TICKET_NOT_FOUND if ticket with given ID does not exist.
     */
    SupportTicketDto getTicketById(String id);

    /**
     * Appends a new conversation message to an existing support ticket thread.
     * Automatically updates ticket status based on sender role (e.g., sets WAITING_FOR_CUSTOMER if sent by staff).
     *
     * @param ticketId Unique identifier of the support ticket thread.
     * @param request Payload containing message content, sender ID, sender name, sender role, and optional attachment URL.
     * @return DTO representation of the created conversation message.
     * @throws ApiException with ErrorCode.SUPPORT_TICKET_NOT_FOUND if ticket with given ID does not exist.
     */
    SupportMessageDto addMessage(String ticketId, AddMessageRequest request);

    /**
     * Updates operational status, priority, assigned staff member, or resolution notes of a support ticket.
     *
     * @param ticketId Unique identifier of the support ticket to update.
     * @param request Payload containing optional new status, assigned staff details, priority, or resolution notes.
     * @return Updated support ticket DTO.
     * @throws ApiException with ErrorCode.SUPPORT_TICKET_NOT_FOUND if ticket with given ID does not exist.
     */
    SupportTicketDto updateTicketStatus(String ticketId, UpdateTicketStatusRequest request);

    /**
     * Persists customer feedback vote on an FAQ article to PostgreSQL database.
     *
     * @param request Payload containing articleId, articleQuestion, customerId, and isHelpful boolean.
     */
    void recordFaqFeedback(FaqFeedbackRequest request);

    /**
     * Computes real-time support analytics, SLA response times, and FAQ deflection statistics from PostgreSQL database.
     *
     * @return SupportAnalyticsDto containing computed deflection rate, SLA metrics, and top FAQs.
     */
    SupportAnalyticsDto getSupportAnalytics();

    /**
     * Retrieves published FAQ articles from PostgreSQL database with optional category and keyword search filtering.
     *
     * @param category Optional category filter ("ORDERS", "MISSIONS", etc.)
     * @param searchQuery Optional search keyword query string
     * @return List of FaqArticleDto objects.
     */
    List<FaqArticleDto> getFaqArticles(String category, String searchQuery);
}
