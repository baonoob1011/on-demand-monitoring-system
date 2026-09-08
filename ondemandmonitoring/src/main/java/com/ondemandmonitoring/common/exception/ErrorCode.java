package com.ondemandmonitoring.common.exception;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public enum ErrorCode {

    INVALID_REQUEST("Yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR("Dữ liệu không hợp lệ", HttpStatus.BAD_REQUEST),

    UNAUTHORIZED("Bạn chưa đăng nhập", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("Bạn không có quyền thực hiện", HttpStatus.FORBIDDEN),

    RESOURCE_NOT_FOUND("Không tìm thấy dữ liệu", HttpStatus.NOT_FOUND),
    RESOURCE_ALREADY_EXISTS("Dữ liệu đã tồn tại", HttpStatus.CONFLICT),

    INTERNAL_SERVER_ERROR("Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),


    /**
     *  Mission Error Code
     *
     * */
    MISSION_NOT_FOUND("Mission not found", HttpStatus.NOT_FOUND),
    MISSION_STATUS_INVALID("Mission status is invalid for this operation", HttpStatus.CONFLICT),
    DRONE_NOT_AVAILABLE("Drone is not available", HttpStatus.CONFLICT),
    SCHEDULE_CONFLICT("The drone is already scheduled for another mission during this time period",
                      HttpStatus.CONFLICT),
    MEDIA_UPLOAD_FAILED("File upload failed after 3 attempts", HttpStatus.BAD_GATEWAY);




    String message;
    HttpStatus status;
}
