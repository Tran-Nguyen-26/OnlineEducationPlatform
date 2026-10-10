package com.oep.corelms;

import com.oep.coremodel.datasource.RoutingDataSourceConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EntityScan("com.oep.coremodel.entity")
@EnableJpaRepositories("com.oep.corelms")
@Import(RoutingDataSourceConfig.class)
public class CoreLmsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoreLmsApplication.class, args);
    }
}