package com.hiiro.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@ConfigurationProperties(prefix = "aliyun.oss")
@Data
public class OSSConfig {
    private String endpoint;
    private String bucketUrl;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucketName;
    private int threadPoolSize;
    private int partSizeMB;

    @Bean
    public ExecutorService uploadThreadPool() {
        return Executors.newFixedThreadPool(threadPoolSize);
    }

    @Bean
    public OSS ossClient() {
        return new OSSClientBuilder().build(
                this.endpoint,
                this.accessKeyId,
                this.accessKeySecret
        );
    }
}

