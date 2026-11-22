package com.example.Job.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(indexes = @Index(name = "idx_job_status_next_retry", columnList = "status, next_retry_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @Column(nullable = false)
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;
    
    @Column(length = 1000)
    private String failureReason;

   private String result;

    private LocalDateTime failedAt;

    private LocalDateTime processingStartedAt;

    @JsonIgnore
    private UUID attemptId;

    @Version
    private Long version;
}
