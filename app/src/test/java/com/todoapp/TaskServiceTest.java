package com.todoapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

class TaskServiceTest {

    private TaskRepository repository;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private ObjectMapper mapper;
    private TaskService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        repository = mock(TaskRepository.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new TaskService(repository, redis, mapper, Duration.ofMinutes(5));
    }

    private static Task task(long id, String title) {
        Task task = new Task(title, null);
        ReflectionTestUtils.setField(task, "id", id);
        ReflectionTestUtils.setField(task, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(task, "updatedAt", Instant.parse("2026-01-01T00:00:00Z"));
        return task;
    }

    @Test
    void listCacheMissReadsDatabaseAndPopulatesCache() throws Exception {
        when(values.get(TaskService.ALL_TASKS_KEY)).thenReturn(null);
        when(repository.findAllByOrderByCompletedAscCreatedAtDesc()).thenReturn(List.of(task(1, "a")));

        ReadResult<List<TaskView>> result = service.listTasks();

        assertThat(result.source()).isEqualTo(ReadResult.Source.DATABASE);
        assertThat(result.value()).extracting(TaskView::title).containsExactly("a");
        verify(values).set(eq(TaskService.ALL_TASKS_KEY), anyString(), eq(Duration.ofMinutes(5)));
    }

    @Test
    void listCacheHitSkipsDatabase() throws Exception {
        String json = mapper.writeValueAsString(List.of(TaskView.of(task(1, "cached"))));
        when(values.get(TaskService.ALL_TASKS_KEY)).thenReturn(json);

        ReadResult<List<TaskView>> result = service.listTasks();

        assertThat(result.fromCache()).isTrue();
        assertThat(result.value()).extracting(TaskView::title).containsExactly("cached");
        verify(repository, never()).findAllByOrderByCompletedAscCreatedAtDesc();
    }

    @Test
    void getTaskUsesPerTaskCacheKey() throws Exception {
        when(values.get(TaskService.taskKey(7))).thenReturn(null);
        when(repository.findById(7L)).thenReturn(Optional.of(task(7, "seven")));

        assertThat(service.getTask(7)).get().extracting(ReadResult::source).isEqualTo(ReadResult.Source.DATABASE);
        verify(values).set(eq(TaskService.taskKey(7)), anyString(), any(Duration.class));

        when(values.get(TaskService.taskKey(7))).thenReturn(mapper.writeValueAsString(TaskView.of(task(7, "seven"))));
        assertThat(service.getTask(7)).get().extracting(ReadResult::source).isEqualTo(ReadResult.Source.REDIS_CACHE);
    }

    @Test
    void writesGoToDatabaseAndEvictCache() {
        when(repository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));
        service.create("  New task ", "  ");
        verify(repository).save(any(Task.class));
        verify(redis).delete(List.of(TaskService.ALL_TASKS_KEY));

        Task existing = task(3, "old");
        when(repository.findById(3L)).thenReturn(Optional.of(existing));
        when(repository.saveAndFlush(existing)).thenReturn(existing);
        assertThat(service.update(3, "renamed", "desc", true)).get()
                .satisfies(view -> {
                    assertThat(view.title()).isEqualTo("renamed");
                    assertThat(view.completed()).isTrue();
                });
        verify(redis).delete(List.of(TaskService.ALL_TASKS_KEY, TaskService.taskKey(3)));
    }

    @Test
    void deleteMissingTaskReturnsFalse() {
        when(repository.existsById(9L)).thenReturn(false);
        assertThat(service.delete(9)).isFalse();
        verify(repository, never()).deleteById(any());
    }

    @Test
    void redisOutageFallsBackToDatabase() {
        when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
        when(repository.findAllByOrderByCompletedAscCreatedAtDesc()).thenReturn(List.of(task(1, "a")));
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
                .when(values).set(anyString(), anyString(), any(Duration.class));

        ReadResult<List<TaskView>> result = service.listTasks();

        assertThat(result.source()).isEqualTo(ReadResult.Source.DATABASE);
        assertThat(result.value()).hasSize(1);
    }
}
