package com.smit.mimw.tenant;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Direct read-only connections to AS400 (DB2 for i) through the JT400 driver, used for a company whose
 * settings contain AS400_HOST. One Hikari pool per distinct host/user/password, created on first use.
 * The AS400 must be reachable from this server (e.g. through the VPN tunnel on the host).
 */
public class As400DirectPools implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(As400DirectPools.class);

    private static final String DRIVER = "com.ibm.as400.access.AS400JDBCDriver"; //$NON-NLS-1$

    /** naming=sql: LIBRARY.TABLE; translate binary: CCSID 65535 fields as text; date format iso. */
    private static final String URL_OPTIONS = ";naming=sql;prompt=false;errors=full;translate binary=true;date format=iso;access=read only"; //$NON-NLS-1$

    private final Map<String, JdbcTemplate> templates = new ConcurrentHashMap<>();
    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    /** JdbcTemplate connected directly to the AS400 of the given company. */
    public JdbcTemplate template(TenantSettings settings) {
        return templates.computeIfAbsent(settings.as400PoolKey(), k -> new JdbcTemplate(pool(k, settings)));
    }

    private HikariDataSource pool(String key, TenantSettings settings) {
        return pools.computeIfAbsent(key, k -> {
            HikariConfig cfg = new HikariConfig();
            cfg.setPoolName("as400-" + (pools.size() + 1)); //$NON-NLS-1$
            cfg.setDriverClassName(DRIVER);
            cfg.setJdbcUrl("jdbc:as400://" + settings.as400Host().trim() + URL_OPTIONS); //$NON-NLS-1$
            cfg.setUsername(settings.as400User());
            cfg.setPassword(settings.as400Password());
            cfg.setReadOnly(true);
            cfg.setAutoCommit(true);
            cfg.setConnectionTimeout(30000);
            cfg.setMaximumPoolSize(3);
            cfg.setMinimumIdle(0);
            cfg.setIdleTimeout(300000);
            cfg.setMaxLifetime(600000);
            cfg.setConnectionTestQuery("SELECT 1 FROM SYSIBM.SYSDUMMY1"); //$NON-NLS-1$
            cfg.setInitializationFailTimeout(-1);
            log.info("Creating direct AS400 pool {} for host={}, user={} ({})", cfg.getPoolName(), //$NON-NLS-1$
                    settings.as400Host(), settings.as400User(), settings.source());
            return new HikariDataSource(cfg);
        });
    }

    @Override
    public void close() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
        templates.clear();
    }
}
