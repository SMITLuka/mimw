package com.smit.mimw.config;

import com.smit.mimw.tenant.As400DirectPools;
import com.smit.mimw.tenant.IntegrationHubClient;
import com.smit.mimw.tenant.RoutingDataSource;
import com.smit.mimw.tenant.TenantInterceptor;
import com.smit.mimw.tenant.TenantSettings;
import com.smit.mimw.tenant.TenantSettingsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * Configures where MIMW gets each company's database and AS400 settings.
 *
 * Integration Hub mode — INTEGRATION_HUB_URL is set:
 *   every /mimv request carries mandatorId + companyId headers (sent by Ares); MIMW asks Integration Hub
 *   (GET /service/configuration) for that company's "MIMW integration" configuration and connects to the
 *   SQL Server named there.
 *     INTEGRATION_HUB_URL            — e.g. http://integration-hub-middlelayer.128.140.44.96.sslip.io
 *     INTEGRATION_HUB_API_KEY        — must be one of SERVICE_API_KEYS in Integration Hub
 *     INTEGRATION_HUB_INTERFACE      — interface name in Hub (default "MIMW integration")
 *     INTEGRATION_HUB_CACHE_SECONDS  — how long settings are cached (default 300)
 *
 * Environment mode — INTEGRATION_HUB_URL is empty: one company, settings from env variables
 *   (SPRING_DATASOURCE_URL/_USERNAME/_PASSWORD, AS400_LINKED_SERVER, AS400_CATALOG, AS400_DATA_GROUP, AS400_BRANCH).
 */
@Configuration
public class As400Config implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(As400Config.class);

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${as400.linked-server:}")
    private String linkedServer;

    @Value("${as400.catalog:ADRIAVC1}")
    private String catalog;

    @Value("${as400.data-group:}")
    private String dataGroup;

    @Value("${as400.branch:}")
    private String branch;

    @Value("${as400.host:}")
    private String as400Host;

    @Value("${as400.user:}")
    private String as400User;

    @Value("${as400.password:}")
    private String as400Password;

    @Value("${integration-hub.url:}")
    private String hubUrl;

    @Value("${integration-hub.api-key:}")
    private String hubApiKey;

    @Value("${integration-hub.interface-name:MIMW integration}")
    private String hubInterfaceName;

    @Value("${integration-hub.cache-seconds:300}")
    private long hubCacheSeconds;

    @Bean
    public TenantSettingsProvider tenantSettingsProvider() {
        TenantSettings env = new TenantSettings("environment", url, username, password, driverClassName, //$NON-NLS-1$
                linkedServer, catalog, dataGroup, branch, as400Host, as400User, as400Password);

        IntegrationHubClient hubClient = null;
        if (hubUrl != null && !hubUrl.isBlank()) {
            if (hubApiKey == null || hubApiKey.isBlank()) {
                throw new IllegalStateException("INTEGRATION_HUB_URL is set but INTEGRATION_HUB_API_KEY is empty"); //$NON-NLS-1$
            }
            hubClient = new IntegrationHubClient(hubUrl, hubApiKey, hubInterfaceName);
            log.info("Company settings: INTEGRATION HUB mode → url={}, interface='{}', cache={}s", hubUrl, hubInterfaceName, hubCacheSeconds); //$NON-NLS-1$
        } else {
            log.info("Company settings: ENVIRONMENT mode (INTEGRATION_HUB_URL not set) → {}", env); //$NON-NLS-1$
        }
        return new TenantSettingsProvider(hubClient, env, Duration.ofSeconds(hubCacheSeconds));
    }

    /** Direct AS400 (JT400) connections for companies with AS400_HOST set. */
    @Bean(destroyMethod = "close")
    public As400DirectPools as400DirectPools() {
        return new As400DirectPools();
    }

    @Bean(destroyMethod = "close")
    @Primary
    public DataSource dataSource(TenantSettingsProvider tenantSettingsProvider) {
        return new RoutingDataSource(tenantSettingsProvider);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new TenantInterceptor(tenantSettingsProvider())).addPathPatterns("/mimv/**"); //$NON-NLS-1$
    }
}
