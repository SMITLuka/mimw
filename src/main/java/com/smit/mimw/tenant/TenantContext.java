package com.smit.mimw.tenant;

/**
 * Holds the settings of the company whose request is being processed on this thread.
 * Set by {@link TenantInterceptor} from the mandatorId/companyId headers Ares sends,
 * cleared when the request ends.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantSettings> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(TenantSettings settings) {
        CURRENT.set(settings);
    }

    /** @return the current company's settings, or null outside a request */
    public static TenantSettings get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
