package com.runstate.reflection;

import com.runstate.reflection.ReflectionContract.CandidateReflections;
import com.runstate.reflection.ReflectionContract.CompletedRunEvidence;

/** Generates one four-branch reflection family from one completed-run evidence snapshot. */
@FunctionalInterface
public interface ReflectionProvider {

    CandidateReflections generate(CompletedRunEvidence evidence) throws ReflectionProviderException;

    /** Small failure categories that callers can report without guessing from message text. */
    enum FailureType {
        CONFIGURATION,
        TIMEOUT,
        TRANSPORT,
        PROVIDER_ERROR,
        REFUSAL,
        TRUNCATED,
        INVALID_RESPONSE
    }

    /** A checked failure because provider problems are expected runtime outcomes. */
    final class ReflectionProviderException extends Exception {

        private final FailureType failureType;

        public ReflectionProviderException(FailureType failureType, String message) {
            super(message);
            this.failureType = failureType;
        }

        public ReflectionProviderException(FailureType failureType, String message, Throwable cause) {
            super(message, cause);
            this.failureType = failureType;
        }

        public FailureType failureType() {
            return failureType;
        }
    }
}
