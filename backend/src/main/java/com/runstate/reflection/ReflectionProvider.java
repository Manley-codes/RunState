package com.runstate.reflection;

import com.runstate.reflection.ReflectionContract.CandidateReflections;
import com.runstate.reflection.ReflectionContract.CompletedRunEvidence;

/** Generates one four-branch reflection family from one completed-run evidence snapshot. */
@FunctionalInterface
public interface ReflectionProvider {

    CandidateReflections generate(CompletedRunEvidence evidence) throws ReflectionProviderException;

    /** Delivery metadata is an actual paired-generation result, never an echo of a client's request. */
    default GeneratedReflections generateForDelivery(CompletedRunEvidence evidence) throws ReflectionProviderException {
        return new GeneratedReflections(generate(evidence), null);
    }

    /** Optional opaque handle stays outside run facts and never reaches either model prompt. */
    default GeneratedReflections generateForDelivery(CompletedRunEvidence evidence, String songPreparationId)
            throws ReflectionProviderException {
        return generateForDelivery(evidence);
    }

    default boolean supportsSongPreparation() { return false; }

    default SongPreparationState prepareSong(ReflectionContract.SongPreparationRequest request)
            throws ReflectionProviderException {
        return SongPreparationState.UNAVAILABLE;
    }

    enum SongPreparationState { READY, UNAVAILABLE }

    /** One opaque preparation ID cannot be rebound to a different song. */
    final class SongPreparationConflictException extends IllegalArgumentException {
        public SongPreparationConflictException() { super("The song preparation identity does not match."); }
    }

    record GeneratedReflections(CandidateReflections candidates, String songCueId) {
        public GeneratedReflections {
            java.util.Objects.requireNonNull(candidates, "candidates");
            if (songCueId != null && !songCueId.matches("[a-z0-9][a-z0-9_-]{0,79}")) {
                throw new IllegalArgumentException("Invalid acknowledged song cue.");
            }
        }
        @Override public String toString() { return "GeneratedReflections[content=redacted, paired=" + (songCueId != null) + "]"; }
    }

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
