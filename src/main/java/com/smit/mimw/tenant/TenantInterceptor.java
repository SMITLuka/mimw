package com.smit.mimw.tenant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Resolves the company of every /mimv request from the mandatorId and companyId headers and
 * stores its settings in {@link TenantContext}, so the whole request uses that company's
 * database and AS400 settings. A lookup failure is thrown here, before the controller runs,
 * and turned into an error response by GlobalExceptionHandler.
 */
public class TenantInterceptor implements HandlerInterceptor {

    static final String MANDATOR_HEADER = "mandatorId"; //$NON-NLS-1$
    static final String COMPANY_HEADER = "companyId"; //$NON-NLS-1$

    private final TenantSettingsProvider provider;

    public TenantInterceptor(TenantSettingsProvider provider) {
        this.provider = provider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        TenantContext.clear();
        String mandatorId = decode(request.getHeader(MANDATOR_HEADER));
        String companyId = decode(request.getHeader(COMPANY_HEADER));
        if (provider.isHubEnabled()) {
            if (isBlank(mandatorId) || isBlank(companyId)) {
                // Leave the context empty; the controller rejects the missing header itself.
                return true;
            }
            TenantContext.set(provider.forTenant(new TenantKey(mandatorId, companyId)));
        } else {
            TenantContext.set(provider.fromEnvironment());
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }

    /**
     * Ares sends mandatorId / companyId percent-encoded (UTF-8), because a Pantheon company key can be a
     * name with Croatian letters and spaces (e.g. "AUTO KUĆA HABULIN d.o.o."), which the HTTP firewall
     * rejects in a raw header. Plain codes such as "000002" are not changed by decoding.
     */
    static String decode(String value) {
        if (value == null || value.indexOf('%') < 0) {
            return value;
        }
        try {
            return java.net.URLDecoder.decode(value.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8); //$NON-NLS-1$ //$NON-NLS-2$
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
