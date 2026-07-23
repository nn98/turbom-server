package com.nextstep.domain.businesstype;

public record ReliabilitySignal(Level level, String reason) {

    public enum Level {
        CONFIRMED,
        NEEDS_VERIFICATION
    }

    public static ReliabilitySignal confirmed() {
        return new ReliabilitySignal(Level.CONFIRMED, null);
    }

    public static ReliabilitySignal needsVerification(String reason) {
        return new ReliabilitySignal(Level.NEEDS_VERIFICATION, reason);
    }
}
