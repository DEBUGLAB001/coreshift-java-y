package com.coreshift.repository;

import com.coreshift.model.Job;
import com.coreshift.model.JobPriority;
import com.coreshift.model.JobStatus;
import com.coreshift.model.ResourceRequirements;
import com.coreshift.model.SchedulingEvent;
import com.coreshift.model.Worker;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Repository
public class SchedulingStateRepository {

    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    private final Map<String, Worker> workers = new ConcurrentHashMap<>();
    private final List<SchedulingEvent> events = new CopyOnWriteArrayList<>();

    public SchedulingStateRepository() {
        resetToDeterministicScenario();
    }

    public synchronized void resetToDeterministicScenario() {
        jobs.clear();
        workers.clear();
        events.clear();

        // 1. Initialize Workers
        Worker alpha = new Worker("worker-alpha", "Worker Alpha (High-Density Compute)", 16, 32768);
        alpha.setUsedCpuCores(12);
        alpha.setUsedMemoryMb(24576);
        alpha.setActiveJobIds(new ArrayList<>(List.of("job-stream-bg")));

        Worker beta = new Worker("worker-beta", "Worker Beta (General Purpose Balanced)", 8, 16384);
        beta.setUsedCpuCores(1);
        beta.setUsedMemoryMb(2048);
        beta.setActiveJobIds(new ArrayList<>());

        Worker gamma = new Worker("worker-gamma", "Worker Gamma (Memory Constrained)", 8, 8192);
        gamma.setUsedCpuCores(2);
        gamma.setUsedMemoryMb(6144);
        gamma.setActiveJobIds(new ArrayList<>());

        workers.put(alpha.getId(), alpha);
        workers.put(beta.getId(), beta);
        workers.put(gamma.getId(), gamma);

        // 2. Initialize Deterministic Workload Jobs
        // Job 0: Preparation (Completed)
        Job jobPrep = new Job("job-prep-00", "Data Cluster Preparation", 7, new ResourceRequirements(2, 2048), Collections.emptyList());
        jobPrep.setStatus(JobStatus.COMPLETED);
        jobPrep.setStartedAt(System.currentTimeMillis() - 120000);
        jobPrep.setCompletedAt(System.currentTimeMillis() - 60000);

        // Job 1: Ingestion Pipeline (Failed, eligible for retry)
        Job jobExtract = new Job("job-extract-01", "Database Ingestion Pipeline", 8, new ResourceRequirements(2, 4096), List.of("job-prep-00"));
        jobExtract.setStatus(JobStatus.FAILED);
        jobExtract.setRetryCount(1);
        jobExtract.setMaxRetries(3);
        jobExtract.setErrorMessage("Shard network connection timeout on replica-04");

        // Job 2: Transform Alpha (Pending, child of failed job 1 and completed job 0)
        Job jobTransformA = new Job("job-transform-shared-a", "Feature Transform Alpha", 4, new ResourceRequirements(2, 4096), List.of("job-extract-01", "job-prep-00"));
        jobTransformA.setStatus(JobStatus.PENDING);

        // Job 3: Vector Embeddings (Pending, child of failed job 1)
        Job jobTransformB = new Job("job-transform-shared-b", "Vector Embeddings Beta", 3, new ResourceRequirements(2, 4096), List.of("job-extract-01"));
        jobTransformB.setStatus(JobStatus.PENDING);

        // Job 4: Aggregator (Blocked by multi-level dependency chain)
        Job jobAggregate = new Job("job-aggregate-03", "Model Weight Aggregator", 9, new ResourceRequirements(4, 8192), List.of("job-transform-shared-a", "job-transform-shared-b"));
        jobAggregate.setStatus(JobStatus.PENDING);

        // Job 5: Background Stream (Running on worker-alpha)
        Job jobStream = new Job("job-stream-bg", "Telemetry Realtime Stream", 6, new ResourceRequirements(12, 24576), Collections.emptyList());
        jobStream.setStatus(JobStatus.RUNNING);
        jobStream.setAssignedWorkerId("worker-alpha");
        jobStream.setStartedAt(System.currentTimeMillis() - 180000);

        // Job 6: Independent Cache Warmup (Pending, immediately eligible)
        Job jobIsolated = new Job("job-isolated-04", "Cache Warmup Service", 5, new ResourceRequirements(2, 4096), Collections.emptyList());
        jobIsolated.setStatus(JobStatus.PENDING);

        jobs.put(jobPrep.getId(), jobPrep);
        jobs.put(jobExtract.getId(), jobExtract);
        jobs.put(jobTransformA.getId(), jobTransformA);
        jobs.put(jobTransformB.getId(), jobTransformB);
        jobs.put(jobAggregate.getId(), jobAggregate);
        jobs.put(jobStream.getId(), jobStream);
        jobs.put(jobIsolated.getId(), jobIsolated);

        // Record initial scenario reset event
        events.add(new SchedulingEvent("SCENARIO_RESET", null, null, "Deterministic demonstration scenario initialized with 7 jobs and 3 workers."));
    }

    private Worker cloneWorker(Worker w) {
        if (w == null) return null;
        Worker copy = new Worker(w.getId(), w.getName(), w.getTotalCpuCores(), w.getTotalMemoryMb());
        copy.setUsedCpuCores(w.getUsedCpuCores());
        copy.setUsedMemoryMb(w.getUsedMemoryMb());
        copy.setActiveJobIds(new ArrayList<>(w.getActiveJobIds()));
        copy.setStatus(w.getStatus());
        return copy;
    }

    private Job cloneJob(Job j) {
        if (j == null) return null;
        Job copy = new Job(j.getId(), j.getName(), j.getPriority(),
                j.getResourceRequirements() != null ? new ResourceRequirements(j.getResourceRequirements().getCpuCores(), j.getResourceRequirements().getMemoryMb()) : null,
                j.getDependencies());
        copy.setStatus(j.getStatus());
        copy.setAssignedWorkerId(j.getAssignedWorkerId());
        copy.setRetryCount(j.getRetryCount());
        copy.setMaxRetries(j.getMaxRetries());
        copy.setSubmittedAt(j.getSubmittedAt());
        copy.setStartedAt(j.getStartedAt());
        copy.setCompletedAt(j.getCompletedAt());
        copy.setErrorMessage(j.getErrorMessage());
        return copy;
    }

    public List<Job> getAllJobs() {
        List<Job> list = new ArrayList<>();
        for (Job j : jobs.values()) {
            list.add(cloneJob(j));
        }
        return list;
    }

    public Optional<Job> getJob(String id) {
        return Optional.ofNullable(cloneJob(jobs.get(id)));
    }

    public void saveJob(Job job) {
        jobs.put(job.getId(), cloneJob(job));
    }

    public List<Worker> getAllWorkers() {
        List<Worker> list = new ArrayList<>();
        for (Worker w : workers.values()) {
            list.add(cloneWorker(w));
        }
        return list;
    }

    public Optional<Worker> getWorker(String id) {
        return Optional.ofNullable(cloneWorker(workers.get(id)));
    }

    public void saveWorker(Worker worker) {
        workers.put(worker.getId(), cloneWorker(worker));
    }

    public List<SchedulingEvent> getAllEvents() {
        return new ArrayList<>(events);
    }

    public void recordEvent(SchedulingEvent event) {
        events.add(0, event);
    }
}
