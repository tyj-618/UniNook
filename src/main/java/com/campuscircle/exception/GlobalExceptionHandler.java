package com.campuscircle.exception;

import com.campuscircle.common.ApiResponse;
import com.campuscircle.common.ErrorCode;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.validation.BindException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Optional;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        return ResponseEntity.status(statusOf(exception.getErrorCode()))
                .body(ApiResponse.fail(exception.getErrorCode().code(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(ErrorCode.PARAM_ERROR.message());
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.PARAM_ERROR.code(), message));
    }

    @ExceptionHandler({BindException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiResponse<Void>> handleValidationException(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.PARAM_ERROR.code(), ErrorCode.PARAM_ERROR.message()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.fail(ErrorCode.PARAM_ERROR.code(), "请使用 multipart/form-data 提交图片"));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MaxUploadSizeExceededException.class, MultipartException.class})
    public ResponseEntity<ApiResponse<Void>> handleMultipartException(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.fail(ErrorCode.PARAM_ERROR.code(), "请提交不超过 2MB 的 PNG 或 JPEG 图片"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.fail(ErrorCode.INTERNAL_ERROR.code(), ErrorCode.INTERNAL_ERROR.message()));
    }

    private HttpStatus statusOf(ErrorCode errorCode) {
        return switch (errorCode) {
            case PARAM_ERROR -> HttpStatus.BAD_REQUEST;
            case AUTH_FAILED, UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case USERNAME_EXISTS, CONFLICT -> HttpStatus.CONFLICT;
            case INTERNAL_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
