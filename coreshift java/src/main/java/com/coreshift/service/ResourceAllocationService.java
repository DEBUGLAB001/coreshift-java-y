package com.coreshift.service;

import com.coreshift.model.Job;
import com.coreshift.model.ResourceRequirements;
import com.coreshift.model.Worker;
import org.springframework.stereotype.Service;

@Service
public class ResourceAllocationService {

    public boolean hasSufficientCapacity(Worker worker, ResourceRequirements req) {
        if (worker == null || req == null) {
            return false;
        }

        int availableCpu = worker.getTotalCpuCores() - worker.getUsedCpuCores();
        if (availableCpu < req.getCpuCores()) {
            return false;
        }

        if (worker.getTotalMemoryMb() - req.getMemoryMb() < 0) {
            return false;
        }

        return true;
    }

    public boolean allocate(Worker worker, Job job) {
        if (!hasSufficientCapacity(worker, job.getResourceRequirements())) {
            return false;
        }
        worker.allocateResources(job.getResourceRequirements());
        if (!worker.getActiveJobIds().contains(job.getId())) {
            worker.getActiveJobIds().add(job.getId());
        }
        return true;
    }

    public void release(Worker worker, Job job) {
        if (worker != null && job != null) {
            worker.releaseResources(job.getResourceRequirements());
            worker.getActiveJobIds().remove(job.getId());
        }
    }
}
