package com.example.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final List<Task> tasks = new ArrayList<>();
    private final AtomicLong counter = new AtomicLong();

    public TaskController() {
        tasks.add(new Task(counter.incrementAndGet(), "Setup CI/CD Pipeline", "COMPLETED"));
        tasks.add(new Task(counter.incrementAndGet(), "Deploy to Cloud Kubernetes", "IN_PROGRESS"));
    }

    @GetMapping
    public List<Task> getTasks() {
        return tasks;
    }

    @PostMapping
    public Task addTask(@RequestBody Task task) {
        task.setId(counter.incrementAndGet());
        if (task.getStatus() == null) task.setStatus("PENDING");
        tasks.add(task);
        return task;
    }

    @GetMapping("/version")
    public Map<String, String> getVersion() {
        // Change this string when demonstrating automated pipeline trigger and redeploy
        return Map.of("version", "v1.0.0", "environment", "Production");
    }
}