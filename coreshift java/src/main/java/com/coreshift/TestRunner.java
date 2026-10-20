package com.coreshift;

import com.coreshift.model.*;
import com.coreshift.repository.SchedulingStateRepository;
import com.coreshift.service.*;

import java.util.*;

public class TestRunner {

    private record TestResult(String name, boolean passed, long ms, String error) {}

    private static final List<TestResult> results = new ArrayList<>();

    private static void test(String name, Runnable testBody) {
        long start = System.currentTimeMillis();
        try {
            testBody.run();
            long elapsed = Math.max(1, System.currentTimeMillis() - start);
            results.add(new TestResult(name, true, elapsed, null));
        } catch (Throwable t) {
            long elapsed = Math.max(1, System.currentTimeMillis() - start);
            String message = t.getMessage();
            if (message == null || message.trim().isEmpty()) {
                message = t.getClass().getSimpleName();
            }
            results.add(new TestResult(name, false, elapsed, message));
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }

    public static void main(String[] args) {
        // Dependency Graph Readiness Evaluation
        test("test_dependency_graph_readiness_with_prerequisites", () -> {
            SchedulingStateRepository repo = new SchedulingStateRepository();
            DependencySchedulerService depService = new DependencySchedulerService(repo);

            List<Job> jobs = repo.getAllJobs();
            Map<String, Boolean> readiness = depService.evaluateReadiness(jobs);

            assertFalse(readiness.getOrDefault("job-transform-shared-a", false),
                    "Job depending on a failed/retrying prerequisite must not be evaluated as ready");
        });

        // Retry Queue Priority Recalculation
        test("test_retry_queue_priority_recalculation", () -> {
            SchedulingStateRepository repo = new SchedulingStateRepository();
            DependencySchedulerService depService = new DependencySchedulerService(repo);
            RetryQueueService retryQueueService = new RetryQueueService(depService);

            Job failedJob = repo.getJob("job-extract-01").orElseThrow();
            int effectivePriority = retryQueueService.calculateEffectivePriority(failedJob);
            assertEquals(13, effectivePriority,
                    "Retrying job priority must be boosted on retry (base 8 + retry 1*5 = 13)");
        });

        // Readiness and Retry Dispatch Ordering
        test("test_wired_dependency_and_retry_dispatch_order", () -> {
            SchedulingStateRepository repo = new SchedulingStateRepository();
            DependencySchedulerService depService = new DependencySchedulerService(repo);
            RetryQueueService retryQueueService = new RetryQueueService(depService);

            List<Job> jobs = repo.getAllJobs();
            List<Job> queue = retryQueueService.buildDispatchQueue(jobs);

            assertFalse(queue.isEmpty(), "Dispatch queue should not be empty");
            Job first = queue.get(0);
            assertEquals("job-extract-01", first.getId(),
                    "The retrying prerequisite pipeline job must be at the head of the dispatch queue");

            boolean childInQueue = queue.stream().anyMatch(j -> j.getId().equals("job-transform-shared-a"));
            assertFalse(childInQueue,
                    "Dependent job 'job-transform-shared-a' must not be in dispatch queue before prerequisite completes");
        });

        // Resource Allocation Memory Capacity Accounting
        test("test_resource_allocation_memory_accounting", () -> {
            SchedulingStateRepository repo = new SchedulingStateRepository();
            ResourceAllocationService allocService = new ResourceAllocationService();

            Worker gamma = repo.getWorker("worker-gamma").orElseThrow();
            ResourceRequirements bigReq = new ResourceRequirements(2, 4096);

            boolean canAllocate = allocService.hasSufficientCapacity(gamma, bigReq);
            assertFalse(canAllocate,
                    "Worker with only 2048 MB available memory must reject an allocation requiring 4096 MB");
        });

        // Worker Load Balancer Selection
        test("test_worker_load_balancer_selection", () -> {
            SchedulingStateRepository repo = new SchedulingStateRepository();
            ResourceAllocationService allocService = new ResourceAllocationService();
            WorkerLoadBalancerService lbService = new WorkerLoadBalancerService(allocService);

            Job job = new Job("test-job", "Test Workload", 5, new ResourceRequirements(1, 2048), Collections.emptyList());
            List<Worker> workers = repo.getAllWorkers();

            Worker selected = lbService.selectBestWorker(job, workers);
            assertFalse(selected == null, "An eligible worker must be selected");
            assertEquals("worker-beta", selected.getId(),
                    "Load balancer must select least congested Worker Beta over heavily loaded Worker Alpha");
        });

        // Job Lifecycle Terminal State Validation
        test("test_job_lifecycle_terminal_state_protection", () -> {
            JobLifecycleService lifecycle = new JobLifecycleService();
            boolean valid = lifecycle.isValidTransition(JobStatus.COMPLETED, JobStatus.CANCELLED);
            assertFalse(valid,
                    "Terminal state COMPLETED must not permit transition to CANCELLED");
        });

        // Persisted Scheduling State Consistency
        test("test_persisted_scheduling_state_consistency", () -> {
            SchedulingStateRepository repo = new SchedulingStateRepository();
            DependencySchedulerService depService = new DependencySchedulerService(repo);
            RetryQueueService retryQueueService = new RetryQueueService(depService);
            ResourceAllocationService allocService = new ResourceAllocationService();
            WorkerLoadBalancerService lbService = new WorkerLoadBalancerService(allocService);
            JobLifecycleService lifecycle = new JobLifecycleService();

            SchedulingOrchestratorService orchestrator = new SchedulingOrchestratorService(
                    repo, depService, retryQueueService, allocService, lbService, lifecycle
            );

            // Reassign running job 'job-stream-bg' from worker-alpha to worker-beta
            // First adjust worker-beta memory in test to allow job-stream-bg (12 cpu, 24576mb requires larger worker, or create a smaller running job)
            Worker testWorker = new Worker("worker-target", "Target Test Worker", 16, 32768);
            repo.saveWorker(testWorker);

            Job runningJob = new Job("test-running-job", "Running Job", 5, new ResourceRequirements(2, 4096), Collections.emptyList());
            runningJob.setStatus(JobStatus.RUNNING);
            runningJob.setAssignedWorkerId("worker-alpha");
            repo.saveJob(runningJob);

            orchestrator.reassignJob("test-running-job", "worker-target");

            // Verify persisted state in repository
            Worker persistedTarget = repo.getWorker("worker-target").orElseThrow();
            assertEquals(2, persistedTarget.getUsedCpuCores(),
                    "Persisted target worker in repository must reflect assigned CPU cores");
            assertEquals(4096, persistedTarget.getUsedMemoryMb(),
                    "Persisted target worker in repository must reflect assigned Memory MB");
            assertTrue(persistedTarget.getActiveJobIds().contains("test-running-job"),
                    "Persisted target worker active jobs must contain reassigned job ID");
        });

        // Normal Workflow Baseline Test
        test("test_normal_cycle_and_dag_validation", () -> {
            SchedulingStateRepository repo = new SchedulingStateRepository();
            DependencySchedulerService depService = new DependencySchedulerService(repo);

            List<Job> validJobs = List.of(
                    new Job("j1", "Root 1", 5, new ResourceRequirements(1, 1024), Collections.emptyList()),
                    new Job("j2", "Child", 5, new ResourceRequirements(1, 1024), List.of("j1"))
            );
            assertFalse(depService.hasCycle(validJobs), "Valid linear DAG must not report a cycle");

            List<Job> cyclicJobs = List.of(
                    new Job("cA", "Cyclic A", 5, new ResourceRequirements(1, 1024), List.of("cB")),
                    new Job("cB", "Cyclic B", 5, new ResourceRequirements(1, 1024), List.of("cA"))
            );
            assertTrue(depService.hasCycle(cyclicJobs), "Cyclic dependency graph must be detected");
        });

        // Generate strict JSON report
        long totalExecutionTime = results.stream().mapToLong(r -> r.ms()).sum();
        int passed = (int) results.stream().filter(r -> r.passed()).count();
        int failed = (int) results.stream().filter(r -> !r.passed()).count();

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        for (TestResult r : results) {
            sb.append("  \"").append(r.name()).append("\": {\n");
            sb.append("    \"Status\": \"").append(r.passed() ? "passed" : "failed").append("\",\n");
            sb.append("    \"Execution time\": \"").append(r.ms()).append("ms\"");
            if (!r.passed() && r.error() != null) {
                sb.append(",\n    \"Error\": \"")
                  .append(r.error()
                          .replace("\\", "\\\\")
                          .replace("\"", "\\\"")
                          .replace("\n", " "))
                  .append("\"");
            }
            sb.append("\n  },\n");
        }
        sb.append("  \"Passed\": ").append(passed).append(",\n");
        sb.append("  \"Failed\": ").append(failed).append(",\n");
        sb.append("  \"Total bugs\": 6,\n");
        sb.append("  \"Total Execution time\": \"").append(totalExecutionTime).append("ms\"\n");
        sb.append("}");

        System.out.println(sb);
        System.exit(failed > 0 ? 1 : 0);
    }
}
