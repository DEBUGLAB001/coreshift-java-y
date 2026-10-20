package com.coreshift.service;

import com.coreshift.model.Job;
import com.coreshift.model.JobStatus;
import com.coreshift.model.SchedulingEvent;
import com.coreshift.model.Worker;
import com.coreshift.repository.SchedulingStateRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SchedulingOrchestratorService {

    private final SchedulingStateRepository repository;
    private final DependencySchedulerService dependencySchedulerService;
    private final RetryQueueService retryQueueService;
    private final ResourceAllocationService resourceAllocationService;
    private final WorkerLoadBalancerService workerLoadBalancerService;
    private final JobLifecycleService jobLifecycleService;

    public SchedulingOrchestratorService(
            SchedulingStateRepository repository,
            DependencySchedulerService dependencySchedulerService,
            RetryQueueService retryQueueService,
            ResourceAllocationService resourceAllocationService,
            WorkerLoadBalancerService workerLoadBalancerService,
            JobLifecycleService jobLifecycleService) {
        this.repository = repository;
        this.dependencySchedulerService = dependencySchedulerService;
        this.retryQueueService = retryQueueService;
        this.resourceAllocationService = resourceAllocationService;
        this.workerLoadBalancerService = workerLoadBalancerService;
        this.jobLifecycleService = jobLifecycleService;
    }

    public synchronized Map<String, Object> runSchedulingCycle() {
        List<Job> allJobs = repository.getAllJobs();
        List<Worker> allWorkers = repository.getAllWorkers();

        List<Job> dispatchQueue = retryQueueService.buildDispatchQueue(allJobs);
        List<String> dispatchedJobIds = new ArrayList<>();
        List<String> skippedJobIds = new ArrayList<>();

        for (Job job : dispatchQueue) {
            Worker bestWorker = workerLoadBalancerService.selectBestWorker(job, allWorkers);
            if (bestWorker != null) {
                boolean allocated = resourceAllocationService.allocate(bestWorker, job);
                if (allocated) {
                    jobLifecycleService.markStarted(job, bestWorker);
                    repository.saveJob(job);
                    repository.saveWorker(bestWorker);
                    dispatchedJobIds.add(job.getId());
                    repository.recordEvent(new SchedulingEvent(
                            "JOB_DISPATCHED",
                            job.getId(),
                            bestWorker.getId(),
                            "Dispatched job '" + job.getName() + "' to " + bestWorker.getName()
                    ));
                } else {
                    skippedJobIds.add(job.getId());
                }
            } else {
                skippedJobIds.add(job.getId());
            }
        }

        repository.recordEvent(new SchedulingEvent(
                "CYCLE_EXECUTED",
                null,
                null,
                "Scheduling cycle executed. Dispatched: " + dispatchedJobIds.size() + ", Deferred: " + skippedJobIds.size()
        ));

        Map<String, Object> result = new HashMap<>();
        result.put("queueSize", dispatchQueue.size());
        result.put("dispatchedCount", dispatchedJobIds.size());
        result.put("dispatchedJobIds", dispatchedJobIds);
        result.put("deferredJobIds", skippedJobIds);
        return result;
    }

    public synchronized Job reassignJob(String jobId, String targetWorkerId) {
        Job job = repository.getJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        Worker targetWorker = repository.getWorker(targetWorkerId)
                .orElseThrow(() -> new IllegalArgumentException("Target worker not found: " + targetWorkerId));

        if (job.getStatus() != JobStatus.RUNNING) {
            throw new IllegalStateException("Only running jobs can be reassigned");
        }

        String sourceWorkerId = job.getAssignedWorkerId();
        Worker sourceWorker = sourceWorkerId != null ? repository.getWorker(sourceWorkerId).orElse(null) : null;

        if (!resourceAllocationService.hasSufficientCapacity(targetWorker, job.getResourceRequirements())) {
            throw new IllegalStateException("Target worker " + targetWorker.getName() + " does not have sufficient capacity");
        }

        if (sourceWorker != null) {
            resourceAllocationService.release(sourceWorker, job);
            repository.saveWorker(sourceWorker);
        }

        resourceAllocationService.allocate(targetWorker, job);
        job.setAssignedWorkerId(targetWorker.getId());
        repository.saveJob(job);

        // State synchronization for the target worker resource assignment
        // (Bug 6: targetWorker is updated in memory but not persisted to repository)

        repository.recordEvent(new SchedulingEvent(
                "JOB_REASSIGNED",
                job.getId(),
                targetWorker.getId(),
                "Reassigned job '" + job.getName() + "' from " + (sourceWorker != null ? sourceWorker.getName() : "None") + " to " + targetWorker.getName()
        ));

        return job;
    }

    public synchronized Job simulateJobSuccess(String jobId) {
        Job job = repository.getJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        if (job.getStatus() != JobStatus.RUNNING) {
            throw new IllegalStateException("Only running jobs can be completed");
        }

        String workerId = job.getAssignedWorkerId();
        Worker worker = workerId != null ? repository.getWorker(workerId).orElse(null) : null;
        if (worker != null) {
            resourceAllocationService.release(worker, job);
            repository.saveWorker(worker);
        }

        jobLifecycleService.markCompleted(job);
        repository.saveJob(job);

        repository.recordEvent(new SchedulingEvent(
                "JOB_COMPLETED",
                job.getId(),
                workerId,
                "Job '" + job.getName() + "' completed successfully."
        ));

        return job;
    }

    public synchronized Job simulateJobFailure(String jobId, String errorMessage) {
        Job job = repository.getJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        if (job.getStatus() != JobStatus.RUNNING) {
            throw new IllegalStateException("Only running jobs can fail");
        }

        String workerId = job.getAssignedWorkerId();
        Worker worker = workerId != null ? repository.getWorker(workerId).orElse(null) : null;
        if (worker != null) {
            resourceAllocationService.release(worker, job);
            repository.saveWorker(worker);
        }

        String err = errorMessage != null ? errorMessage : "Simulated execution error";
        jobLifecycleService.markFailed(job, err);
        repository.saveJob(job);

        repository.recordEvent(new SchedulingEvent(
                "JOB_FAILED",
                job.getId(),
                workerId,
                "Job '" + job.getName() + "' failed: " + err
        ));

        return job;
    }

    public synchronized Job cancelJob(String jobId) {
        Job job = repository.getJob(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        if (job.getStatus() == JobStatus.RUNNING) {
            String workerId = job.getAssignedWorkerId();
            Worker worker = workerId != null ? repository.getWorker(workerId).orElse(null) : null;
            if (worker != null) {
                resourceAllocationService.release(worker, job);
                repository.saveWorker(worker);
            }
        }

        jobLifecycleService.cancel(job);
        repository.saveJob(job);

        repository.recordEvent(new SchedulingEvent(
                "JOB_CANCELLED",
                job.getId(),
                job.getAssignedWorkerId(),
                "Job '" + job.getName() + "' was cancelled."
        ));

        return job;
    }

    public synchronized Job submitJob(Job job) {
        if (job.getId() == null || job.getId().trim().isEmpty()) {
            job.setId("job-" + UUID.randomUUID().toString().substring(0, 8));
        }

        List<Job> allJobs = repository.getAllJobs();
        allJobs.add(job);
        if (dependencySchedulerService.hasCycle(allJobs)) {
            throw new IllegalArgumentException("Submitting job would introduce a cyclic dependency in the workflow graph");
        }

        repository.saveJob(job);
        repository.recordEvent(new SchedulingEvent(
                "JOB_SUBMITTED",
                job.getId(),
                null,
                "Submitted new job '" + job.getName() + "' with priority " + job.getPriority()
        ));
        return job;
    }
}
