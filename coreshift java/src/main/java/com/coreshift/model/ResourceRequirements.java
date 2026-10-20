package com.coreshift.model;

public class ResourceRequirements {
    private int cpuCores;
    private int memoryMb;

    public ResourceRequirements() {
    }

    public ResourceRequirements(int cpuCores, int memoryMb) {
        this.cpuCores = cpuCores;
        this.memoryMb = memoryMb;
    }

    public int getCpuCores() {
        return cpuCores;
    }

    public void setCpuCores(int cpuCores) {
        this.cpuCores = cpuCores;
    }

    public int getMemoryMb() {
        return memoryMb;
    }

    public void setMemoryMb(int memoryMb) {
        this.memoryMb = memoryMb;
    }
}
