package com.smit.mimw.tenant;

/**
 * The settings of a company could not be obtained (Integration Hub unreachable, company or
 * MIMW configuration missing there, or a required key not filled in).
 */
public class TenantConfigurationException extends RuntimeException {

    public TenantConfigurationException(String message) {
        super(message);
    }

    public TenantConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
