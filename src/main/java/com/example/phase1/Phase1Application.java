package com.example.phase1;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableKafka
@SpringBootApplication
@EnableAsync
public class Phase1Application {

    public static void main(String[] args) {
        SpringApplication.run(Phase1Application.class, args);

       
    }

}