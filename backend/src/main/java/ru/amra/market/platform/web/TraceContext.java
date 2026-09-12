package ru.amra.market.platform.web;

import java.util.UUID;
import org.slf4j.MDC;

/** Provides the correlation identifier bound to the current request thread. */
public final class TraceContext {

    public static final String HEADER_NAME = "X-Trace-Id";
    static final String MDC_KEY = "traceId";

    private TraceContext() {}

    /** Returns the current correlation identifier, with a safe fallback for non-HTTP callers. */
    public static String currentId() {
        var traceId = MDC.get(MDC_KEY);
        return traceId == null || traceId.isBlank() ? "system-" + UUID.randomUUID() : traceId;
    }
}
