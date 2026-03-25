package org.rail.userservice.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.rail.userservice.exception.UnauthorizedException;
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
     * 生成token（核心：将userId藏在token内部）
     * @param userId 用户唯一标识（核心身份信息）
     * @return 生成的JWT令牌
     */
    public static String createToken(Long userId) {
        // 调用重载方法，使用默认过期时间
        return createToken(userId, defaultExpiration);
    }

    /**
     * 生成token（支持自定义过期时间）
     * @param userId 用户唯一标识
     * @param expiration 过期时间（单位：秒）
     * @return 生成的JWT令牌
     */
    public static String createToken(Long userId, long expiration) {
        // 1. 构建负载（将userId藏在这里）
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId); // 核心：用户标识藏在负载中

        // 2. 生成token（包含负载、过期时间、签名）
        return Jwts.builder()
                .setClaims(claims) // 负载：存放userId
                .setSubject(userId.toString()) // 主题：也存userId（可选，方便快速提取）
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
    public static Long parseToken(String token) {
        // 1. 校验token是否为空
        if (token == null || token.trim().isEmpty()) {
            throw new UnauthorizedException("未登录：token为空");
        }

        try {
            // 2. 解析token（自动校验签名）
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey()) // 用密钥校验签名
                    .build()
                    .parseClaimsJws(token) // 解析token，签名无效会直接抛异常
                    .getBody();

            // 3. 校验过期时间（JJWT会自动校验，若过期会抛ExpiredJwtException）
            // （这里无需手动校验，异常处理见下方catch块）

            // 4. 提取并返回userId（从负载中获取藏好的用户标识）
            Object userIdObj = claims.get("userId");
            if (userIdObj == null) {
                throw new UnauthorizedException("无效的token：未包含用户信息");
            }
            return Long.valueOf(userIdObj.toString());

        } catch (ExpiredJwtException e) {
            // token已过期
            throw new UnauthorizedException("token已过期", e);
        } catch (JwtException | IllegalArgumentException e) {
            // 签名无效/格式错误/解析失败等
            throw new UnauthorizedException("无效的token", e);
        }
    }

    /**
     * 仅校验token是否有效（不返回userId，用于快速判断）
     * @param token 待校验的令牌
     * @return true=有效，false=无效
     */
    public static boolean isValid(String token) {
        try {
            parseToken(token); // 复用parseToken的校验逻辑
            return true;
        } catch (UnauthorizedException e) {
            return false;
        }
    }
}