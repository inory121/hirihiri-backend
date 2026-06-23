package com.hiiro.mq;

import com.hiiro.VideoServiceApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@SpringBootTest(classes = VideoServiceApplication.class)
public class MqTest {

    @Autowired
    private StreamBridge streamBridge;

    // ───────────────── 测试一：仅验证发送成功 ─────────────────
    @Test
    @DisplayName("RocketMQ - 发送消息（简单发送）")
    void testSendSimple() {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "created");
        event.put("vid", 1001L);
        event.put("uid", 1L);
        event.put("timestamp", System.currentTimeMillis());

        boolean sent = streamBridge.send("videoEvent-out-0", event);
        assertTrue(sent, "❌ 消息发送到 RocketMQ 失败，请检查 NameServer(127.0.0.1:9876) 是否启动");
        log.info("✅ 消息已发送至 video-events: {}", event);
    }

    // ────────── 测试二：发送 Message（带 headers）并等待消费 ──────────
    @Test
    @DisplayName("RocketMQ - 发送并等待消费者处理（观察日志）")
    void testSendAndReceive() throws InterruptedException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "created");
        payload.put("vid", 1001L);
        payload.put("uid", 1L);
        payload.put("timestamp", System.currentTimeMillis());

        Message<Map<String, Object>> message = MessageBuilder
                .withPayload(payload)
                .setHeader("test-source", "junit")
                .build();

        boolean sent = streamBridge.send("videoEvent-out-0", message);
        assertTrue(sent, "❌ 消息发送失败");
        log.info("✅ 消息已发送，等待消费者异步处理...");

        // 等待消费者异步消费（根据环境调整等待时间）
        // 消费成功后控制台应出现: [video.events] 已同步ES, type=created, vid=1001
        // 如果 vid=1001 在数据库中不存在，会看到: [video.events] 未找到视频, vid=1001
        TimeUnit.SECONDS.sleep(5);

        log.info("⏱ 5秒等待结束，请查看上方日志确认消费结果。");
    }

    // ───────────── 测试三：发送多种事件类型 ─────────────
    @Test
    @DisplayName("RocketMQ - 发送 deleted 事件")
    void testSendDeletedEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "deleted");
        event.put("vid", 99999L);
        event.put("uid", 1L);
        event.put("timestamp", System.currentTimeMillis());

        boolean sent = streamBridge.send("videoEvent-out-0", event);
        assertTrue(sent, "❌ deleted 事件发送失败");
        log.info("✅ deleted 事件已发送: {}", event);
    }

    @Test
    @DisplayName("RocketMQ - 发送未知类型事件（应收到 warn 日志）")
    void testSendUnknownEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("type", "UNKNOWN");
        event.put("vid", 1001L);
        event.put("uid", 1L);
        event.put("timestamp", System.currentTimeMillis());

        boolean sent = streamBridge.send("videoEvent-out-0", event);
        assertTrue(sent, "❌ 未知类型事件发送失败");
        log.info("✅ 未知类型事件已发送，消费者应输出 warn 日志: {}", event);
    }

    // ───── 测试四：发往独立测试 Topic（需先在 RocketMQ 创建 video-events-test）─────
    @Test
    @DisplayName("RocketMQ - 发送到测试 Topic（避免污染业务数据）")
    void testSendToTestTopic() {
        // 直接指定 destination = topic 名称（绕开 binding 配置）
        // 需要先在 broker 创建 topic: video-events-test
        Map<String, Object> event = new HashMap<>();
        event.put("type", "test");
        event.put("vid", 99999L);
        event.put("uid", 0L);
        event.put("timestamp", System.currentTimeMillis());

        boolean sent = streamBridge.send("video-events-test", event);
        assertTrue(sent, "❌ 发送到测试 topic 失败，请先创建 video-events-test");
        log.info("✅ 已发送到测试 Topic [video-events-test]: {}", event);
    }
}