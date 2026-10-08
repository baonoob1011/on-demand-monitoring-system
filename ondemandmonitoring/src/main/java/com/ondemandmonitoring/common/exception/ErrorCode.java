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
    METHOD_NOT_ALLOWED("Phương thức HTTP không được hỗ trợ", HttpStatus.METHOD_NOT_ALLOWED),
    EMAIL_ALREADY_EXISTS("Email đã được đăng ký", HttpStatus.CONFLICT),
    LOCAL_IDENTITY_LINK_REQUIRED(
            "Email đã đăng ký bằng Google. Hãy đăng nhập bằng Google để thiết lập mật khẩu đăng nhập local",
            HttpStatus.CONFLICT),
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
    CHECKLIST_EXECUTION_NOT_FOUND("Checklist execution not found", HttpStatus.NOT_FOUND),
    CHECKLIST_EXECUTION_PARENT_MISMATCH("Execution does not belong to this mission", HttpStatus.FORBIDDEN),
    CHECKLIST_EXECUTION_TRANSITION_INVALID("Invalid checklist execution transition", HttpStatus.CONFLICT),
    CHECKLIST_EXECUTION_LOCKED("Checklist is locked for result review", HttpStatus.CONFLICT),
    CHECKLIST_EXECUTION_INTEGRITY_INVALID("Checklist execution does not match the order snapshot", HttpStatus.CONFLICT),
    CHECKLIST_NOT_READY("Monitoring checklist is not ready for result submission", HttpStatus.CONFLICT),
    EVIDENCE_NOT_FOUND("Checklist evidence not found", HttpStatus.NOT_FOUND),
    EVIDENCE_NOT_ELIGIBLE("Media is not eligible for checklist evidence", HttpStatus.CONFLICT),
    MEDIA_EVIDENCE_PROTECTED("Media is retained by checklist evidence history", HttpStatus.CONFLICT),
    SCHEDULE_CONFLICT("The Device is already scheduled for another mission during this time period",
            HttpStatus.CONFLICT),
    MEDIA_UPLOAD_FAILED("File upload failed after 3 attempts", HttpStatus.BAD_GATEWAY),
    MEDIA_NOT_FOUND("Media not found", HttpStatus.NOT_FOUND),
    MEDIA_UPLOAD_NOT_ALLOWED("Media upload is not allowed", HttpStatus.CONFLICT),
    MEDIA_UPLOAD_ATTEMPT_INVALID("Media upload attempt is invalid", HttpStatus.CONFLICT),
    MEDIA_IDEMPOTENCY_CONFLICT("Local media ID conflicts with existing metadata", HttpStatus.CONFLICT),
    MEDIA_PROBE_INVALID("Media probe payload is invalid", HttpStatus.BAD_REQUEST),
    MEDIA_PROBE_FAILED("Media storage round-trip probe failed", HttpStatus.BAD_GATEWAY),
    MAPILLARY_UNAVAILABLE("Dịch vụ ảnh tham chiếu Mapillary hiện không khả dụng", HttpStatus.BAD_GATEWAY),
    MAPILLARY_IMAGE_NOT_FOUND("Không tìm thấy ảnh tham chiếu gần vị trí hiện tại của drone.", HttpStatus.NOT_FOUND),
    WEATHER_PROVIDER_UNAVAILABLE("Hiện chưa thể tải dự báo thời tiết", HttpStatus.BAD_GATEWAY),
    DRONE_TELEMETRY_UNAVAILABLE("Chưa có vị trí GPS hiện tại của drone.", HttpStatus.CONFLICT),
    DRONE_TELEMETRY_STALE("Telemetry của drone đã quá cũ. Vui lòng chờ kết nối realtime.", HttpStatus.CONFLICT),
    /**
     * Service & DeliverableType Error Codes
     */
    SERVICE_NOT_FOUND("Service not found", HttpStatus.NOT_FOUND),
    ORDER_CHECKLIST_LOCKED("Order checklist snapshot is immutable", HttpStatus.CONFLICT),
    ORDER_CHECKLIST_TEMPLATE_CHANGED("Service checklist changed; refresh requirements and retry", HttpStatus.CONFLICT),
    ORDER_CHECKLIST_REVIEW_INVALID("Order checklist review is incomplete or invalid", HttpStatus.CONFLICT),
    QUOTE_NOT_FOUND("Quote not found", HttpStatus.NOT_FOUND),
    QUOTE_NOT_CURRENT("Quote is no longer current", HttpStatus.CONFLICT),
    QUOTE_NOT_APPROVED("Only an approved quote can be accepted", HttpStatus.CONFLICT),
    QUOTE_ALREADY_ACCEPTED("Quote has already been accepted", HttpStatus.CONFLICT),
    QUOTE_PRICING_INVALID("Quote pricing is invalid", HttpStatus.BAD_REQUEST),
    INVOICE_NOT_FOUND("Invoice not found", HttpStatus.NOT_FOUND),
    INVOICE_NOT_PAYABLE("Invoice is not payable", HttpStatus.CONFLICT),
    INVOICE_ALREADY_PAID("Invoice is already paid", HttpStatus.CONFLICT),
    DEPOSIT_ALREADY_SATISFIED("Required deposit has already been paid", HttpStatus.CONFLICT),
    ACTIVE_PAYMENT_EXISTS("An active payment already exists", HttpStatus.CONFLICT),
    PAYMENT_NOT_FOUND("Payment not found", HttpStatus.NOT_FOUND),
    PAYMENT_AMOUNT_MISMATCH("Payment amount does not match the expected amount", HttpStatus.CONFLICT),
    PAYMENT_WEBHOOK_INVALID("Invalid payment webhook signature", HttpStatus.UNAUTHORIZED),
    PAYMENT_PROVIDER_ERROR("Payment provider is unavailable", HttpStatus.BAD_GATEWAY),
    FINAL_PAYMENT_NOT_ALLOWED("Final payment is available after mission completion", HttpStatus.CONFLICT),
    MISSION_NOT_COMPLETED("Mission must be completed", HttpStatus.CONFLICT),
    DELIVERY_NOT_READY("Delivery is not ready", HttpStatus.CONFLICT),
    DELIVERY_ALREADY_RELEASED("Deliverables have already been released", HttpStatus.CONFLICT),
    RESULT_NOT_READY_FOR_REVIEW("Result is not ready for customer review", HttpStatus.CONFLICT),
    RESULT_ALREADY_ACCEPTED("Result has already been accepted", HttpStatus.CONFLICT),
    REVISION_NOT_ALLOWED("A revision cannot be requested in the current state", HttpStatus.CONFLICT),
    FINAL_PAYMENT_REQUIRED("Final payment is required", HttpStatus.PAYMENT_REQUIRED),
    INVOICE_NOT_PAID("Invoice has not been paid", HttpStatus.PAYMENT_REQUIRED),
    DELIVERABLE_NOT_RELEASED("Original deliverables have not been released", HttpStatus.FORBIDDEN),
    MEDIA_ACCESS_DENIED("Media access is denied", HttpStatus.FORBIDDEN),
    INVALID_DELIVERY_STATE("Invalid delivery state transition", HttpStatus.CONFLICT),
    SERVICE_INACTIVE("Service is inactive", HttpStatus.CONFLICT),
    CHECKLIST_NOT_FOUND("Checklist not found", HttpStatus.NOT_FOUND),
    CHECKLIST_ALREADY_EXISTS("Checklist content already exists", HttpStatus.CONFLICT),
    CHECKLIST_INACTIVE("Checklist is inactive", HttpStatus.CONFLICT),
    SERVICE_CHECKLIST_NOT_FOUND("Service checklist assignment not found", HttpStatus.NOT_FOUND),
    SERVICE_CHECKLIST_ALREADY_EXISTS("Checklist is already assigned to this service", HttpStatus.CONFLICT),
    SERVICE_IMAGE_STORAGE_ERROR("Unable to store service illustration", HttpStatus.BAD_GATEWAY),
    SERVICE_ALREADY_EXISTS("Service already exists", HttpStatus.CONFLICT),
    DELIVERABLE_TYPE_NOT_FOUND("Deliverable type not found", HttpStatus.NOT_FOUND),
    DELIVERABLE_TYPE_ALREADY_EXISTS("Deliverable type already exists", HttpStatus.CONFLICT),
    SERVICE_DELIVERABLE_ALREADY_EXISTS("Service deliverable link already exists", HttpStatus.CONFLICT),

    DEVICE_TYPE_NOT_FOUND("Device type not found", HttpStatus.NOT_FOUND),
    DEVICE_TYPE_CODE_EXISTS("Device type code already exists", HttpStatus.CONFLICT),
    DEVICE_MODEL_NOT_FOUND("Device model not found", HttpStatus.NOT_FOUND),
    DEVICE_MODEL_CODE_EXISTS("Device model code already exists", HttpStatus.CONFLICT),
    DEVICE_NOT_FOUND("Device not found", HttpStatus.NOT_FOUND),
    DEVICE_SERIAL_NUMBER_EXISTS("Device serial number already exists", HttpStatus.CONFLICT),
    /**
     * Support Ticket Error Codes
     */
    SUPPORT_TICKET_NOT_FOUND("Support ticket not found with specified ID", HttpStatus.NOT_FOUND),
    DEVICE_NOT_AVAILABLE("Device is not available", HttpStatus.CONFLICT);

    String message;
    HttpStatus status;
}
