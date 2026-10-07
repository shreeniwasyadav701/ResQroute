package com.resqroute;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ResQRouteApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResQRouteApplication.class, args);
    }
}
