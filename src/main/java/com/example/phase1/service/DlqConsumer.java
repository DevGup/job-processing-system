package com.example.phase1.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class DlqConsumer {

    private static final Logger log = LoggerFactory.getLogger(DlqConsumer.class);

    @KafkaListener(topics = "job-dlq", groupId = "job-dlq-group")
    public void consumeDlq(String jobId) {

        log.error("DLQ RECEIVED FAILED JOB {}",jobId);
    }
}