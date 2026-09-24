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

    EMAIL_ALREADY_EXISTS("Email đã được đăng ký", HttpStatus.CONFLICT),
    USER_NOT_FOUND("Không tìm thấy người dùng", HttpStatus.NOT_FOUND),
    INVALID_CREDENTIALS("Email hoặc mật khẩu không đúng", HttpStatus.UNAUTHORIZED),
    USER_NOT_CONFIRMED("Tài khoản chưa được xác thực email", HttpStatus.FORBIDDEN),
    ACCOUNT_DISABLED("Tài khoản đã bị vô hiệu hóa", HttpStatus.FORBIDDEN),
    SELF_DEACTIVATION_NOT_ALLOWED("Không thể vô hiệu hóa tài khoản của chính bạn", HttpStatus.CONFLICT),
    OTP_INVALID("Mã OTP không hợp lệ", HttpStatus.BAD_REQUEST),
    OTP_EXPIRED("Mã OTP đã hết hạn", HttpStatus.BAD_REQUEST),
    USER_ALREADY_CONFIRMED("Tài khoản đã được xác thực", HttpStatus.CONFLICT),
    PASSWORD_POLICY_VIOLATED("Mật khẩu không đáp ứng chính sách bảo mật", HttpStatus.BAD_REQUEST),
    REFRESH_TOKEN_INVALID("Refresh token không hợp lệ hoặc đã hết hạn", HttpStatus.UNAUTHORIZED),
    AUTH_PROVIDER_ERROR("Không thể kết nối nhà cung cấp xác thực", HttpStatus.BAD_GATEWAY),
    SOCIAL_EMAIL_NOT_VERIFIED("Email social chưa được xác thực", HttpStatus.UNAUTHORIZED),
    SOCIAL_PROVIDER_UNSUPPORTED("Nhà cung cấp social chưa được hỗ trợ", HttpStatus.BAD_REQUEST),

    INTERNAL_SERVER_ERROR("Lỗi hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),

    /**
     * Mission Error Code
     *
     */
    MISSION_NOT_FOUND("Mission not found", HttpStatus.NOT_FOUND),
    MISSION_STATUS_INVALID("Mission status is invalid for this operation", HttpStatus.CONFLICT),
    CONCURRENT_UPDATE("Resource changed during this request; refresh and retry", HttpStatus.CONFLICT),
    DRONE_NOT_AVAILABLE("Drone is not available", HttpStatus.CONFLICT),
    SCHEDULE_CONFLICT("The drone is already scheduled for another mission during this time period",
            HttpStatus.CONFLICT),
    MEDIA_UPLOAD_FAILED("File upload failed after 3 attempts", HttpStatus.BAD_GATEWAY),
    MEDIA_NOT_FOUND("Media not found", HttpStatus.NOT_FOUND),
    MEDIA_UPLOAD_NOT_ALLOWED("Media upload is not allowed", HttpStatus.CONFLICT),
    MEDIA_UPLOAD_ATTEMPT_INVALID("Media upload attempt is invalid", HttpStatus.CONFLICT),
    MEDIA_IDEMPOTENCY_CONFLICT("Local media ID conflicts with existing metadata", HttpStatus.CONFLICT),
    DRONE_MODEL_NOT_FOUND("Drone model not found", HttpStatus.NOT_FOUND),
    DRONE_PAYLOAD_NOT_FOUND("Drone payload not found", HttpStatus.NOT_FOUND),
    DRONE_NOT_FOUND("Drone not found", HttpStatus.NOT_FOUND),

    /**
     * Service & DeliverableType Error Codes
     */
    SERVICE_NOT_FOUND("Service not found", HttpStatus.NOT_FOUND),
    SERVICE_ALREADY_EXISTS("Service already exists", HttpStatus.CONFLICT),
    DELIVERABLE_TYPE_NOT_FOUND("Deliverable type not found", HttpStatus.NOT_FOUND),
    DELIVERABLE_TYPE_ALREADY_EXISTS("Deliverable type already exists", HttpStatus.CONFLICT),
    SERVICE_DELIVERABLE_ALREADY_EXISTS("Service deliverable link already exists", HttpStatus.CONFLICT),

    DEVICE_TYPE_NOT_FOUND("Device type not found", HttpStatus.NOT_FOUND),
    DEVICE_TYPE_CODE_EXISTS("Device type code already exists", HttpStatus.CONFLICT),
    DEVICE_MODEL_NOT_FOUND("Device model not found", HttpStatus.NOT_FOUND),
    DEVICE_MODEL_CODE_EXISTS("Device model code already exists", HttpStatus.CONFLICT),
    DEVICE_NOT_FOUND("Device not found", HttpStatus.NOT_FOUND),
    DEVICE_SERIAL_NUMBER_EXISTS("Device serial number already exists", HttpStatus.CONFLICT);

    String message;
    HttpStatus status;
}
