package com.todoapp;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Server-rendered UI: list, create, edit, complete and delete tasks. */
@Controller
public class TaskController {

    private final TaskService service;
    private final RuntimeInfo runtime;

    public TaskController(TaskService service, RuntimeInfo runtime) {
        this.service = service;
        this.runtime = runtime;
    }

    @ModelAttribute("runtime")
    RuntimeInfo runtime() {
        return runtime;
    }

    @GetMapping("/")
    public String index(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new TaskForm());
        }
        ReadResult<List<TaskView>> result = service.listTasks();
        model.addAttribute("tasks", result.value());
        model.addAttribute("read", result);
        return "index";
    }

    @PostMapping("/tasks")
    public String create(@Valid @ModelAttribute("form") TaskForm form, BindingResult errors,
                         Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            ReadResult<List<TaskView>> result = service.listTasks();
            model.addAttribute("tasks", result.value());
            model.addAttribute("read", result);
            return "index";
        }
        TaskView task = service.create(form.getTitle(), form.getDescription());
        redirect.addFlashAttribute("message", "Task \"" + task.title() + "\" created");
        return "redirect:/";
    }

    @GetMapping("/tasks/{id}/edit")
    public String edit(@PathVariable long id, Model model) {
        ReadResult<TaskView> result = service.getTask(id).orElseThrow(TaskController::notFound);
        model.addAttribute("task", result.value());
        model.addAttribute("read", result);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", TaskForm.from(result.value()));
        }
        return "edit";
    }

    @PostMapping("/tasks/{id}")
    public String update(@PathVariable long id, @Valid @ModelAttribute("form") TaskForm form,
                         BindingResult errors, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            ReadResult<TaskView> result = service.getTask(id).orElseThrow(TaskController::notFound);
            model.addAttribute("task", result.value());
            model.addAttribute("read", result);
            return "edit";
        }
        service.update(id, form.getTitle(), form.getDescription(), form.isCompleted())
                .orElseThrow(TaskController::notFound);
        redirect.addFlashAttribute("message", "Task updated");
        return "redirect:/";
    }

    @PostMapping("/tasks/{id}/toggle")
    public String toggle(@PathVariable long id) {
        service.toggle(id).orElseThrow(TaskController::notFound);
        return "redirect:/";
    }

    @PostMapping("/tasks/{id}/delete")
    public String delete(@PathVariable long id, RedirectAttributes redirect) {
        if (!service.delete(id)) {
            throw notFound();
        }
        redirect.addFlashAttribute("message", "Task deleted");
        return "redirect:/";
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found");
    }
}
