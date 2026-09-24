package com.ondemandmonitoring.user.service;

import com.ondemandmonitoring.user.dto.response.AvailableOperatorResponse;
import java.util.List;

public interface IOperatorDirectoryService {

    List<AvailableOperatorResponse> getAvailableOperators();
}
