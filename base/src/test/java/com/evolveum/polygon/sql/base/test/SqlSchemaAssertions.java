/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.test;

import org.identityconnectors.framework.common.objects.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Fluent assertions for SQL connector schema validation.
 * <p>
 * Eliminates the repeated pattern of:</p>
 * <pre>{@code
 * var schema = connector.schema();
 * var types = schema.getObjectClassInfo().stream()
 *     .map(ObjectClassInfo::getType).collect(Collectors.toList());
 * assertThat(types).contains("Person", "Team");
 *
 * var personOci = schema.getObjectClassInfo().stream()
 *     .filter(o -> "Person".equals(o.getType()))
 *     .findFirst().orElseThrow();
 * var attrs = personOci.getAttributeInfo().stream()
 *     .collect(Collectors.toMap(AttributeInfo::getName, a -> a));
 * assertThat(attrs.get(Uid.NAME).getNativeName()).isEqualTo("user_id");
 * }</pre>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * var schema = connector.schema();
 *
 * sqlAssert(schema)
 *     .hasObjectClasses("Person", "Team")
 *     .doesNotHaveObjectClasses("app_user", "app_group");
 *
 * sqlAssert(schema)
 *     .objectClass("Person")
 *     .hasUidColumn("user_id")
 *     .hasNameColumn("user_name")
 *     .hasAttribute("emailAddress")
 *         .nativeName("user_email")
 *         .type(String.class);
 * }</pre>
 */
public final class SqlSchemaAssertions {

    private final Schema schema;

    private SqlSchemaAssertions(Schema schema) {
        this.schema = schema;
    }

    /**
     * Create assertions for a schema.
     */
    public static SqlSchemaAssertions sqlAssert(Schema schema) {
        return new SqlSchemaAssertions(schema);
    }

    // ─── Object class assertions ───

