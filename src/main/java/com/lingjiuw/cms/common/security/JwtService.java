package com.lingjiuw.cms.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT 签发与解析（HS256）。
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final long expireMillis;

    public JwtService(@Value("${cms.jwt.secret}") String secret,
                      @Value("${cms.jwt.expire-hours}") long expireHours) {
        byte[] secretBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("cms.jwt.secret 长度不足：HS256 至少需要 32 字节，当前 "
                    + secretBytes.length + " 字节；请通过环境变量 CMS_JWT_SECRET 提供（至少 32 字节）");
        }
        if (expireHours <= 0) {
            throw new IllegalStateException("cms.jwt.expire-hours 必须为正数，当前值：" + expireHours);
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expireMillis = expireHours * 3600_000L;
    }

    public String generate(LoginUser user) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("nickname", user.getNickname())
                .claim("roles", user.getRoles())
                .claim("perms", user.getPerms())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并校验 token，无效/过期抛出 JwtException。
     */
    public LoginUser parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        return LoginUser.builder()
                .id(userId(claims))
                .username(claims.get("username", String.class))
                .nickname(claims.get("nickname", String.class))
                .roles(stringList(claims.get("roles")))
                .perms(stringList(claims.get("perms")))
                .build();
    }

    /** subject 必须是数字用户 id；不合法时同样按解析失败（JwtException）处理，调用方只捕获 JwtException 也不会漏判 */
    private static Long userId(Claims claims) {
        String subject = claims.getSubject();
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new JwtException("token 的 subject 不是合法的用户 id：" + subject, e);
        }
    }

    /** roles/perms 来自 token：非字符串或空值一律丢弃，避免下游 new SimpleGrantedAuthority 抛异常 */
    private static List<String> stringList(Object claim) {
        if (!(claim instanceof List<?> values)) {
            return null;
        }
        return values.stream()
                .filter(value -> value instanceof String text && !text.isBlank())
                .map(String.class::cast)
                .toList();
    }

    public long getExpireMillis() {
        return expireMillis;
    }
}
