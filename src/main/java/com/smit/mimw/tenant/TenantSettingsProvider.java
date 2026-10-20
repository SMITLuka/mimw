package com.smit.mimw.tenant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Supplies the settings of a company.
 *
 * Hub mode (INTEGRATION_HUB_URL set): settings are read from Integration Hub per
 * mandatorId/companyId and cached for INTEGRATION_HUB_CACHE_SECONDS. If Hub is unreachable
 * when the cache expires, the last known settings keep being used (logged as a warning).
 *
 * Environment mode (INTEGRATION_HUB_URL empty): one set of settings from env variables for
 * every request — the behaviour MIMW had before Integration Hub.
 *
 * Config keys (same names in Hub and as env variables): SPRING_DATASOURCE_URL,
 * SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD, SPRING_DATASOURCE_DRIVER,
 * AS400_LINKED_SERVER, AS400_CATALOG, AS400_DATA_GROUP, AS400_BRANCH.
 */
public class TenantSettingsProvider {

    private static final Logger log = LoggerFactory.getLogger(TenantSettingsProvider.class);

    static final String DEFAULT_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver"; //$NON-NLS-1$
    static final String DEFAULT_CATALOG = "ADRIAVC1"; //$NON-NLS-1$
    static final String DEFAULT_BRANCH = "1"; //$NON-NLS-1$

    private final IntegrationHubClient hubClient;
    private final TenantSettings environmentSettings;
    private final Duration cacheTtl;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(TenantSettings settings, Instant loadedAt) {
    }

    /**
     * @param hubClient           null = environment mode
     * @param environmentSettings settings from env variables (used in environment mode)
     */
    public TenantSettingsProvider(IntegrationHubClient hubClient, TenantSettings environmentSettings, Duration cacheTtl) {
        this.hubClient = hubClient;
        this.environmentSettings = environmentSettings;
        this.cacheTtl = cacheTtl;
    }

    public boolean isHubEnabled() {
        return hubClient != null;
    }

    public TenantSettings fromEnvironment() {
        return environmentSettings;
    }

    public TenantSettings forTenant(TenantKey key) {
        Cached cached = cache.get(key.cacheKey());
        if (cached != null && cached.loadedAt().plus(cacheTtl).isAfter(Instant.now())) {
            return cached.settings();
        }
        try {
            TenantSettings fresh = toSettings(key, hubClient.fetch(key));
            cache.put(key.cacheKey(), new Cached(fresh, Instant.now()));
            return fresh;
        } catch (TenantConfigurationException e) {
            if (cached != null) {
                log.warn("Integration Hub lookup for {} failed ({}); using cached settings from {}", key, e.getMessage(), cached.loadedAt()); //$NON-NLS-1$
                return cached.settings();
            }
            throw e;
        }
    }

    /** Forget cached settings, so the next request reads them from Hub again. */
    public void evictAll() {
        cache.clear();
    }

    private static TenantSettings toSettings(TenantKey key, IntegrationHubClient.HubConfiguration hub) {
        Map<String, String> config = hub.config() == null ? Map.of() : hub.config();
        Map<String, String> additional = hub.additionalData() == null ? Map.of() : hub.additionalData();

        String url = value(config, additional, "SPRING_DATASOURCE_URL"); //$NON-NLS-1$
        if (url == null) {
            throw new TenantConfigurationException("U Integration Hubu za " + key //$NON-NLS-1$
                    + " nije upisan SPRING_DATASOURCE_URL u konfiguraciji '" + hub.interfaceName() + "'"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        return new TenantSettings(
                "hub:" + key, //$NON-NLS-1$
                url,
                orDefault(value(config, additional, "SPRING_DATASOURCE_USERNAME"), ""), //$NON-NLS-1$ //$NON-NLS-2$
                orDefault(value(config, additional, "SPRING_DATASOURCE_PASSWORD"), ""), //$NON-NLS-1$ //$NON-NLS-2$
                orDefault(value(config, additional, "SPRING_DATASOURCE_DRIVER"), DEFAULT_DRIVER), //$NON-NLS-1$
                value(config, additional, "AS400_LINKED_SERVER"), //$NON-NLS-1$
                orDefault(value(config, additional, "AS400_CATALOG"), DEFAULT_CATALOG), //$NON-NLS-1$
                value(config, additional, "AS400_DATA_GROUP"), //$NON-NLS-1$
                orDefault(value(config, additional, "AS400_BRANCH"), DEFAULT_BRANCH)); //$NON-NLS-1$
    }

    /** Interface configuration first, then the company's Additional Data; blank counts as not set. */
    private static String value(Map<String, String> config, Map<String, String> additional, String key) {
        String v = config.get(key);
        if (v == null || v.isBlank()) {
            v = additional.get(key);
        }
        return v == null || v.isBlank() ? null : v.trim();
    }

    private static String orDefault(String value, String fallback) {
        return value != null ? value : fallback;
    }
}
