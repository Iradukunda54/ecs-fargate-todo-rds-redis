package com.todoapp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cache-aside access to tasks.
 *
 * <p>Reads check Redis first and fall back to PostgreSQL (through RDS Proxy), then populate
 * the cache. Writes always go to PostgreSQL and evict the affected cache keys. If Redis is
 * unavailable the app keeps working directly against the database.
 */
@Service
public class TaskService {

    static final String ALL_TASKS_KEY = "todo:tasks:all";
    private static final Logger log = LoggerFactory.getLogger(TaskService.class);
    private static final TypeReference<List<TaskView>> TASK_LIST = new TypeReference<>() { };

    private final TaskRepository repository;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public TaskService(TaskRepository repository, StringRedisTemplate redis, ObjectMapper mapper,
                       @Value("${todo.cache.ttl:PT5M}") Duration ttl) {
        this.repository = repository;
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
    }

    static String taskKey(long id) {
        return "todo:task:" + id;
    }

    @Transactional(readOnly = true)
    public ReadResult<List<TaskView>> listTasks() {
        long start = System.nanoTime();
        Optional<List<TaskView>> cached = readCache(ALL_TASKS_KEY, TASK_LIST);
        if (cached.isPresent()) {
            return new ReadResult<>(cached.get(), ReadResult.Source.REDIS_CACHE, elapsed(start));
        }
        List<TaskView> tasks = repository.findAllByOrderByCompletedAscCreatedAtDesc().stream()
                .map(TaskView::of).toList();
        writeCache(ALL_TASKS_KEY, tasks);
        return new ReadResult<>(tasks, ReadResult.Source.DATABASE, elapsed(start));
    }

    @Transactional(readOnly = true)
    public Optional<ReadResult<TaskView>> getTask(long id) {
        long start = System.nanoTime();
        Optional<TaskView> cached = readCache(taskKey(id), new TypeReference<TaskView>() { });
        if (cached.isPresent()) {
            return Optional.of(new ReadResult<>(cached.get(), ReadResult.Source.REDIS_CACHE, elapsed(start)));
        }
        return repository.findById(id).map(TaskView::of).map(view -> {
            writeCache(taskKey(id), view);
            return new ReadResult<>(view, ReadResult.Source.DATABASE, elapsed(start));
        });
    }

    @Transactional
    public TaskView create(String title, String description) {
        TaskView saved = TaskView.of(repository.save(new Task(title.strip(), blankToNull(description))));
        evict(ALL_TASKS_KEY);
        log.info("Task created id={}", saved.id());
        return saved;
    }

    @Transactional
    public Optional<TaskView> update(long id, String title, String description, boolean completed) {
        return repository.findById(id).map(task -> {
            task.setTitle(title.strip());
            task.setDescription(blankToNull(description));
            task.setCompleted(completed);
            TaskView saved = TaskView.of(repository.saveAndFlush(task));
            evict(ALL_TASKS_KEY, taskKey(id));
            log.info("Task updated id={}", id);
            return saved;
        });
    }

    @Transactional
    public Optional<TaskView> toggle(long id) {
        return repository.findById(id).map(task -> {
            task.setCompleted(!task.isCompleted());
            TaskView saved = TaskView.of(repository.saveAndFlush(task));
            evict(ALL_TASKS_KEY, taskKey(id));
            return saved;
        });
    }

    @Transactional
    public boolean delete(long id) {
        if (!repository.existsById(id)) {
            return false;
        }
        repository.deleteById(id);
        evict(ALL_TASKS_KEY, taskKey(id));
        log.info("Task deleted id={}", id);
        return true;
    }

    // ------------------------------------------------------------------ cache helpers

    private <T> Optional<T> readCache(String key, TypeReference<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            return json == null ? Optional.empty() : Optional.of(mapper.readValue(json, type));
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("Redis read failed for {}, falling back to database: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private void writeCache(String key, Object value) {
        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(value), ttl);
        } catch (DataAccessException | JsonProcessingException e) {
            log.warn("Redis write failed for {}: {}", key, e.getMessage());
        }
    }

    private void evict(String... keys) {
        try {
            redis.delete(List.of(keys));
        } catch (DataAccessException e) {
            log.warn("Redis evict failed for {}: {}", List.of(keys), e.getMessage());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static long elapsed(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }
}
