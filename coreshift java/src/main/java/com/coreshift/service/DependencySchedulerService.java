package com.coreshift.service;

import com.coreshift.model.Job;
import com.coreshift.model.JobStatus;
import com.coreshift.repository.SchedulingStateRepository;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DependencySchedulerService {

    private final SchedulingStateRepository repository;

    public DependencySchedulerService(SchedulingStateRepository repository) {
        this.repository = repository;
    }

    public boolean hasCycle(List<Job> jobs) {
        Map<String, List<String>> adj = new HashMap<>();
        for (Job job : jobs) {
            adj.putIfAbsent(job.getId(), new ArrayList<>());
            for (String depId : job.getDependencies()) {
                adj.computeIfAbsent(depId, k -> new ArrayList<>()).add(job.getId());
            }
        }

        Map<String, Integer> visited = new HashMap<>(); // 0: unvisited, 1: visiting, 2: visited
        for (Job job : jobs) {
            if (visited.getOrDefault(job.getId(), 0) == 0) {
                if (detectCycleDfs(job.getId(), adj, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean detectCycleDfs(String u, Map<String, List<String>> adj, Map<String, Integer> visited) {
        visited.put(u, 1);
        for (String v : adj.getOrDefault(u, Collections.emptyList())) {
            int state = visited.getOrDefault(v, 0);
            if (state == 1) {
                return true;
            }
            if (state == 0 && detectCycleDfs(v, adj, visited)) {
                return true;
            }
        }
        visited.put(u, 2);
        return false;
    }

    public List<Job> getTopologicalOrder(List<Job> jobs) {
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> adj = new HashMap<>();
        Map<String, Job> jobMap = new HashMap<>();

        for (Job job : jobs) {
            jobMap.put(job.getId(), job);
            inDegree.put(job.getId(), 0);
            adj.put(job.getId(), new ArrayList<>());
        }

        for (Job job : jobs) {
            for (String depId : job.getDependencies()) {
                if (adj.containsKey(depId)) {
                    adj.get(depId).add(job.getId());
                    inDegree.put(job.getId(), inDegree.get(job.getId()) + 1);
                }
            }
        }

        PriorityQueue<Job> pq = new PriorityQueue<>(Comparator.comparingInt(Job::getPriority).reversed());
        for (Job job : jobs) {
            if (inDegree.get(job.getId()) == 0) {
                pq.add(job);
            }
        }

        List<Job> ordered = new ArrayList<>();
        while (!pq.isEmpty()) {
            Job curr = pq.poll();
            ordered.add(curr);

            for (String nextId : adj.getOrDefault(curr.getId(), Collections.emptyList())) {
                inDegree.put(nextId, inDegree.get(nextId) - 1);
                if (inDegree.get(nextId) == 0) {
                    Job nextJob = jobMap.get(nextId);
                    if (nextJob != null) {
                        pq.add(nextJob);
                    }
                }
            }
        }
        return ordered;
    }

    public Map<String, Boolean> evaluateReadiness(List<Job> jobs) {
        Map<String, Boolean> readiness = new HashMap<>();

        for (Job job : jobs) {
            if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.RUNNING || job.getStatus() == JobStatus.CANCELLED) {
                readiness.put(job.getId(), false);
                continue;
            }

            boolean ready = true;
            for (String depId : job.getDependencies()) {
                Job dep = repository.getJob(depId).orElse(null);
                if (dep == null || dep.getStatus() == JobStatus.PENDING) {
                    ready = false;
                    break;
                }
            }
            readiness.put(job.getId(), ready);
        }
        return readiness;
    }
}
