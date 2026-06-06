package org.rail.common.core.util.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.rail.common.core.exception.UnauthorizedException;
import org.rail.common.core.model.UserAuthInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 令牌生成与校验工具类
 */
@Component
public class JwtTokenUtil {

    private static String secret;
    private static long defaultExpiration;


    @Value("${jwt.secret:abcdefghijklmnopqrstuvwxyz1234567890ABCDEFG}")
    public void setSecret(String secret) {
        JwtTokenUtil.secret = secret;
    }

    @Value("${jwt.expiration:7200}")
    public void setDefaultExpiration(long defaultExpiration) {
        JwtTokenUtil.defaultExpiration = defaultExpiration;
    }

    /**
     * 生成签名密钥（HS256对称加密）
     */
    private static SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    /**
     * 生成token（使用默认过期时间）
     * @param userId 用户账号ID（用户唯一标识）
     * @param username  用户名
     * @return JWT令牌
     */
    public static String createToken(Long userId, String username) {
        // 调用重载方法，使用默认过期时间
        return createToken(userId, username, defaultExpiration);
    }

    /**
     * 生成token（支持自定义过期时间）
     * @param userId 用户账号ID
     * @param username 用户名
     * @param expiration 过期时间（单位：秒）
     * @return 生成的JWT令牌
     */
    public static String createToken(Long userId, String username, long expiration) {
        // 构建负载
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId); // 核心：用户标识藏在负载中
        claims.put("username", username);

        // 生成token（包含负载、过期时间、签名）
        return Jwts.builder()
                .setClaims(claims) // 负载
                .setIssuedAt(new Date()) // 签发时间
                .setExpiration(new Date(System.currentTimeMillis() + expiration * 1000)) // 过期时间
                .signWith(getSigningKey(), SignatureAlgorithm.HS256) // 签名防篡改
                .compact();
    }

    /**
     * 解析并校验token（仅需传入token，自动完成所有校验）
     * @param token 待校验的令牌
     * @return 解析出的用户唯一标识（userId）
     * @throws UnauthorizedException 校验失败时抛出（未登录/无效/过期）
     */
    public static UserAuthInfo parseToken(String token) {
        // 校验token是否为空
        if (token == null || token.trim().isEmpty()) {
            throw new UnauthorizedException("未登录：token为空");
        }

        try {
            // 解析token（自动校验签名）
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey()) // 用密钥校验签名
                    .build()
                    .parseClaimsJws(token) // 解析token，签名无效会直接抛异常
                    .getBody();

            Long userId = claims.get("userId", Long.class);
            String username = claims.get("username", String.class);

            if (userId == null || username == null) {
                throw new UnauthorizedException("无效的token：用户信息不完整");
            }

            UserAuthInfo authInfo = new UserAuthInfo();
            authInfo.setUserId(userId);
            authInfo.setUsername(username);
            return authInfo;

        } catch (ExpiredJwtException e) {
            // token已过期
            throw new UnauthorizedException("token已过期", e);
        } catch (JwtException | IllegalArgumentException e) {
            // 签名无效/格式错误/解析失败等
            throw new UnauthorizedException("无效的token", e);
        }
    }

    /**
     * 仅校验token是否有效
     * @param token 待校验的令牌
     * @return true=有效，false=无效
     */
    public static boolean isValid(String token) {
        try {
            parseToken(token);
            return true;
        } catch (UnauthorizedException e) {
            return false;
        }
    }
}