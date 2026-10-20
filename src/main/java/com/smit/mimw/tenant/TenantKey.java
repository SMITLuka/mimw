package com.smit.mimw.tenant;

/**
 * Identifies one company of one mandator, exactly as the DMS (Pantheon/Ares) sends it
 * in the mandatorId and companyId request headers.
 */
public record TenantKey(String mandatorId, String companyId) {

    public TenantKey {
        mandatorId = mandatorId == null ? "" : mandatorId.trim(); //$NON-NLS-1$
        companyId = companyId == null ? "" : companyId.trim(); //$NON-NLS-1$
    }

    public String cacheKey() {
        return mandatorId.toUpperCase() + "|" + companyId; //$NON-NLS-1$
    }

    @Override
    public String toString() {
        return mandatorId + "/" + companyId; //$NON-NLS-1$
    }
}
