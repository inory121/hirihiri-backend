package com.hiiro.mq;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@SpringBootTest(classes = MqSmokeTest.TestApp.class)
@TestPropertySource(properties = {
        // RocketMQ 地址
        "spring.cloud.stream.rocketmq.binder.name-server=127.0.0.1:9876",
        // 生产者绑定 → 发送到 test.topic
        "spring.cloud.stream.bindings.testOut-out-0.destination=test-topic",
        "spring.cloud.stream.bindings.testOut-out-0.content-type=application/json",
        // 消费者绑定 → 从 test.topic 消费
        "spring.cloud.stream.bindings.testConsumer-in-0.destination=test-topic",
        "spring.cloud.stream.bindings.testConsumer-in-0.group=test-group",
        "spring.cloud.stream.bindings.testConsumer-in-0.content-type=application/json",
        // 函数名绑定
        "spring.cloud.stream.function.definition=testConsumer"
})
public class MqSmokeTest {

    @SpringBootApplication
    static class TestApp {
        @Bean
        public CountDownLatch latch() { return new CountDownLatch(1); }
        @Bean
        public AtomicReference<Map<String, Object>> received() { return new AtomicReference<>(); }

        @Bean
        public Consumer<Map<String, Object>> testConsumer(CountDownLatch latch,
                                                          AtomicReference<Map<String, Object>> received) {
            return payload -> {
                received.set(payload);
                latch.countDown();
            };
        }
    }

    @Autowired
    private StreamBridge streamBridge;
    @Autowired
    private CountDownLatch latch;
    @Autowired
    private AtomicReference<Map<String, Object>> received;

    @Test
    @DisplayName("RocketMQ Stream 简单收发冒烟测试")
    void mqSendAndReceive() throws Exception {
        Map<String, Object> msg = Map.of(
                "msg", "hello-rocketmq",
                "ts", System.currentTimeMillis()
        );
        // 等待消费者绑定完成（避免 RocketMQ 默认从最新偏移开始导致漏消费）
        Thread.sleep(1500);
        boolean sent = streamBridge.send("testOut-out-0", msg);
        Assertions.assertTrue(sent, "消息发送失败");

        boolean ok = latch.await(20, TimeUnit.SECONDS);
        Assertions.assertTrue(ok, "在超时时间内未收到消息");

        Map<String, Object> got = received.get();
        Assertions.assertNotNull(got, "收到消息为空");
        Assertions.assertEquals(msg.get("msg"), got.get("msg"));
    }
} 