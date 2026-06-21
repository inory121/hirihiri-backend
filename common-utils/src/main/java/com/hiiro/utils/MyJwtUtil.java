package com.hiiro.utils;

import cn.hutool.core.convert.NumberWithFormat;
import cn.hutool.core.date.DateUtil;
import cn.hutool.crypto.digest.DigestAlgorithm;
import cn.hutool.crypto.digest.Digester;
import cn.hutool.jwt.JWTUtil;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Component
public class MyJwtUtil {

    private static final long JWT_EXPIRE_SECONDS = 10 * 24 * 60 * 60L;

    @Value("${jwt.secret}")
    private String secretKey;

    @Resource
    RedisUtil redisUtil;

    /**
     * 创建JWT令牌
     *
     * @param claims JWT声明，包含如用户ID、角色等信息
     * @return 生成的JWT令牌
     */
    public String createJwtToken(Map<String, Object> claims) {
        // 使用SHA-256算法增强密钥强度
        byte[] enhancedSecretKey = sha256(secretKey);

        String token = JWTUtil.createToken(claims, enhancedSecretKey);
        redisUtil.setWithDefaultExpire("token:user:" + claims.get("uid"), token);
        return token;
    }

    /**
     * 使用SHA-256算法生成更强的密钥
     *
     * @param originalSecret 原始密钥
     * @return 更强的密钥（字节数组形式）
     */
    private byte[] sha256(String originalSecret) {
        return new Digester(DigestAlgorithm.SHA256).digest(originalSecret);
    }

    /**
     * 示例方法：创建具有默认声明的JWT令牌
     *
     * @param customClaims 自定义声明，可为空
     * @return 生成的JWT令牌
     */
    public String createDefaultJwtToken(Map<String, Object> customClaims) {
        long expSeconds = DateUtil.currentSeconds() + JWT_EXPIRE_SECONDS;
        Map<String, Object> defaultClaims = new HashMap<>(Map.of(
                "jti", UUID.randomUUID().toString(),
                "role", "user",
                "exp", expSeconds
        ));
        if (Objects.nonNull(customClaims)) {
            defaultClaims.putAll(customClaims);
        }
        return createJwtToken(defaultClaims);
    }

    /**
     * 验证JWT令牌
     *
     * @param token JWT令牌
     * @return 验证结果
     */
    public boolean verifyJwtToken(String token) {
        byte[] enhancedSecretKey = sha256(secretKey);
        if (!JWTUtil.verify(token, enhancedSecretKey)) {
            return false;
        }
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查token是否过期
     *
     * @param token JWT令牌
     * @return 是否过期
     */
    public Boolean isTokenExpired(String token) {
        Object claim = JWTUtil.parseToken(token).getPayload().getClaim("exp");
        if (claim instanceof NumberWithFormat) {
            long expSeconds = ((NumberWithFormat) claim).longValue();
            return expSeconds < DateUtil.currentSeconds();
        } else {
            throw new IllegalArgumentException("解析token是否过期错误!");
        }
    }

    /**
     * 从token中获取指定claim
     *
     * @param token     JWT令牌
     * @param claimName claim名称
     * @return claim值
     */
    public String getClaimFromToken(String token, String claimName) {
        // 解析token，获取payload部分的所有claim
        return JWTUtil.parseToken(token).getPayload().getClaim(claimName).toString();
    }

}