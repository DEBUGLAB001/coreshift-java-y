package com.coreshift.service;

import com.coreshift.model.Job;
import com.coreshift.model.Worker;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class WorkerLoadBalancerService {

    private final ResourceAllocationService resourceAllocationService;

    public WorkerLoadBalancerService(ResourceAllocationService resourceAllocationService) {
        this.resourceAllocationService = resourceAllocationService;
    }

    public double calculateLoadScore(Worker worker) {
        if (worker.getTotalCpuCores() <= 0 || worker.getTotalMemoryMb() <= 0) {
            return 1.0;
        }
        double cpuRatio = (double) worker.getUsedCpuCores() / worker.getTotalCpuCores();
        double memRatio = (double) worker.getUsedMemoryMb() / worker.getTotalMemoryMb();
        return (cpuRatio + memRatio) / 2.0;
    }

    public Worker selectBestWorker(Job job, List<Worker> workers) {
        List<Worker> eligibleWorkers = workers.stream()
                .filter(w -> "ONLINE".equalsIgnoreCase(w.getStatus()))
                .filter(w -> resourceAllocationService.hasSufficientCapacity(w, job.getResourceRequirements()))
                .collect(Collectors.toList());

        if (eligibleWorkers.isEmpty()) {
            return null;
        }

        return eligibleWorkers.stream()
                .max(Comparator.comparingDouble(this::calculateLoadScore))
                .orElse(null);
    }
}
