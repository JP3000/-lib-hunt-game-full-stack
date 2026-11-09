package com.libhunt.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 现场二维码的签发与校验。
 * <p>
 * 二维码内容不是固定密码，而是「格式版本 + 关卡号 + 过期时间 + HMAC 签名」：
 * <pre>v1.&lt;levelNo&gt;.&lt;expireEpochSecond&gt;.&lt;hex(HMAC-SHA256)&gt;</pre>
 * 签名密钥由服务端私钥与该关的 {@code qr_answer} 拼接而成，两者都不下发给客户端。
 * <p>
 * 这样即使二维码被拍照转发，也只在有效期内可用；想伪造其它关卡，
 * 需要同时拿到服务端私钥和数据库里对应关卡的密钥。
 */
@Service
public class QrTokenService {

    private static final String VERSION = "v1";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String serverSecret;

    public QrTokenService(@Value("${libhunt.qr.secret}") String serverSecret) {
        this.serverSecret = serverSecret;
    }

    /**
     * 签发一个带过期时间的二维码内容。
     */
    public String issue(int levelNo, String levelSecret, Duration ttl) {
        long expiresAt = Instant.now().plus(ttl).getEpochSecond();
        String payload = VERSION + "." + levelNo + "." + expiresAt;
        return payload + "." + sign(payload, levelSecret);
    }

    /**
     * 校验二维码内容：关卡号必须一致、未过期、签名匹配。
     * 十六进制签名按小写比较，因此扫码结果的大小写不影响校验。
     */
    public boolean verify(int levelNo, String token, String levelSecret) {
        if (token == null || levelSecret == null || levelSecret.isBlank()) {
            return false;
        }
        String[] parts = token.trim().toLowerCase(Locale.ROOT).split("\\.", -1);
        if (parts.length != 4 || !VERSION.equals(parts[0])) {
            return false;
        }
        try {
            if (Integer.parseInt(parts[1]) != levelNo) {
                return false;
            }
            if (Long.parseLong(parts[2]) <= Instant.now().getEpochSecond()) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        String payload = parts[0] + "." + parts[1] + "." + parts[2];
        String expected = sign(payload, levelSecret);
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                parts[3].getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String payload, String levelSecret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(
                    (serverSecret + "|" + levelSecret).getBytes(StandardCharsets.UTF_8),
                    HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("二维码签名失败", e);
        }
    }
}
