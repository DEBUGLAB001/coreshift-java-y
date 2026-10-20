package com.coreshift.controller;

import com.coreshift.model.Job;
import com.coreshift.model.ResourceRequirements;
import com.coreshift.model.SchedulingEvent;
import com.coreshift.model.Worker;
import com.coreshift.repository.SchedulingStateRepository;
import com.coreshift.service.DependencySchedulerService;
import com.coreshift.service.RetryQueueService;
import com.coreshift.service.SchedulingOrchestratorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class SchedulingApiController {

    private final SchedulingStateRepository repository;
    private final SchedulingOrchestratorService orchestratorService;
    private final DependencySchedulerService dependencySchedulerService;
    private final RetryQueueService retryQueueService;

    public SchedulingApiController(
            SchedulingStateRepository repository,
            SchedulingOrchestratorService orchestratorService,
            DependencySchedulerService dependencySchedulerService,
            RetryQueueService retryQueueService) {
        this.repository = repository;
        this.orchestratorService = orchestratorService;
        this.dependencySchedulerService = dependencySchedulerService;
        this.retryQueueService = retryQueueService;
    }

    @GetMapping("/jobs")
    public ResponseEntity<List<Job>> getJobs() {
        return ResponseEntity.ok(repository.getAllJobs());
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<?> getJob(@PathVariable String id) {
        return repository.getJob(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Job not found")));
    }

    @PostMapping("/jobs")
    public ResponseEntity<?> createJob(@RequestBody Map<String, Object> payload) {
        try {
            String name = (String) payload.get("name");
            if (name == null || name.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Job name is required"));
            }

            int priority = payload.containsKey("priority") ? ((Number) payload.get("priority")).intValue() : 5;
            int cpu = payload.containsKey("cpuCores") ? ((Number) payload.get("cpuCores")).intValue() : 2;
            int mem = payload.containsKey("memoryMb") ? ((Number) payload.get("memoryMb")).intValue() : 4096;

            @SuppressWarnings("unchecked")
            List<String> deps = payload.containsKey("dependencies") ? (List<String>) payload.get("dependencies") : Collections.emptyList();

            Job job = new Job();
            if (payload.containsKey("id") && payload.get("id") != null) {
                job.setId((String) payload.get("id"));
            }
            job.setName(name);
            job.setPriority(priority);
            job.setResourceRequirements(new ResourceRequirements(cpu, mem));
            job.setDependencies(deps);

            Job created = orchestratorService.submitJob(job);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/workers")
    public ResponseEntity<List<Worker>> getWorkers() {
        return ResponseEntity.ok(repository.getAllWorkers());
    }

    @GetMapping("/workers/{id}")
    public ResponseEntity<?> getWorker(@PathVariable String id) {
        return repository.getWorker(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Worker not found")));
    }

    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getQueue() {
        List<Job> allJobs = repository.getAllJobs();
        Map<String, Boolean> readinessMap = dependencySchedulerService.evaluateReadiness(allJobs);
        List<Job> dispatchQueue = retryQueueService.buildDispatchQueue(allJobs);

        List<Map<String, Object>> queueItems = new ArrayList<>();
        for (Job j : dispatchQueue) {
            Map<String, Object> item = new HashMap<>();
            item.put("job", j);
            item.put("effectivePriority", retryQueueService.calculateEffectivePriority(j));
            item.put("ready", readinessMap.getOrDefault(j.getId(), false));
            queueItems.add(item);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("queue", queueItems);
        response.put("readinessMap", readinessMap);
        response.put("totalReady", queueItems.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/graph")
    public ResponseEntity<Map<String, Object>> getGraph() {
        List<Job> allJobs = repository.getAllJobs();
        Map<String, Boolean> readinessMap = dependencySchedulerService.evaluateReadiness(allJobs);

        List<Map<String, Object>> nodes = new ArrayList<>();
        List<Map<String, Object>> edges = new ArrayList<>();

        for (Job job : allJobs) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", job.getId());
            node.put("name", job.getName());
            node.put("status", job.getStatus());
            node.put("priority", job.getPriority());
            node.put("ready", readinessMap.getOrDefault(job.getId(), false));
            nodes.add(node);

            for (String depId : job.getDependencies()) {
                Map<String, Object> edge = new HashMap<>();
                edge.put("from", depId);
                edge.put("to", job.getId());
                edges.add(edge);
            }
        }

        Map<String, Object> graph = new HashMap<>();
        graph.put("nodes", nodes);
        graph.put("edges", edges);
        graph.put("hasCycle", dependencySchedulerService.hasCycle(allJobs));
        return ResponseEntity.ok(graph);
    }

    @PostMapping("/schedule/cycle")
    public ResponseEntity<?> runCycle() {
        try {
            Map<String, Object> result = orchestratorService.runSchedulingCycle();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/jobs/{id}/reassign")
    public ResponseEntity<?> reassignJob(@PathVariable String id, @RequestBody Map<String, String> payload) {
        String targetWorkerId = payload.get("workerId");
        if (targetWorkerId == null || targetWorkerId.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "workerId is required"));
        }
        try {
            Job reassigned = orchestratorService.reassignJob(id, targetWorkerId);
            return ResponseEntity.ok(reassigned);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/jobs/{id}/complete")
    public ResponseEntity<?> completeJob(@PathVariable String id) {
        try {
            Job job = orchestratorService.simulateJobSuccess(id);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/jobs/{id}/fail")
    public ResponseEntity<?> failJob(@PathVariable String id, @RequestBody(required = false) Map<String, String> payload) {
        try {
            String error = (payload != null && payload.containsKey("error")) ? payload.get("error") : "Simulated failure";
            Job job = orchestratorService.simulateJobFailure(id, error);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/jobs/{id}/cancel")
    public ResponseEntity<?> cancelJob(@PathVariable String id) {
        try {
            Job job = orchestratorService.cancelJob(id);
            return ResponseEntity.ok(job);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/events")
    public ResponseEntity<List<SchedulingEvent>> getEvents() {
        return ResponseEntity.ok(repository.getAllEvents());
    }

    @PostMapping("/scenario/reset")
    public ResponseEntity<Map<String, String>> resetScenario() {
        repository.resetToDeterministicScenario();
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Scenario reset to initial state"));
    }
}
