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

    default boolean supportsSongSelection() { return false; }

    default boolean supportsSongSnippets() { return false; }

    default boolean supportsSongSnippetFallbacks() { return false; }

    default GeneratedReflections generateForDelivery(CompletedRunEvidence evidence, String songPreparationId,
            java.util.List<ReflectionContract.SongCandidate> songCandidates) throws ReflectionProviderException {
        return generateForDelivery(evidence, songPreparationId);
    }

    default GeneratedReflections generateForDelivery(CompletedRunEvidence evidence, String songPreparationId,
            java.util.List<ReflectionContract.SongCandidate> songCandidates, boolean allowSongSnippetLookup)
            throws ReflectionProviderException {
        return generateForDelivery(evidence, songPreparationId, songCandidates);
    }

    default GeneratedReflections generateForDelivery(CompletedRunEvidence evidence, String songPreparationId,
            java.util.List<ReflectionContract.SongCandidate> songCandidates, boolean allowSongSnippetLookup,
            boolean allowSongSnippetFallbacks) throws ReflectionProviderException {
        return generateForDelivery(evidence, songPreparationId, songCandidates, allowSongSnippetLookup);
    }

    default SongPreparationState prepareSong(ReflectionContract.SongPreparationRequest request)
            throws ReflectionProviderException {
        return SongPreparationState.UNAVAILABLE;
    }

    enum SongPreparationState { READY, UNAVAILABLE }

    /** One opaque preparation ID cannot be rebound to a different song. */
    final class SongPreparationConflictException extends IllegalArgumentException {
        public SongPreparationConflictException() { super("The song preparation identity does not match."); }
    }

    enum SongSelectionMethod { CONTEXT_MATCH, AVAILABLE_FALLBACK, OBSERVED_FALLBACK }

    record GeneratedReflections(CandidateReflections candidates, String songCueId,
                                String selectedSongId, SongSelectionMethod songSelectionMethod,
                                ReflectionContract.SongSnippet songSnippet) {
        public GeneratedReflections(CandidateReflections candidates, String songCueId,
                                    String selectedSongId, SongSelectionMethod songSelectionMethod) {
            this(candidates, songCueId, selectedSongId, songSelectionMethod, null);
        }
        public GeneratedReflections(CandidateReflections candidates, String songCueId) {
            this(candidates, songCueId, null, null, null);
        }
        public GeneratedReflections {
            java.util.Objects.requireNonNull(candidates, "candidates");
            if (songCueId != null && !songCueId.matches("[a-z0-9][a-z0-9_-]{0,79}")) {
                throw new IllegalArgumentException("Invalid acknowledged song cue.");
            }
            if ((selectedSongId == null) != (songSelectionMethod == null)
                    || selectedSongId != null && !selectedSongId.matches("(?:0|[1-9][0-9]{0,9})")) {
                throw new IllegalArgumentException("Invalid song selection acknowledgement.");
            }
            if (songSnippet != null && (selectedSongId == null ||
                    songCueId != null && !songSnippet.cueId().equals(songCueId) ||
                    songSnippet.version() == 1 && songCueId == null)) {
                throw new IllegalArgumentException("A generated snippet requires its selected song and exact cue.");
            }
            if (songSelectionMethod == SongSelectionMethod.OBSERVED_FALLBACK && songCueId != null) {
                throw new IllegalArgumentException("Observed selection cannot claim analysis-derived pairing.");
            }
            if (songSnippet != null && songSnippet.selectionMethod()
                    == ReflectionContract.SongSnippetSelectionMethod.RECORDING_OPENING_FALLBACK && songCueId != null) {
                throw new IllegalArgumentException("An opening fallback is not an analysis-derived landing.");
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
