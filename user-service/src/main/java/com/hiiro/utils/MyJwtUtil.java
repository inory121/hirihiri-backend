package com.hiiro.utils;

import cn.hutool.core.convert.NumberWithFormat;
import cn.hutool.core.date.DateUtil;
import cn.hutool.crypto.digest.DigestAlgorithm;
import cn.hutool.crypto.digest.Digester;
import cn.hutool.jwt.JWTUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class MyJwtUtil {

    @Value("${jwt.secret}")
    private String secretKey;

    /**
     * 创建JWT令牌
     *
     * @param claims JWT声明，包含如用户ID、角色等信息
     * @return 生成的JWT令牌
     */
    public String createJwtToken(Map<String, Object> claims) {
        // 使用SHA-256算法增强密钥强度
        byte[] enhancedSecretKey = sha256(secretKey);

        return JWTUtil.createToken(claims, enhancedSecretKey);
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
        Map<String, Object> defaultClaims = new HashMap<>(Map.of(
                "uid", "1",
                "role", "normal",
                "exp", DateUtil.current() + 1000 * 60 * 60 * 24 * 5 // 当前时间加上5天
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
        // 使用SHA-256算法增强密钥强度
        byte[] enhancedSecretKey = sha256(secretKey);

        return JWTUtil.verify(token, enhancedSecretKey);
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
            // 将 claim 转换为 long 类型
            long expTime = ((NumberWithFormat) claim).longValue();
            // 检查当前时间是否在过期时间之后
            return DateUtil.date(expTime).before(DateUtil.date(DateUtil.current()));
        } else {
            throw new IllegalArgumentException("解析token是否过期错误!");
        }
    }

    /**
     * 获取指定的声明
     *
     * @param token     JWT令牌
     * @param claimName 声明名称
     * @return 指定声明的值
     */
    public Object getClaimFromToken(String token, String claimName) {
        // 解析token，获取payload部分的所有claim
        return JWTUtil.parseToken(token).getPayload().getClaim(claimName);
    }

}
