package com.ondemandmonitoring.finance.service;

import com.ondemandmonitoring.finance.domain.Invoice;
import com.ondemandmonitoring.mission.domain.Mission;

/** Central business policy for deposit satisfaction and mission unlock. */
public interface IMissionPaymentEligibilityService {
    boolean isDepositSatisfied(Invoice invoice);
    boolean canUnlockMission(Invoice invoice, Mission mission);
    boolean unlockIfEligible(Invoice invoice, Mission mission);
}
