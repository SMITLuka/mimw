package com.smit.mimw.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.time.LocalDate;

/**
 * Configures the AS400 DataSource.
 *
 * Password convention: MMYY — e.g. March 2026 → "0326", April 2026 → "0426".
 * The password is computed at application startup. If the app runs across a
 * month boundary, restart it so a fresh connection pool picks up the new password.
 *
 * To override the dynamic password set environment variable SPRING_DATASOURCE_PASSWORD.
 */
@Configuration
public class As400Config {

    private static final Logger log = LoggerFactory.getLogger(As400Config.class);

    @Value("${spring.datasource.url}")
    private String url;

    @Value("${spring.datasource.username}")
    private String username;

    /** Optional manual override — if blank/absent the password is computed dynamically. */
    @Value("${spring.datasource.password:}")
    private String passwordOverride;

    @Bean
    @Primary
    public DataSource as400DataSource() {
        String password = (passwordOverride != null && !passwordOverride.isBlank())
                ? passwordOverride
                : resolvePassword();

        log.info("AS400 DataSource configured → url={}, username={}, passwordMonth={}",
                url, username, LocalDate.now().getMonthValue());

        return DataSourceBuilder.create()
                .url(url)
                .username(username)
                .password(password)
                .driverClassName("com.ibm.as400.access.AS400JDBCDriver")
                .build();
    }

    /**
     * Computes the current password as MMYY (e.g. March 2026 = "0326").
     */
    public static String resolvePassword() {
        LocalDate now = LocalDate.now();
        return String.format("%02d%02d", now.getMonthValue(), now.getYear() % 100);
    }
}
