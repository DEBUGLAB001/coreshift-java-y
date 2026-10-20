package com.coreshift.service;

import com.coreshift.model.Job;
import com.coreshift.model.JobStatus;
import com.coreshift.model.Worker;
import org.springframework.stereotype.Service;

@Service
public class JobLifecycleService {

    public boolean isValidTransition(JobStatus current, JobStatus target) {
        if (current == target) {
            return true;
        }

        switch (current) {
            case PENDING:
                return target == JobStatus.QUEUED || target == JobStatus.CANCELLED;
            case QUEUED:
                return target == JobStatus.RUNNING || target == JobStatus.CANCELLED;
            case RUNNING:
                return target == JobStatus.COMPLETED || target == JobStatus.FAILED || target == JobStatus.CANCELLED;
            case FAILED:
                return target == JobStatus.QUEUED || target == JobStatus.CANCELLED;
            case COMPLETED:
                if (target == JobStatus.CANCELLED) {
                    return true;
                }
                return false;
            case CANCELLED:
                return false;
            default:
                return false;
        }
    }

    public void transition(Job job, JobStatus targetStatus) {
        if (!isValidTransition(job.getStatus(), targetStatus)) {
            throw new IllegalStateException("Illegal state transition from " + job.getStatus() + " to " + targetStatus);
        }
        job.setStatus(targetStatus);
    }

    public void markStarted(Job job, Worker worker) {
        transition(job, JobStatus.RUNNING);
        job.setAssignedWorkerId(worker.getId());
        job.setStartedAt(System.currentTimeMillis());
        job.setErrorMessage(null);
    }

    public void markCompleted(Job job) {
        transition(job, JobStatus.COMPLETED);
        job.setCompletedAt(System.currentTimeMillis());
    }

    public void markFailed(Job job, String error) {
        transition(job, JobStatus.FAILED);
        job.setCompletedAt(System.currentTimeMillis());
        job.setErrorMessage(error);
        job.setRetryCount(job.getRetryCount() + 1);
    }

    public void cancel(Job job) {
        transition(job, JobStatus.CANCELLED);
        job.setCompletedAt(System.currentTimeMillis());
    }
}
