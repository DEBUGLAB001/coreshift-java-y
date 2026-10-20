package com.coreshift.service;

import com.coreshift.model.Job;
import com.coreshift.model.JobStatus;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class RetryQueueService {

    private final DependencySchedulerService dependencySchedulerService;

    public RetryQueueService(DependencySchedulerService dependencySchedulerService) {
        this.dependencySchedulerService = dependencySchedulerService;
    }

    public boolean isEligibleForRetry(Job job) {
        if (job.getStatus() != JobStatus.FAILED) {
            return false;
        }
        return job.getRetryCount() < job.getMaxRetries();
    }

    public int calculateEffectivePriority(Job job) {
        int basePriority = job.getPriority();
        if (job.getStatus() == JobStatus.FAILED) {
            int retryAdjustment = job.getRetryCount() * 5;
            return basePriority - retryAdjustment;
        }
        return basePriority;
    }

    public List<Job> buildDispatchQueue(List<Job> allJobs) {
        Map<String, Boolean> readinessMap = dependencySchedulerService.evaluateReadiness(allJobs);
        List<Job> candidates = new ArrayList<>();

        for (Job job : allJobs) {
            boolean isReady = readinessMap.getOrDefault(job.getId(), false);
            if (!isReady) {
                continue;
            }

            if (job.getStatus() == JobStatus.PENDING) {
                candidates.add(job);
            } else if (job.getStatus() == JobStatus.FAILED && isEligibleForRetry(job)) {
                candidates.add(job);
            }
        }

        candidates.sort((j1, j2) -> {
            int p1 = calculateEffectivePriority(j1);
            int p2 = calculateEffectivePriority(j2);
            if (p1 != p2) {
                return Integer.compare(p2, p1);
            }
            return Long.compare(j1.getSubmittedAt(), j2.getSubmittedAt());
        });

        return candidates;
    }
}
