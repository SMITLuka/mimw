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
        String branch) {

    /** Main data library, e.g. KB0D1 (dataGroup + "D" + branch). Null when dataGroup is not set. */
    public String mainLibrary() {
        return hasDataGroup() ? dataGroup + "D" + branchOrDefault() : null; //$NON-NLS-1$
    }

    /** HF tables library, e.g. KB0D1K. Null when dataGroup is not set. */
    public String hfLibrary() {
        return hasDataGroup() ? prefix() + "1K" : null; //$NON-NLS-1$
    }

    /** P library, e.g. KB0DP. Null when dataGroup is not set. */
    public String pLibrary() {
        return hasDataGroup() ? prefix() + "P" : null; //$NON-NLS-1$
    }

    /** Identifies the connection pool: same URL + user + password share one pool. */
    public String poolKey() {
        return datasourceUrl + "|" + datasourceUsername + "|" + sha256(datasourcePassword); //$NON-NLS-1$ //$NON-NLS-2$
    }

    private boolean hasDataGroup() {
        return dataGroup != null && !dataGroup.isBlank();
    }

    private String branchOrDefault() {
        return branch != null && !branch.isBlank() ? branch.trim() : "1"; //$NON-NLS-1$
    }

    private String prefix() {
        String lib = dataGroup.trim() + "D" + branchOrDefault(); //$NON-NLS-1$
        return lib.substring(0, Math.min(4, lib.length()));
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
                + ", linkedServer=" + linkedServer + ", catalog=" + catalog + ", dataGroup=" + dataGroup + ", branch=" + branch + "]"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
    }
}
