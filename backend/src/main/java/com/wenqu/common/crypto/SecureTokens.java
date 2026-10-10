package com.wenqu.common.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 生成随机令牌并计算用于存储的哈希。 */
public final class SecureTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecureTokens() {}

    /** 32 字节随机数的 base64url 编码。 */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 令牌本身是高熵随机数，SHA-256 足以防止数据库泄露后被直接使用。 */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
