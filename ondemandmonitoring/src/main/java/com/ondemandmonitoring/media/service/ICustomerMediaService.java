package com.ondemandmonitoring.media.service;

import com.ondemandmonitoring.media.dto.CustomerMediaNotificationResponse;
import com.ondemandmonitoring.media.dto.CustomerMediaResponse;
import java.util.List;

public interface ICustomerMediaService {
    List<CustomerMediaResponse> listAvailable(String missionId);
    CustomerMediaResponse getAvailable(String mediaId);
    List<CustomerMediaNotificationResponse> listNotifications(String missionId);
}
