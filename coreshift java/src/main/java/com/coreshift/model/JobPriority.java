package com.coreshift.model;

public enum JobPriority {
    LOW(2),
    NORMAL(5),
    HIGH(8),
    CRITICAL(10);

    private final int weight;

    JobPriority(int weight) {
        this.weight = weight;
    }

    public int getWeight() {
        return weight;
    }
}
