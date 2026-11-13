package com.example.Job.exception;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(Long id) {
        super("Job %d was not found".formatted(id));
    }
}
