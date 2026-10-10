package com.wenqu.auth.service;

import com.wenqu.auth.config.AuthProperties;
import com.wenqu.auth.domain.IssuedTokens;
import com.wenqu.auth.domain.RefreshToken;
import com.wenqu.auth.domain.User;
import com.wenqu.auth.repository.RefreshTokenRepository;
import com.wenqu.auth.repository.UserRepository;
import com.wenqu.common.crypto.SecureTokens;
import com.wenqu.common.error.BusinessException;
import com.wenqu.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注册、登录、刷新与退出。
 *
 * <p>Refresh Token 每次刷新都轮换；已作废的 Token 被再次使用说明可能被盗用，作废整个 family，迫使所有持有者重新登录。
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final AuthProperties properties;
    private final Clock clock;

    /** 邮箱不存在时也执行一次 BCrypt 校验，使响应时间与密码错误一致，避免据此探测邮箱是否注册。 */
    private final String dummyPasswordHash;

    public AuthService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenIssuer accessTokenIssuer,
            AuthProperties properties,
            Clock clock) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(SecureTokens.generate());
    }

    @Transactional
    public IssuedTokens register(String email, String password, String displayName) {
        String passwordHash = passwordEncoder.encode(password);
        User user = userRepository
                .insertIfEmailAbsent(email.strip(), passwordHash, displayName.strip())
                .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED));
        return issue(user, UUID.randomUUID());
    }

    @Transactional
    public IssuedTokens login(String email, String password) {
        User user = userRepository.findByEmail(email.strip()).orElse(null);
        String hash = user != null ? user.passwordHash() : dummyPasswordHash;
        if (!passwordEncoder.matches(password, hash) || user == null) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        return issue(user, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = BusinessException.class)
    public IssuedTokens refresh(String rawRefreshToken) {
        Instant now = clock.instant();
        RefreshToken token = refreshTokenRepository
                .findByHashForUpdate(SecureTokens.hash(rawRefreshToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        if (token.isRevoked()) {
            refreshTokenRepository.revokeFamily(token.familyId(), now);
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (token.isExpiredAt(now)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        refreshTokenRepository.revoke(token.id(), now);
        User user = userRepository
                .findById(token.userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));
        return issue(user, token.familyId());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenRepository
                .findByHashForUpdate(SecureTokens.hash(rawRefreshToken))
                .ifPresent(token -> refreshTokenRepository.revokeFamily(token.familyId(), clock.instant()));
    }

    private IssuedTokens issue(User user, UUID familyId) {
        Instant now = clock.instant();
        String refreshToken = SecureTokens.generate();
        Instant refreshTokenExpiresAt = now.plus(properties.refreshTokenTtl());
        refreshTokenRepository.insert(user.id(), SecureTokens.hash(refreshToken), familyId, refreshTokenExpiresAt);
        return new IssuedTokens(
                user,
                accessTokenIssuer.issue(user.id(), now),
                accessTokenIssuer.expiresAt(now),
                refreshToken,
                refreshTokenExpiresAt);
    }
}
