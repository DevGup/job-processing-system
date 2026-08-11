package com.example.Job.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter

public class Job {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String type;

    @Enumerated(EnumType.STRING)
     private JobStatus status;

    private LocalDateTime createdAt;

    private Integer retryCount = 0;
    
    private String failureReason;

    private LocalDateTime failedAt;
   
}
