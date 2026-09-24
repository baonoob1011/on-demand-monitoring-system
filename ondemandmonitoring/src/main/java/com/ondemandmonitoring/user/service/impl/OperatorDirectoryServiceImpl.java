package com.ondemandmonitoring.user.service.impl;

import com.ondemandmonitoring.role.domain.RoleCode;
import com.ondemandmonitoring.user.dto.response.AvailableOperatorResponse;
import com.ondemandmonitoring.user.repository.UserRepository;
import com.ondemandmonitoring.user.service.IOperatorDirectoryService;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class OperatorDirectoryServiceImpl implements IOperatorDirectoryService {

    UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<AvailableOperatorResponse> getAvailableOperators() {
        return userRepository.findAllByRole_CodeAndIsActiveTrueOrderByFullNameAsc(RoleCode.DRONE_OPERATOR)
                .stream()
                .map(user -> AvailableOperatorResponse.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .build())
                .toList();
    }
}
