package com.smit.mimw.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Configures the datasource.
 *
  * On Render: connects to SQL Server (Pantheon DB), which in turn reaches
 * AS400 via a configured Linked Server — no direct AS400 exposure needed.
 *
 * Required env vars on Render:
 *   SPRING_DATASOURCE_URL      — jdbc:sqlserver://HOST:1433;databaseName=DB;encrypt=true;trustServerCertificate=true
 *   SPRING_DATASOURCE_USERNAME — SQL Server username
 *   SPRING_DATASOURCE_PASSWORD — SQL Server password
 *   AS400_LINKED_SERVER        — Linked server name (or leave blank to auto-read from _cdp_param)
 */
@Configuration
public class As400Config {

    private static final Logger log = LoggerFactory.getLogger(As400Config.class);

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password:}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Bean
    @Primary
    public DataSource dataSource() {
        log.info("DataSource configured → driver={}, url={}, username={}", driverClassName, url, username);
        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName(driverClassName)
                .build();
    }
}
