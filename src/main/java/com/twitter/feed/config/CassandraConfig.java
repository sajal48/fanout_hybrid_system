package com.twitter.feed.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.cassandra.config.AbstractCassandraConfiguration;
import org.springframework.data.cassandra.config.SchemaAction;
import org.springframework.data.cassandra.core.cql.keyspace.CreateKeyspaceSpecification;
import org.springframework.data.cassandra.repository.config.EnableCassandraRepositories;

import java.util.Collections;
import java.util.List;

/**
 * Cassandra configuration for the feed system.
 * Configures connection to Cassandra cluster and keyspace settings.
 *
 * Follows Single Responsibility Principle - only handles Cassandra configuration.
 */
@Configuration
@EnableCassandraRepositories(basePackages = "com.twitter.feed.post.repository")
public class CassandraConfig extends AbstractCassandraConfiguration {

    @Value("${spring.data.cassandra.keyspace-name}")
    private String keyspaceName;

    @Value("${spring.data.cassandra.contact-points}")
    private String contactPoints;

    @Value("${spring.data.cassandra.port}")
    private int port;

    @Value("${spring.data.cassandra.local-datacenter}")
    private String localDatacenter;

    @Override
    protected String getKeyspaceName() {
        return keyspaceName;
    }

    @Override
    protected String getContactPoints() {
        return contactPoints;
    }

    @Override
    protected int getPort() {
        return port;
    }

    @Override
    protected String getLocalDataCenter() {
        return localDatacenter;
    }

    @Override
    public SchemaAction getSchemaAction() {
        return SchemaAction.NONE; // Schema is managed by init.cql
    }

    @Override
    protected List<CreateKeyspaceSpecification> getKeyspaceCreations() {
        // Keyspace already created by docker init script
        return Collections.emptyList();
    }

    @Override
    public String[] getEntityBasePackages() {
        return new String[]{"com.twitter.feed.post.model"};
    }
}
