package com.smit.mimw.tenant;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.AbstractDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DataSource that hands out connections to the database of the company in {@link TenantContext}.
 * One Hikari pool per distinct URL/user/password, created on first use and kept for reuse.
 * Outside a request (or a request without tenant headers) it uses the env settings in environment
 * mode and refuses in Hub mode, because there is no company to connect to.
 */
public class RoutingDataSource extends AbstractDataSource implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RoutingDataSource.class);

    private final TenantSettingsProvider provider;
    private final Map<String, HikariDataSource> pools = new ConcurrentHashMap<>();

    public RoutingDataSource(TenantSettingsProvider provider) {
        this.provider = provider;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return pool(currentSettings()).getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        return pool(currentSettings()).getConnection(username, password);
    }

    private TenantSettings currentSettings() throws SQLException {
        TenantSettings settings = TenantContext.get();
        if (settings != null) {
            return settings;
        }
        if (!provider.isHubEnabled()) {
            return provider.fromEnvironment();
        }
        throw new SQLException("Nema mandatorId/companyId headera — ne znam na čiju bazu se spojiti"); //$NON-NLS-1$
    }

    private HikariDataSource pool(TenantSettings settings) {
        return pools.computeIfAbsent(settings.poolKey(), k -> createPool(settings));
    }

    private HikariDataSource createPool(TenantSettings settings) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName("mimw-" + (pools.size() + 1)); //$NON-NLS-1$
        cfg.setDriverClassName(settings.datasourceDriver());
        cfg.setJdbcUrl(settings.datasourceUrl());
        cfg.setUsername(settings.datasourceUsername());
        cfg.setPassword(settings.datasourcePassword());
        cfg.setConnectionTimeout(60000);
        cfg.setMaximumPoolSize(5);
        cfg.setMinimumIdle(0);
        cfg.setIdleTimeout(300000);
        cfg.setMaxLifetime(600000);
        cfg.setInitializationFailTimeout(-1);
        log.info("Creating connection pool {} for {}", cfg.getPoolName(), settings); //$NON-NLS-1$
        return new HikariDataSource(cfg);
    }

    @Override
    public void close() {
        pools.values().forEach(HikariDataSource::close);
        pools.clear();
    }
}
