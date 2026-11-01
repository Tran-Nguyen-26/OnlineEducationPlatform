package com.oep.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
@EntityScan("com.oep.coremodel.entity")
@EnableJpaRepositories("com.oep.worker")
public class WorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(WorkerApplication.class, args);
    }
}
