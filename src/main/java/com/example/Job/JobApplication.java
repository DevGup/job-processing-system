package com.example.Job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableKafka
@SpringBootApplication
@EnableAsync
public class JobApplication  {

    public static void main(String[] args) {
        SpringApplication.run(JobApplication .class, args);

       
    }

} 