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
        String mandatorId = request.getHeader(MANDATOR_HEADER);
        String companyId = request.getHeader(COMPANY_HEADER);
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
