/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.connection;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

/**
 * JSON parser for SQL operations.
 * Parses JSONB/CLOB columns and raw JSON strings.
 */
public class SqlJsonParser {

    private static final ObjectMapper OBJECT_MAPPER = createSecureMapper();

    private static ObjectMapper createSecureMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Don't fail on unknown properties (common in schema-less databases like PostgreSQL JSONB)
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, false);
        mapper.configure(DeserializationFeature.ACCEPT_EMPTY_ARRAY_AS_NULL_OBJECT, false);
        return mapper;
    }

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