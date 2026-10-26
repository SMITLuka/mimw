package com.smit.mimw.tenant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Everything MIMW needs to serve one company: the Pantheon SQL Server connection and the
 * AS400 settings used to reach the AS400 tables through the SQL Server linked server.
 * Comes from Integration Hub (per company) or, when Hub is not configured, from env variables.
 */
public record TenantSettings(
        String source,
        String datasourceUrl,
        String datasourceUsername,
        String datasourcePassword,
        String datasourceDriver,
        String linkedServer,
        String catalog,
        String dataGroup,
        String branch,
        String as400Host,
        String as400User,
        String as400Password) {

    /** true when AS400_HOST is set: AS400 tables are read directly (JT400) instead of through the linked server. */
    public boolean directAs400() {
        return as400Host != null && !as400Host.isBlank();
    }

    /** Identifies the direct AS400 pool: same host + user + password share one pool. */
    public String as400PoolKey() {
        return as400Host + "|" + as400User + "|" + sha256(as400Password); //$NON-NLS-1$ //$NON-NLS-2$
    }

    // AS400 library names are the data group (AS400_DATA_GROUP, e.g. KB0) followed by a fixed suffix.
    // They do not depend on AS400_BRANCH: all branches of a data group share the same libraries.

    /** Main data library, e.g. KB0D1. Null when dataGroup is not set. */
    public String mainLibrary() {
        return hasDataGroup() ? dataGroup.trim() + "D1" : null; //$NON-NLS-1$
    }

    /** HF tables library, e.g. KB0D1K. Null when dataGroup is not set. */
    public String hfLibrary() {
        return hasDataGroup() ? dataGroup.trim() + "D1K" : null; //$NON-NLS-1$
    }

    /** P library, e.g. KB0DP. Null when dataGroup is not set. */
    public String pLibrary() {
        return hasDataGroup() ? dataGroup.trim() + "DP" : null; //$NON-NLS-1$
    }

    /**
     * Branch number the vehicle query is limited to, or null when AS400_BRANCH is not set:
     * then all branches of the mandator (data group) are included.
     */
    public Integer branchFilter() {
        if (branch == null || branch.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(branch.trim());
        } catch (NumberFormatException e) {
            throw new TenantConfigurationException("AS400_BRANCH mora biti broj poslovnice (npr. 1) ili prazan, a upisano je '" //$NON-NLS-1$
                    + branch.trim() + "' (" + source + ")."); //$NON-NLS-1$ //$NON-NLS-2$
        }
    }

    /** Identifies the connection pool: same URL + user + password share one pool. */
    public String poolKey() {
        return datasourceUrl + "|" + datasourceUsername + "|" + sha256(datasourcePassword); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private boolean hasDataGroup() {
        return dataGroup != null && !dataGroup.isBlank();
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256") //$NON-NLS-1$
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8)); //$NON-NLS-1$
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Never log the password. */
    @Override
    public String toString() {
        return "TenantSettings[source=" + source + ", url=" + datasourceUrl + ", user=" + datasourceUsername //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                + ", linkedServer=" + linkedServer + ", catalog=" + catalog + ", dataGroup=" + dataGroup + ", branch=" + branch + ", as400Host=" + as400Host + ", as400User=" + as400User + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }
}
