package com.hiiro.mq;

import com.hiiro.VideoServiceApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;

import java.util.HashMap;
import java.util.Map;

@SpringBootTest(classes = VideoServiceApplication.class)
public class MqSmokeTest {

    @Autowired
    private StreamBridge streamBridge;

    @Test
    @DisplayName("RocketMQ 简单测试")
    void mqSendAndReceive() throws InterruptedException {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "created");
        event.put("vid", 0);
        event.put("uid", 0);
        event.put("timestamp", System.currentTimeMillis());
        streamBridge.send("videoEvent-out-0", event);
    }
} 