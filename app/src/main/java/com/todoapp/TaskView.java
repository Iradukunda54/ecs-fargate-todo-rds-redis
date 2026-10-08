package com.todoapp;

import java.time.Instant;

/** Serializable snapshot of a task, stored in Redis and rendered by the UI. */
public record TaskView(Long id, String title, String description, boolean completed,
                       Instant createdAt, Instant updatedAt) {

    static TaskView of(Task task) {
        return new TaskView(task.getId(), task.getTitle(), task.getDescription(), task.isCompleted(),
                task.getCreatedAt(), task.getUpdatedAt());
    }
}