    /**
     * Assert the schema contains all specified object class names.
     * Names are matched case-insensitively.
     */
    public SqlSchemaAssertions hasObjectClasses(String... names) {
        var schemaNames = schema.getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType)
                .collect(Collectors.toList());
        assertThat(schemaNames)
                .as("Schema should contain object classes %s", Arrays.toString(names))
                .containsAll(List.of(names));
        return this;
    }

    /**
     * Assert the schema does NOT contain any of the specified object class names.
     */
    public SqlSchemaAssertions doesNotHaveObjectClasses(String... names) {
        var schemaNames = schema.getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType)
                .collect(Collectors.toList());
        assertThat(schemaNames)
                .as("Schema should not contain object classes %s", Arrays.toString(names))
                .doesNotContain(names);
        return this;
    }

    /**
     * Assert the schema contains all specified object class names (lowercase).
     */
    public SqlSchemaAssertions hasObjectClassesInsensitive(String... names) {
        var schemaNames = schema.getObjectClassInfo().stream()
                .map(ObjectClassInfo::getType)
                .map(String::toLowerCase)
                .collect(Collectors.toList());
        assertThat(schemaNames)
                .as("Schema should contain object classes %s", Arrays.toString(names))
                .containsAll(List.of(names).stream().map(String::toLowerCase).toList());
        return this;
    }

    // ─── Object class detail assertions ───

    /**
     * Get assertions for a specific object class.
     * Throws AssertionError if the object class is not found.
     */
    public ObjectClassAssert objectClass(String name) {
        var oci = schema.getObjectClassInfo().stream()
                .filter(oc -> oc.getType().equals(name) || oc.getType().equalsIgnoreCase(name))
                .findFirst().orElse(null);
        if (oci == null) {
            fail("Object class not found: " + name +
                    " (available: " + schema.getObjectClassInfo().stream()
                            .map(ObjectClassInfo::getType).collect(Collectors.toList()) + ")");
        }
        return new ObjectClassAssert(oci);
    }

    /**
     * Assertions for a specific object class.
     */
    public static final class ObjectClassAssert {
        private final ObjectClassInfo oci;
        private final Map<String, AttributeInfo> attrs;

        private ObjectClassAssert(ObjectClassInfo oci) {
            this.oci = oci;
            this.attrs = oci.getAttributeInfo().stream()
                    .collect(Collectors.toMap(AttributeInfo::getName, Function.identity()));
        }

        /**
         * Assert the UID column maps to the given native name.
         */
        public ObjectClassAssert hasUidColumn(String nativeName) {
            var uid = attrs.get(Uid.NAME);
            assertThat(uid)
                    .as("Object class %s should have __UID__ attribute", oci.getType())
                    .isNotNull();
            assertThat(uid.getNativeName())
                    .as("__UID__ should map to %s", nativeName)
                    .isEqualTo(nativeName);
            return this;
        }

        /**
         * Assert the Name column maps to the given native name.
         */
        public ObjectClassAssert hasNameColumn(String nativeName) {
            var name = attrs.get(Name.NAME);
            assertThat(name)
                    .as("Object class %s should have __NAME__ attribute", oci.getType())
                    .isNotNull();
            assertThat(name.getNativeName())
                    .as("__NAME__ should map to %s", nativeName)
                    .isEqualTo(nativeName);
            return this;
        }

        /**
         * Assert the object class is embedded.
         */
        public ObjectClassAssert isEmbedded() {
            assertThat(oci.isEmbedded())
                    .as("Object class %s should be embedded", oci.getType())
                    .isTrue();
            return this;
        }

        /**
         * Assert the object class is NOT embedded.
         */
        public ObjectClassAssert isNotEmbedded() {
            assertThat(oci.isEmbedded())
                    .as("Object class %s should not be embedded", oci.getType())
                    .isFalse();
            return this;
        }

        /**
         * Assert an attribute exists by name (case-sensitive).
         */
        public AttributeAssert hasAttribute(String name) {
            var attr = attrs.get(name);
            assertThat(attr)
                    .as("Object class %s should have attribute %s", oci.getType(), name)
                    .isNotNull();
            return new AttributeAssert(attr);
        }

        /**
         * Assert an attribute exists by name (case-insensitive).
         */
        public AttributeAssert hasAttributeInsensitive(String name) {
            for (var attr : attrs.values()) {
                if (attr.getName().equalsIgnoreCase(name)) {
                    return new AttributeAssert(attr);
                }
            }
            fail("Object class " + oci.getType() + " should have attribute " + name);
            return null;
        }

        /**
         * Assert the object class contains all specified attribute names.
         */
        public ObjectClassAssert hasAttributes(String... names) {
            var attrNames = oci.getAttributeInfo().stream()
                    .map(AttributeInfo::getName)
                    .collect(Collectors.toList());
            assertThat(attrNames)
                    .as("Object class %s should contain attributes %s", oci.getType(), Arrays.toString(names))
                    .containsAll(List.of(names));
            return this;
        }

        /**
         * Assert the object class attribute names contain all specified (case-insensitive).
         */
        public ObjectClassAssert hasAttributesInsensitive(String... names) {
            var attrNames = oci.getAttributeInfo().stream()
                    .map(AttributeInfo::getName)
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
            assertThat(attrNames)
                    .as("Object class %s should contain attributes %s", oci.getType(), Arrays.toString(names))
                    .containsAll(List.of(names).stream().map(String::toLowerCase).toList());
            return this;
        }

        /**
         * Assert an attribute does NOT exist.
         */
        public ObjectClassAssert missingAttribute(String name) {
            assertThat(attrs.get(name))
                    .as("Object class %s should not have attribute %s", oci.getType(), name)
                    .isNull();
            return this;
        }

        /**
         * Get attribute count.
         */
        public ObjectClassAssert hasAttributeCount(int expected) {
            assertThat(oci.getAttributeInfo().size())
                    .as("Object class %s should have %d attributes but has %d",
                            oci.getType(), expected, oci.getAttributeInfo().size())
                    .isEqualTo(expected);
            return this;
        }
    }

    /**
     * Assertions for a specific attribute.
     */
    public static final class AttributeAssert {
        private final AttributeInfo attr;

        private AttributeAssert(AttributeInfo attr) {
            this.attr = attr;
        }

        /**
         * Assert native name.
         */
        public AttributeAssert nativeName(String expected) {
            assertThat(attr.getNativeName())
                    .as("Attribute %s nativeName should be %s", attr.getName(), expected)
                    .isEqualTo(expected);
            return this;
        }

        /**
         * Assert attribute type.
         */
        public AttributeAssert type(Class<?> expected) {
            assertThat(attr.getType())
                    .as("Attribute %s type should be %s", attr.getName(), expected.getSimpleName())
                    .isEqualTo(expected);
            return this;
        }

        /**
         * Assert attribute is required.
         */
        public AttributeAssert isRequired() {
            assertThat(attr.isRequired())
                    .as("Attribute %s should be required", attr.getName())
                    .isTrue();
            return this;
        }

        /**
         * Assert attribute is not required.
         */
        public AttributeAssert isNotRequired() {
            assertThat(attr.isRequired())
                    .as("Attribute %s should not be required", attr.getName())
                    .isFalse();
            return this;
        }

        /**
         * Assert attribute is multi-valued.
         */
        public AttributeAssert isMultiValued() {
            assertThat(attr.isMultiValued())
                    .as("Attribute %s should be multi-valued", attr.getName())
                    .isTrue();
            return this;
        }

        /**
         * Assert attribute is single-valued.
         */
        public AttributeAssert isSingleValued() {
            assertThat(attr.isMultiValued())
                    .as("Attribute %s should be single-valued", attr.getName())
                    .isFalse();
            return this;
        }

        /**
         * Get the attribute info for further assertions.
         */
        public AttributeInfo getAttributeInfo() {
            return attr;
        }
    }
}
