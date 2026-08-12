package com.audioagent.analysis.processing;

public enum ProcessingPriority {
    HIGH,
    MEDIUM,
    LOW;

    public int rank() {
        return ordinal();
    }

    public static ProcessingPriority fromSeverity(String severity) {
        if (severity == null) {
            return LOW;
        }
        try {
            return valueOf(severity.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return LOW;
        }
    }
}
