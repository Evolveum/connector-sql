/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;

/**
 * JSON parser for SQL operations.
 * Reuses connector-scimrest JSON utilities for parsing JSONB/CLOB columns.
 */
public class SqlJsonParser {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public JsonNode parse(byte[] jsonBytes) throws IOException {
        if (jsonBytes == null) {
            return null;
        }
        return OBJECT_MAPPER.readTree(jsonBytes);
    }

    public JsonNode parse(String jsonString) throws IOException {
        if (jsonString == null) {
            return null;
        }
        return OBJECT_MAPPER.readTree(jsonString);
    }

    public byte[] toJsonBytes(JsonNode node) throws IOException {
        if (node == null) {
            return null;
        }
        return OBJECT_MAPPER.writeValueAsBytes(node);
    }

    public Map<String, Object> toObject(byte[] jsonBytes) throws IOException {
        if (jsonBytes == null) {
            return null;
        }
        return OBJECT_MAPPER.readValue(jsonBytes, Map.class);
    }

    public Map<String, Object> toObject(JsonNode node) {
        if (node == null) {
            return null;
        }
        return OBJECT_MAPPER.convertValue(node, Map.class);
    }

    public boolean isJson(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }
        try {
            OBJECT_MAPPER.readTree(data);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}