package com.hiiro.test;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTHeader;
import cn.hutool.jwt.JWTUtil;
import cn.hutool.jwt.JWTValidator;
import com.hiiro.UserServiceApplication;
import com.hiiro.entity.ResultCodeEnum;
import com.hiiro.entity.User;
import com.hiiro.exp.UserException;
import com.hiiro.utils.MyJwtUtil;
import com.hiiro.utils.RedisUtil;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@SpringBootTest(classes = UserServiceApplication.class)
public class UserServiceApplicationTests {

    @Resource
    RedisUtil redisUtil;

    @Resource
    MyJwtUtil jwtUtil;

    @Test
    public void contextLoad() {
        System.out.println(redisUtil.getObject("user:2", User.class));
        String token="eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1aWQiOiIyIiwicm9sZSI6Im5vcm1hbCIsImV4cCI6MTczOTE5OTgxNTUxOX0.TGNmIGnemb781RXpr5AaWVU2afekGVQIl7FkqTORVow";
        if (jwtUtil.verifyJwtToken(token)) {
            Object uid = jwtUtil.getClaimFromToken(token, "uid");
            System.out.println(redisUtil.get("user:" + uid));
        } else {
            throw new UserException(ResultCodeEnum.TOKEN_INVALID);
        }
    }
    @Test
    public void contextLoads() {
        Map<String, Object> claims = new HashMap<>(Map.of(
                "uid", "1",
                "role", "admin",
                "exp", System.currentTimeMillis()
        ));
        String token = JWTUtil.createToken(claims, "114514".getBytes());
        System.out.println(token);
        JWT jwt = JWTUtil.parseToken(token);
        System.out.println(jwt.getHeader(JWTHeader.TYPE));
//        ThreadUtil.sleep(2000);
        System.out.println(System.currentTimeMillis());
        System.out.println(jwt.getPayload("exp"));
        System.out.println(Long.parseLong(String.valueOf(jwt.getPayload("exp")))<=(System.currentTimeMillis()));
        System.out.println(jwt.getPayload("uid"));
        System.out.println(jwt.getPayload("role"));
        System.out.println(JWTUtil.verify(token, "114514".getBytes()));


        System.out.println(JWTValidator.of(token).validateDate(DateUtil.date()));
    }

    @Test
    public void test() {
        String token = jwtUtil.createDefaultJwtToken(null);
        System.out.println(token);
        System.out.println(jwtUtil.verifyJwtToken("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1aWQiOiIxIiwicm9sZSI6Im5vcm1hbCIsImV4cCI6MTczOTE2OTk4ODQxNH0.VH3b-2BPiTIVxnCE9R-CzuEpBZc92PfmAPYJjyLB-3k"));
    }

    @Test
    public void test2() {
        Date date = new Date();
        DateTime time = DateUtil.date(System.currentTimeMillis() + 3000);
//        System.out.println(date);
//        System.out.println(time);
//        System.out.println(date.before(time));
        Boolean expired = jwtUtil.isTokenExpired("eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1aWQiOiIxIiwicm9sZSI6Im5vcm1hbCIsImV4cCI6MjAwMH0.lz0wOCpr-tHkvCHp2XexlnszNUj4RKczzAfhqfj9xFA");
        System.out.println(expired);
    }

}
