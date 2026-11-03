package com.adrovis.influencer.collector.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Secondary DataSource configuration specifically targeting the Creator Collector Supabase project.
 * All repository calls inside com.adrovis.influencer.collector.repository will route here.
 */
@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.adrovis.influencer.collector.repository",
        entityManagerFactoryRef = "collectorEntityManagerFactory",
        transactionManagerRef = "collectorTransactionManager"
)
public class CollectorDbConfig {

    @Bean(name = "collectorDataSourceProperties")
    @ConfigurationProperties("collector.datasource")
    public DataSourceProperties collectorDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "collectorDataSource")
    public DataSource collectorDataSource(
            @Qualifier("collectorDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean(name = "collectorEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean collectorEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("collectorDataSource") DataSource dataSource) {

        Map<String, Object> properties = new HashMap<>();
        properties.put("hibernate.hbm2ddl.auto", "none");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");

        return builder
                .dataSource(dataSource)
                .packages("com.adrovis.influencer.collector.entity")
                .persistenceUnit("collectorPU")
                .properties(properties)
                .build();
    }

    @Bean(name = "collectorTransactionManager")
    public PlatformTransactionManager collectorTransactionManager(
            @Qualifier("collectorEntityManagerFactory") LocalContainerEntityManagerFactoryBean factory) {
        return new JpaTransactionManager(Objects.requireNonNull(factory.getObject()));
    }
}