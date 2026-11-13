package com.example.Job.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JobPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.kafka.topics.requests}")
    private String requestsTopic;

    @Value("${app.kafka.topics.dlq}")
    private String dlqTopic;

    public void publishJob(Long jobId) {
        publish(requestsTopic, jobId, "job request");
    }

    public void publishDlq(Long jobId) {
        publish(dlqTopic, jobId, "dead-letter job");
    }

    private void publish(String topic, Long jobId, String description) {
        kafkaTemplate.send(topic, jobId.toString()).whenComplete((result, exception) -> {
            if (exception == null) {
                log.debug("Published {} {} to {}", description, jobId, topic);
            } else {
                log.error("Unable to publish {} {} to {}. Recovery will retry pending jobs.", description, jobId, topic,
                        exception);
            }
        });
    }
}
