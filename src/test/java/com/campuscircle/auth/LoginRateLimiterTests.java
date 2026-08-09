package com.campuscircle.auth;

import com.campuscircle.common.ErrorCode;
import com.campuscircle.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTests {

    @Test
    void blocksRepeatedFailuresAndAllowsAResetAfterSuccessfulLogin() {
        LoginRateLimiter limiter = new LoginRateLimiter();
        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.recordFailure("Student_One");
        }

        assertThatThrownBy(() -> limiter.checkAllowed("student_one"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getErrorCode())
                .isEqualTo(ErrorCode.CONFLICT);

        limiter.clear("student_one");
        assertThatCode(() -> limiter.checkAllowed("Student_One")).doesNotThrowAnyException();
    }
}
