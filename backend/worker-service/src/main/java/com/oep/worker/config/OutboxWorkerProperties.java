package com.oep.worker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "oep.outbox")
public class OutboxWorkerProperties {

    private Worker worker = new Worker();
    private Consumer consumer = new Consumer();

    @Getter
    @Setter
    public static class Worker {
        private boolean enabled = true;
        private int batchSize = 100;
        private long basePollDelayMs = 200L;
        private long maxPollDelayMs = 30000L;
        private int maxRetries = 5;
    }

    @Getter
    @Setter
    public static class Consumer {
        private boolean enabled = true;
        private int pollWaitSeconds = 2;
        private int maxMessages = 10;
        private int maxRetries = 5;
    }
}
