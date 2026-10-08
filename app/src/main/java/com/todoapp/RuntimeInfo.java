package com.todoapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Deployment details shown in the UI footer (useful to watch blue/green deployments). */
@Component
public class RuntimeInfo {

    private final String version;
    private final String databaseHost;
    private final String cacheHost;
    private final String hostname;

    public RuntimeInfo(@Value("${todo.version:dev}") String version,
                       @Value("${todo.database-host:localhost}") String databaseHost,
                       @Value("${spring.data.redis.host:localhost}") String cacheHost) {
        this.version = version;
        this.databaseHost = databaseHost;
        this.cacheHost = cacheHost;
        String host = System.getenv("HOSTNAME");
        this.hostname = host == null ? "local" : host;
    }

    public String getVersion() {
        return version;
    }

    public String getDatabaseHost() {
        return databaseHost;
    }

    public String getCacheHost() {
        return cacheHost;
    }

    public String getHostname() {
        return hostname;
    }
}
