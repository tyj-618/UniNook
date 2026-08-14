package com.uninook.exception;

import com.uninook.common.ApiResponse;
import com.uninook.common.ErrorCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void returnsFirstFieldErrorMessageForMethodArgumentNotValidException() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(new BindException(new Object(), "request"));
        exception.getBindingResult().addError(new FieldError("request", "username", "用户名不能为空"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentNotValidException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.PARAM_ERROR.code());
        assertThat(response.getBody().message()).isEqualTo("用户名不能为空");
    }

    @Test
    void returnsFirstFieldErrorMessageForBindException() {
        BindException exception = new BindException(new Object(), "request");
        exception.addError(new FieldError("request", "nickname", "昵称长度不能超过 32 位"));

        ResponseEntity<ApiResponse<Void>> response = handler.handleBindException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("昵称长度不能超过 32 位");
    }

    @Test
    void returnsFirstConstraintViolationMessage() {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        when(violation.getMessage()).thenReturn("搜索关键词不能为空");
        ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));

        ResponseEntity<ApiResponse<Void>> response = handler.handleConstraintViolationException(exception);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("搜索关键词不能为空");
    }

    @Test
    void fallsBackToDefaultParamErrorMessageWhenValidationHasNoMessage() {
        ConstraintViolationException exception = new ConstraintViolationException(Set.of());

        ResponseEntity<ApiResponse<Void>> response = handler.handleConstraintViolationException(exception);

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(ErrorCode.PARAM_ERROR.message());
    }
}
