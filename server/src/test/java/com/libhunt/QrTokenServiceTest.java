package com.libhunt;

import com.libhunt.service.QrTokenService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("现场二维码签名")
class QrTokenServiceTest {

    private static final String LEVEL_SECRET = "IQUAN1634";

    private final QrTokenService service = new QrTokenService("test-server-secret");

    @Test
    @DisplayName("签发的 token 可通过校验")
    void issuedTokenVerifies() {
        String token = service.issue(1, LEVEL_SECRET, Duration.ofDays(1));

        assertThat(service.verify(1, token, LEVEL_SECRET)).isTrue();
    }

    @Test
    @DisplayName("过期 token 被拒绝")
    void expiredTokenRejected() {
        String token = service.issue(1, LEVEL_SECRET, Duration.ofSeconds(-10));

        assertThat(service.verify(1, token, LEVEL_SECRET)).isFalse();
    }

    @Test
    @DisplayName("关卡号不一致被拒绝")
    void tokenForAnotherLevelRejected() {
        String token = service.issue(1, LEVEL_SECRET, Duration.ofDays(1));

        assertThat(service.verify(2, token, LEVEL_SECRET)).isFalse();
    }

    @Test
    @DisplayName("篡改签名被拒绝")
    void tamperedTokenRejected() {
        String token = service.issue(1, LEVEL_SECRET, Duration.ofDays(1));
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == '0' ? '1' : '0');

        assertThat(service.verify(1, tampered, LEVEL_SECRET)).isFalse();
    }

    @Test
    @DisplayName("使用错误的关卡密钥被拒绝")
    void wrongLevelSecretRejected() {
        String token = service.issue(1, LEVEL_SECRET, Duration.ofDays(1));

        assertThat(service.verify(1, token, "OTHER-SECRET")).isFalse();
    }

    @Test
    @DisplayName("大小写差异与首尾空白不影响校验")
    void caseAndOuterWhitespaceTolerated() {
        String token = service.issue(1, LEVEL_SECRET, Duration.ofDays(1));

        assertThat(service.verify(1, "  " + token.toUpperCase() + "  ", LEVEL_SECRET)).isTrue();
    }
}
