package com.coreshift.model;

import java.util.ArrayList;
import java.util.List;

public class Job {
    private String id;
    private String name;
    private JobStatus status;
    private int priority;
    private List<String> dependencies;
    private ResourceRequirements resourceRequirements;
    private String assignedWorkerId;
    private int retryCount;
    private int maxRetries;
    private long submittedAt;
    private Long startedAt;
    private Long completedAt;
    private String errorMessage;

    public Job() {
        this.dependencies = new ArrayList<>();
        this.status = JobStatus.PENDING;
        this.priority = 5;
        this.maxRetries = 3;
        this.retryCount = 0;
        this.submittedAt = System.currentTimeMillis();
    }

    public Job(String id, String name, int priority, ResourceRequirements resourceRequirements, List<String> dependencies) {
        this();
        this.id = id;
        this.name = name;
        this.priority = priority;
        this.resourceRequirements = resourceRequirements;
        if (dependencies != null) {
            this.dependencies = new ArrayList<>(dependencies);
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public JobStatus getStatus() {
        return status;
    }

    public void setStatus(JobStatus status) {
        this.status = status;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies != null ? new ArrayList<>(dependencies) : new ArrayList<>();
    }

    public ResourceRequirements getResourceRequirements() {
        return resourceRequirements;
    }

    public void setResourceRequirements(ResourceRequirements resourceRequirements) {
        this.resourceRequirements = resourceRequirements;
    }

    public String getAssignedWorkerId() {
        return assignedWorkerId;
    }

    public void setAssignedWorkerId(String assignedWorkerId) {
        this.assignedWorkerId = assignedWorkerId;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(long submittedAt) {
        this.submittedAt = submittedAt;
    }

    public Long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Long startedAt) {
        this.startedAt = startedAt;
    }

    public Long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Long completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
