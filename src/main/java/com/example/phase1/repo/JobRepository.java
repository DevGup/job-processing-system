package com.example.phase1.repo;


import org.springframework.data.jpa.repository.JpaRepository;
import com.example.phase1.entity.Job;

public interface JobRepository extends JpaRepository<Job, Long> {
}