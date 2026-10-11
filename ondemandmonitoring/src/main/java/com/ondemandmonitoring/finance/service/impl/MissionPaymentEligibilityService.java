package com.ondemandmonitoring.finance.service.impl;

import com.ondemandmonitoring.finance.domain.Invoice;
import com.ondemandmonitoring.finance.enums.QuoteStatus;
import com.ondemandmonitoring.finance.service.IMissionPaymentEligibilityService;
import com.ondemandmonitoring.mission.domain.Mission;
import com.ondemandmonitoring.mission.enums.MissionStatus;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class MissionPaymentEligibilityService implements IMissionPaymentEligibilityService {
    public boolean isDepositSatisfied(Invoice invoice) {
        return invoice != null && invoice.getPaidAmount() != null && invoice.getDepositAmount() != null
                && invoice.getPaidAmount().compareTo(invoice.getDepositAmount()) >= 0;
    }

    public boolean canUnlockMission(Invoice invoice, Mission mission) {
        return invoice != null && invoice.getQuote() != null
                && invoice.getQuote().getStatus() == QuoteStatus.ACCEPTED_BY_CUSTOMER
                && invoice.getTotalAmount().compareTo(BigDecimal.ZERO) > 0
                && isDepositSatisfied(invoice)
                && mission != null && mission.getStatus() == MissionStatus.WAITING_DEPOSIT
                && mission.getOrder().getId().equals(invoice.getOrder().getId());
    }

    public boolean unlockIfEligible(Invoice invoice, Mission mission) {
        if (!canUnlockMission(invoice, mission)) return false;
        mission.setStatus(MissionStatus.RESOURCE_ASSIGNING);
        return true;
    }
}
