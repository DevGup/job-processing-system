package com.example.Job.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfiguration {

    @Bean
    NewTopic jobRequestsTopic(
            @Value("${app.kafka.topics.requests}") String topic,
            @Value("${app.kafka.topics.requests-partitions:3}") int partitions) {
        return TopicBuilder.name(topic)
                .partitions(partitions)
                .replicas(1)
                .build();
    }

    @Bean
    NewTopic jobDlqTopic(
            @Value("${app.kafka.topics.dlq}") String topic) {
        return TopicBuilder.name(topic)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
