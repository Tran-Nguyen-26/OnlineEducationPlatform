package com.oep.assessment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EntityScan("com.oep.coremodel.entity")
@org.springframework.context.annotation.Import(com.oep.coremodel.datasource.RoutingDataSourceConfig.class)
@EnableJpaRepositories("com.oep.assessment")
@EnableScheduling
public class AssessmentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssessmentApplication.class, args);
    }
}
