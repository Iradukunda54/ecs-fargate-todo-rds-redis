package com.todoapp;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({TaskController.class, TaskApiController.class})
@Import(RuntimeInfo.class)
class TaskControllerTest {

    private static final TaskView TASK = new TaskView(1L, "Buy milk", "2 litres", false,
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TaskService service;

    @Test
    void indexShowsTasksAndCacheSource() throws Exception {
        when(service.listTasks()).thenReturn(new ReadResult<>(List.of(TASK), ReadResult.Source.REDIS_CACHE, 1));
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Buy milk")))
                .andExpect(content().string(Matchers.containsString("Served from Redis cache")));
    }

    @Test
    void createRedirectsAfterWrite() throws Exception {
        when(service.create("Buy milk", "")).thenReturn(TASK);
        mvc.perform(post("/tasks").param("title", "Buy milk").param("description", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
        verify(service).create("Buy milk", "");
    }

    @Test
    void createRejectsBlankTitle() throws Exception {
        when(service.listTasks()).thenReturn(new ReadResult<>(List.of(), ReadResult.Source.DATABASE, 3));
        mvc.perform(post("/tasks").param("title", " "))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Title is required")));
        verify(service).listTasks();
        verifyNoMoreInteractions(service);
    }

    @Test
    void updateToggleDelete() throws Exception {
        when(service.update(1, "New", "", true)).thenReturn(Optional.of(TASK));
        mvc.perform(post("/tasks/1").param("title", "New").param("description", "").param("completed", "true"))
                .andExpect(redirectedUrl("/"));

        when(service.toggle(1)).thenReturn(Optional.of(TASK));
        mvc.perform(post("/tasks/1/toggle")).andExpect(redirectedUrl("/"));

        when(service.delete(1)).thenReturn(true);
        mvc.perform(post("/tasks/1/delete")).andExpect(redirectedUrl("/"));

        when(service.delete(2)).thenReturn(false);
        mvc.perform(post("/tasks/2/delete")).andExpect(status().isNotFound());
    }

    @Test
    void editMissingTaskIs404() throws Exception {
        when(service.getTask(anyLong())).thenReturn(Optional.empty());
        mvc.perform(get("/tasks/5/edit")).andExpect(status().isNotFound());
    }

    @Test
    void apiExposesDataSourceHeader() throws Exception {
        when(service.listTasks()).thenReturn(new ReadResult<>(List.of(TASK), ReadResult.Source.DATABASE, 4));
        mvc.perform(get("/api/tasks"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Data-Source", "DATABASE"))
                .andExpect(jsonPath("$[0].title").value("Buy milk"));
    }
}
