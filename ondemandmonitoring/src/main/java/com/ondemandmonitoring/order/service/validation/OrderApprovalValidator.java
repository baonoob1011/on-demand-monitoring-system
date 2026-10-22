package com.ondemandmonitoring.order.service.validation;

import com.ondemandmonitoring.common.exception.ApiException;
import com.ondemandmonitoring.common.exception.ErrorCode;
import com.ondemandmonitoring.order.domain.Order;
import com.ondemandmonitoring.order.enums.LegalReviewStatus;
import com.ondemandmonitoring.order.enums.OrderStatus;
import org.springframework.stereotype.Component;

@Component
public class OrderApprovalValidator {
    public void validate(Order order) {
        if (order.getOrderStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "Only PENDING orders can be approved");
        }
        LegalReviewStatus status = order.getLegalReviewStatus();
        if (status != LegalReviewStatus.NOT_REQUIRED
                && status != LegalReviewStatus.APPROVED
                && status != LegalReviewStatus.PENDING_PERMIT) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "Order legal review is not ready for approval");
        }
    }
}
