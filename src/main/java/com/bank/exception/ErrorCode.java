package com.bank.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // 500 - Lỗi hệ thống
    INTERNAL_SERVER_ERROR("SYS_500", "Đã xảy ra lỗi nội bộ hệ thống", HttpStatus.INTERNAL_SERVER_ERROR),

    // 400 - Dữ liệu không hợp lệ
    INVALID_REQUEST("REQ_400", "Dữ liệu yêu cầu không hợp lệ", HttpStatus.BAD_REQUEST),
    VALIDATION_FAILED("VAL_400", "Dữ liệu xác thực không hợp lệ", HttpStatus.BAD_REQUEST),
    MALFORMED_JSON("REQ_401", "Cấu trúc dữ liệu JSON không đúng định dạng", HttpStatus.BAD_REQUEST),

    // 401 & 403 - Xác thực & Phân quyền
    UNAUTHENTICATED("AUTH_401", "Chưa xác thực hoặc phiên đăng nhập hết hạn", HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS("AUTH_402", "Tên đăng nhập hoặc mật khẩu không chính xác", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("AUTH_403", "Bạn không có quyền thực hiện thao tác này", HttpStatus.FORBIDDEN),

    // 404 - Không tìm thấy
    USER_NOT_FOUND("USR_404", "Không tìm thấy thông tin người dùng", HttpStatus.NOT_FOUND),
    WALLET_NOT_FOUND("WLT_404", "Không tìm thấy thông tin ví", HttpStatus.NOT_FOUND),
    TRANSACTION_NOT_FOUND("TX_404", "Không tìm thấy giao dịch", HttpStatus.NOT_FOUND),

    // 409 - Xung đột / Nghiệp vụ ví
    USER_ALREADY_EXISTS("USR_409", "Tên đăng nhập hoặc email đã tồn tại", HttpStatus.CONFLICT),
    WALLET_LOCKED("WLT_409", "Ví đang bị khóa hoặc ngưng hoạt động", HttpStatus.BAD_REQUEST),
    SAME_WALLET_TRANSFER("TX_400", "Ví gửi và ví nhận không được trùng nhau", HttpStatus.BAD_REQUEST),
    INSUFFICIENT_BALANCE("TX_409", "Số dư trong ví không đủ để thực hiện giao dịch", HttpStatus.BAD_REQUEST),
    CURRENCY_MISMATCH("TX_410", "Đơn vị tiền tệ giữa 2 ví không khớp nhau", HttpStatus.BAD_REQUEST),
    TRANSACTION_PROCESSING("TX_411", "Giao dịch đang được xử lý, vui lòng không gửi lại", HttpStatus.CONFLICT),
    SYSTEM_BUSY("TX_412", "Hệ thống đang xử lý giao dịch cho ví này, vui lòng thử lại sau", HttpStatus.CONFLICT),
    TRANSACTION_LIMIT_EXCEEDED("TX_413", "Vượt quá hạn mức tối đa cho một giao dịch (tối đa 50.000.000 VND)", HttpStatus.BAD_REQUEST),
    DAILY_LIMIT_EXCEEDED("TX_414", "Vượt quá hạn mức giao dịch tối đa trong ngày (tối đa 100.000.000 VND/ngày)", HttpStatus.BAD_REQUEST),

    // OTP Error Codes
    OTP_INVALID("OTP_400", "Mã xác thực OTP không chính xác", HttpStatus.BAD_REQUEST),
    OTP_EXPIRED("OTP_401", "Mã OTP đã hết hạn hoặc chưa được yêu cầu tạo mới", HttpStatus.BAD_REQUEST),
    OTP_MAX_ATTEMPTS("OTP_429", "Bạn đã nhập sai OTP quá số lần cho phép (3 lần). Vui lòng yêu cầu mã OTP mới", HttpStatus.TOO_MANY_REQUESTS);

    private final String code;
    private final String message;
    private final HttpStatus httpStatus;

    ErrorCode(String code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
