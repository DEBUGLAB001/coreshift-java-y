package com.coreshift.model;

import java.util.ArrayList;
import java.util.List;

public class Worker {
    private String id;
    private String name;
    private int totalCpuCores;
    private int totalMemoryMb;
    private int usedCpuCores;
    private int usedMemoryMb;
    private List<String> activeJobIds;
    private String status;

    public Worker() {
        this.activeJobIds = new ArrayList<>();
        this.status = "ONLINE";
    }

    public Worker(String id, String name, int totalCpuCores, int totalMemoryMb) {
        this();
        this.id = id;
        this.name = name;
        this.totalCpuCores = totalCpuCores;
        this.totalMemoryMb = totalMemoryMb;
        this.usedCpuCores = 0;
        this.usedMemoryMb = 0;
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

    public int getTotalCpuCores() {
        return totalCpuCores;
    }

    public void setTotalCpuCores(int totalCpuCores) {
        this.totalCpuCores = totalCpuCores;
    }

    public int getTotalMemoryMb() {
        return totalMemoryMb;
    }

    public void setTotalMemoryMb(int totalMemoryMb) {
        this.totalMemoryMb = totalMemoryMb;
    }

    public int getUsedCpuCores() {
        return usedCpuCores;
    }

    public void setUsedCpuCores(int usedCpuCores) {
        this.usedCpuCores = usedCpuCores;
    }

    public int getUsedMemoryMb() {
        return usedMemoryMb;
    }

    public void setUsedMemoryMb(int usedMemoryMb) {
        this.usedMemoryMb = usedMemoryMb;
    }

    public List<String> getActiveJobIds() {
        return activeJobIds;
    }

    public void setActiveJobIds(List<String> activeJobIds) {
        this.activeJobIds = activeJobIds != null ? new ArrayList<>(activeJobIds) : new ArrayList<>();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getAvailableCpuCores() {
        return Math.max(0, totalCpuCores - usedCpuCores);
    }

    public int getAvailableMemoryMb() {
        return Math.max(0, totalMemoryMb - usedMemoryMb);
    }

    public double getCpuUtilizationPercentage() {
        if (totalCpuCores <= 0) return 0.0;
        return Math.round(((double) usedCpuCores / totalCpuCores) * 1000.0) / 10.0;
    }

    public double getMemoryUtilizationPercentage() {
        if (totalMemoryMb <= 0) return 0.0;
        return Math.round(((double) usedMemoryMb / totalMemoryMb) * 1000.0) / 10.0;
    }

    public void allocateResources(ResourceRequirements req) {
        if (req != null) {
            this.usedCpuCores += req.getCpuCores();
            this.usedMemoryMb += req.getMemoryMb();
        }
    }

    public void releaseResources(ResourceRequirements req) {
        if (req != null) {
            this.usedCpuCores = Math.max(0, this.usedCpuCores - req.getCpuCores());
            this.usedMemoryMb = Math.max(0, this.usedMemoryMb - req.getMemoryMb());
        }
    }
}
