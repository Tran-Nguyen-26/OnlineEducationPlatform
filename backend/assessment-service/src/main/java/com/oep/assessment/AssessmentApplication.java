package com.oep.assessment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.oep.coremodel.entity")
@EnableJpaRepositories("com.oep.assessment")
public class AssessmentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssessmentApplication.class, args);
    }
}
