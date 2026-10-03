package com.ondemandmonitoring.order.service.impl;

import com.ondemandmonitoring.mission.service.IMissionAuthorizationService;
import com.ondemandmonitoring.order.repository.OrderRepository;
import com.ondemandmonitoring.order.service.IOrderAuthorizationService;
import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.domain.User;
import com.ondemandmonitoring.user.service.AuthenticatedUserResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service("orderAuthorizationService")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderAuthorizationService implements IOrderAuthorizationService {
    private final OrderRepository orders;
    private final AuthenticatedUserResolver currentUser;
    private final IMissionAuthorizationService missionAuthorization;

    @Override
    public boolean canViewOrder(String orderId) {
        User user = currentUser.getCurrentUser();
        return orders.findById(orderId).map(order -> {
            RoleCode role = user.getRole().getCode();
            if (role == RoleCode.ADMIN || role == RoleCode.MANAGER) return true;
            if (role == RoleCode.CUSTOMER) {
                return order.getCustomer() != null && user.getId().equals(order.getCustomer().getId());
            }
            return role == RoleCode.STAFF && missionAuthorization.canViewMission(orderId);
        }).orElse(false);
    }
}
