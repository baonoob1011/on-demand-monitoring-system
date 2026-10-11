package com.ondemandmonitoring.delivery.enums;

/**
 * Represents the business lifecycle of an order's result delivery.
 *
 * <p>The status advances independently from the mission status so that review,
 * final payment, and access to original media can be enforced explicitly.</p>
 */
public enum DeliveryStatus {
    PROCESSING,
    READY_FOR_MANAGER_REVIEW,
    CUSTOMER_REVIEW,
    REVISION_REQUESTED,
    FINAL_PAYMENT_PENDING,
    PAYMENT_CONFIRMED,
    READY_FOR_DELIVERY,
    DELIVERED
}
