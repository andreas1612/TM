package com.treppides.taskmanager.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class EsoftDataSourceConfig {

    @Bean("esoftDataSource")
    @ConfigurationProperties(prefix = "esoft.datasource")
    public DataSource esoftDataSource() {
        return DataSourceBuilder.create().build();
    }

    @Bean("esoftJdbcTemplate")
    public JdbcTemplate esoftJdbcTemplate(@Qualifier("esoftDataSource") DataSource esoftDataSource) {
        return new JdbcTemplate(esoftDataSource);
    }
}
