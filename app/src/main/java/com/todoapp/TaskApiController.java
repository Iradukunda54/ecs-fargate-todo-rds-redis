package com.todoapp;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only JSON endpoints for verification. The X-Data-Source header says whether a
 * response came from the Redis cache or from PostgreSQL.
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskApiController {

    private final TaskService service;

    public TaskApiController(TaskService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<TaskView>> list() {
        return withSource(service.listTasks());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskView> get(@PathVariable long id) {
        return service.getTask(id).map(TaskApiController::withSource)
                .orElse(ResponseEntity.notFound().build());
    }

    private static <T> ResponseEntity<T> withSource(ReadResult<T> result) {
        return ResponseEntity.ok()
                .header("X-Data-Source", result.source().name())
                .header("X-Read-Millis", Long.toString(result.elapsedMillis()))
                .body(result.value());
    }
}
