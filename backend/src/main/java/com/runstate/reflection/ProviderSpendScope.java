package com.runstate.reflection;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.Callable;

/** One authenticated HTTP request's budget, shared explicitly with its song lookup workers. */
final class ProviderSpendScope implements AutoCloseable {
    private static final ThreadLocal<ProviderSpendScope> CURRENT = new ThreadLocal<>();

    private final ProviderSpendScope previous;
    private final ProviderSpendBudget budget;
    private final String principal;
    private final LocalProviderUsage usage;
    private boolean attempted;
    private boolean closed;
    private Denied denial;

    private ProviderSpendScope(ProviderSpendBudget budget, String principal, LocalProviderUsage usage) {
        this.budget = budget;
        this.principal = Objects.requireNonNull(principal);
        this.usage = usage;
        this.previous = CURRENT.get();
    }

    static ProviderSpendScope open(ProviderSpendBudget budget, String principal) {
        return open(budget, principal, null);
    }

    static ProviderSpendScope open(ProviderSpendBudget budget, String principal, LocalProviderUsage usage) {
        ProviderSpendScope scope = new ProviderSpendScope(budget, principal, usage);
        CURRENT.set(scope);
        return scope;
    }

    static LocalProviderUsage beforeSend(ProviderSpendBudget.Provider provider) {
        ProviderSpendScope scope = CURRENT.get();
        if (scope == null) return null;
        scope.reserve(provider);
        return scope.usage;
    }

    // A worker may already be in flight when another is denied. Serialize the decision and
    // attempted flag so HTTP can safely tell whether *any* outbound work was admitted.
    private synchronized void reserve(ProviderSpendBudget.Provider provider) {
        if (denial != null) throw denial;
        if (budget == null || closed) throw deny(true);
        try {
            switch (budget.reserve(principal, provider)) {
                case ALLOWED -> attempted = true;
                case GLOBAL_LIMIT, RUNNER_LIMIT -> throw deny(false);
                case UNCONFIGURED -> throw deny(true);
            }
        } catch (IOException failure) {
            throw deny(true);
        }
    }

    private synchronized Denied deny(boolean unavailable) {
        if (denial == null) denial = new Denied(unavailable, attempted);
        return denial;
    }

    // A joined speculative study may have been made by a different HTTP request. Roll its
    // reason into this request, keeping this request's own dispatch history.
    static void propagateIfDenied(Throwable cause) {
        if (cause instanceof Denied denied) {
            ProviderSpendScope scope = CURRENT.get();
            throw scope == null ? denied : scope.deny(denied.unavailable());
        }
    }

    static <T> Callable<T> withCurrent(Callable<T> task) {
        ProviderSpendScope captured = CURRENT.get();
        if (captured == null) return task;
        return () -> {
            ProviderSpendScope workerPrevious = CURRENT.get();
            CURRENT.set(captured);
            try {
                return task.call();
            } finally {
                restore(workerPrevious);
            }
        };
    }

    @Override public synchronized void close() {
        closed = true;
        restore(previous);
    }

    private static void restore(ProviderSpendScope scope) {
        if (scope == null) CURRENT.remove();
        else CURRENT.set(scope);
    }

    static final class Denied extends RuntimeException {
        private final boolean unavailable;
        private final boolean attempted;

        private Denied(boolean unavailable, boolean attempted) {
            super("Paid provider request was not admitted.");
            this.unavailable = unavailable;
            this.attempted = attempted;
        }

        boolean unavailable() { return unavailable; }
        boolean attempted() { return attempted; }
    }
}
