package com.contractor.app.common.api;

import java.time.Instant;

public class HealthResponse {

    private final String status;
    private final String database;
    private final Instant timestamp;

    public HealthResponse(String status, String database, Instant timestamp) {
        this.status = status;
        this.database = database;
        this.timestamp = timestamp;
    }

    public String getStatus() {
        return status;
    }

    public String getDatabase() {
        return database;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
