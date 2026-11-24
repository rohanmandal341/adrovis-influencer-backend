package com.adrovis.influencer.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(prefix = "security.datasource", name = "url")
public class SecurityDataSourceConfig {

    @Bean(name = "securityDataSourceProperties")
    @ConfigurationProperties("security.datasource")
    public DataSourceProperties securityDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "securityDataSource")
    @ConfigurationProperties("security.datasource.hikari")
    public DataSource securityDataSource(
            @Qualifier("securityDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "securityJdbcTemplate")
    public NamedParameterJdbcTemplate securityJdbcTemplate(
            @Qualifier("securityDataSource") DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    @Bean(name = "securityTransactionManager")
    public PlatformTransactionManager securityTransactionManager(
            @Qualifier("securityDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}