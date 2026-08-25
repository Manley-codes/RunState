package com.runstate;

/*
 * Thrown when RunState cannot durably read or write runs to the database.
 *
 * CHECKED exception: it extends Exception (not RuntimeException), so the compiler
 * forces every caller of saveRun(), loadRuns(), updateRunFeedback(), or
 * deleteRun() to consciously handle a storage failure. A run only counts once
 * it is durably saved, so this must never be silently swallowed.
 *
 * The original SQLException is passed in as the cause (chaining), preserving the
 * low-level database detail for the "Details:" line while keeping JDBC knowledge
 * inside RunStorage.
 */
public class RunStorageException extends Exception {
    public RunStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
