/*
 * Copyright (c) 2026 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.sql.base.schema;

import com.evolveum.polygon.sql.base.schema.ChildTableRelationship.ChildTableType;
import org.testng.annotations.Test;

import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for child table relationship classification.
 */
@Test
public class ChildTableRelationshipTest {

    @Test
    public void singleValuePkEqualsFkOnly() {
        // user_profiles: PK=user_id=FK → SINGLE_VALUE_EMBEDDED
        var rel = new ChildTableRelationship.EmbeddedRelationship(
                "users", "user_profiles",
                List.of(new ChildTableRelationship.JoinKey("id", "user_id")),
                ChildTableType.SINGLE_VALUE_EMBEDDED, false);
        assertThat(rel.type()).isEqualTo(ChildTableType.SINGLE_VALUE_EMBEDDED);
        assertThat(rel.type().isEmbedded()).isTrue();
        assertThat(rel.type().isSingleValue()).isTrue();
        assertThat(rel.type().isJunction()).isFalse();
    }

    @Test
    public void multiValueCompositePk() {
        // user_emails: PK=(user_id, email), FK=user_id → MULTI_VALUE_EMBEDDED
        var rel = new ChildTableRelationship.EmbeddedRelationship(
                "users", "user_emails",
                List.of(new ChildTableRelationship.JoinKey("id", "user_id")),
                ChildTableType.MULTI_VALUE_EMBEDDED, false);
        assertThat(rel.type()).isEqualTo(ChildTableType.MULTI_VALUE_EMBEDDED);
        assertThat(rel.type().isEmbedded()).isTrue();
        assertThat(rel.type().isSingleValue()).isFalse();
    }

    @Test
    public void multiValueIndependentPk() {
        // user_addresses: PK=id, FK=user_id → MULTI_VALUE_EMBEDDED
        var rel = new ChildTableRelationship.EmbeddedRelationship(
                "users", "user_addresses",
                List.of(new ChildTableRelationship.JoinKey("id", "user_id")),
                ChildTableType.MULTI_VALUE_EMBEDDED, false);
        assertThat(rel.type()).isEqualTo(ChildTableType.MULTI_VALUE_EMBEDDED);
    }

    @Test
    public void junctionTableMultipleFks() {
        // user_group_membership: FKs to users and groups → JUNCTION_TABLE
        var rel = new ChildTableRelationship.JunctionRelationship(
                "users", "user_group_membership",
                List.of(new ChildTableRelationship.JoinKey("id", "user_id")),
                List.of(new ChildTableRelationship.JoinKey("id", "group_id")),
                "groups",
                ChildTableType.JUNCTION_TABLE, false);
        assertThat(rel.type()).isEqualTo(ChildTableType.JUNCTION_TABLE);
        assertThat(rel.type().isJunction()).isTrue();
        assertThat(rel.type().isEmbedded()).isFalse();
        assertThat(rel.parentTable()).isEqualTo("users");
        assertThat(rel.junctionTable()).isEqualTo("user_group_membership");
        assertThat(rel.targetTable()).isEqualTo("groups");
    }

    @Test
    public void simpleAttributeRelationship() {
        // user_emails: PK=(user_id, email), FK=user_id + 1 value col → MULTI_VALUE_ATTRIBUTE
        var valueCol = SqlColumnMeta.builder()
                .name("email")
                .typeName("VARCHAR")
                .typeCode(Types.VARCHAR)
                .javaType(String.class)
                .build();
        var rel = new ChildTableRelationship.SimpleAttributeRelationship(
                "users", "user_emails",
                List.of(new ChildTableRelationship.JoinKey("id", "user_id")),
                valueCol,
                ChildTableType.MULTI_VALUE_ATTRIBUTE, false);
        assertThat(rel.type()).isEqualTo(ChildTableType.MULTI_VALUE_ATTRIBUTE);
        assertThat(rel.type().isSimpleAttribute()).isTrue();
        assertThat(rel.type().isEmbedded()).isFalse();
        assertThat(rel.valueColumn()).isSameAs(valueCol);
        assertThat(rel.childTable()).isEqualTo("user_emails");
    }

    @Test
    public void simpleAttributeRejectsWrongType() {
        assertThatThrownBy(() -> new ChildTableRelationship.SimpleAttributeRelationship(
                "users", "user_emails",
                List.of(new ChildTableRelationship.JoinKey("id", "user_id")),
                null,
                ChildTableType.MULTI_VALUE_EMBEDDED, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MULTI_VALUE_ATTRIBUTE");
    }

    @Test
    public void conventionBasedFk() {
        var rel = new ChildTableRelationship.EmbeddedRelationship(
                "users", "user_profiles",
                List.of(new ChildTableRelationship.JoinKey("id", "user_id")),
                ChildTableType.SINGLE_VALUE_EMBEDDED, true);
        assertThat(rel.conventionBased()).isTrue();
    }

    @Test
    public void joinKeyFields() {
        var jk = new ChildTableRelationship.JoinKey("parentId", "userId");
        assertThat(jk.parentColumn()).isEqualTo("parentId");
        assertThat(jk.childColumn()).isEqualTo("userId");
    }

    @Test
    public void childTableTypeHelpers() {
        assertThat(ChildTableType.SINGLE_VALUE_EMBEDDED.isEmbedded()).isTrue();
        assertThat(ChildTableType.SINGLE_VALUE_EMBEDDED.isSingleValue()).isTrue();
        assertThat(ChildTableType.SINGLE_VALUE_EMBEDDED.isSimpleAttribute()).isFalse();
        assertThat(ChildTableType.SINGLE_VALUE_EMBEDDED.isMultiValue()).isFalse();

        assertThat(ChildTableType.MULTI_VALUE_EMBEDDED.isEmbedded()).isTrue();
        assertThat(ChildTableType.MULTI_VALUE_EMBEDDED.isSingleValue()).isFalse();
        assertThat(ChildTableType.MULTI_VALUE_EMBEDDED.isMultiValue()).isTrue();

        assertThat(ChildTableType.MULTI_VALUE_ATTRIBUTE.isSimpleAttribute()).isTrue();
        assertThat(ChildTableType.MULTI_VALUE_ATTRIBUTE.isEmbedded()).isFalse();
        assertThat(ChildTableType.MULTI_VALUE_ATTRIBUTE.isMultiValue()).isTrue();

        assertThat(ChildTableType.JUNCTION_TABLE.isJunction()).isTrue();
        assertThat(ChildTableType.JUNCTION_TABLE.isEmbedded()).isFalse();
        assertThat(ChildTableType.JUNCTION_TABLE.isSimpleAttribute()).isFalse();
        assertThat(ChildTableType.JUNCTION_TABLE.isMultiValue()).isFalse();
    }

    @Test
    public void sqlChildJoinConfigFields() {
        var joinKeys = List.of(
                new ChildTableRelationship.JoinKey("parentId", "childParentId"),
                new ChildTableRelationship.JoinKey("tenantId", "childTenantId"));
        var config = new SqlChildJoinConfig(
                "parentTable", "childTable", joinKeys, true, "childAttr");
        assertThat(config.parentTable()).isEqualTo("parentTable");
        assertThat(config.childTable()).isEqualTo("childTable");
        assertThat(config.joinKeys()).containsExactlyElementsOf(joinKeys);
        assertThat(config.multiValued()).isTrue();
        assertThat(config.targetAttributeName()).isEqualTo("childAttr");
        assertThat(config.valueColumn()).isNull();
    }

    @Test
    public void sqlChildJoinConfigWithValueColumn() {
        var config = new SqlChildJoinConfig(
                "parentTable", "childTable",
                List.of(new ChildTableRelationship.JoinKey("parentId", "childParentId")),
                true, "childAttr", "email");
        assertThat(config.valueColumn()).isEqualTo("email");
    }

    @Test
    public void sqlJunctionJoinConfigFields() {
        var parentKeys = List.of(new ChildTableRelationship.JoinKey("id", "user_id"));
        var targetKeys = List.of(new ChildTableRelationship.JoinKey("id", "group_id"));
        var config = new SqlJunctionJoinConfig(
                "users", "membership", parentKeys, targetKeys, "groups");
        assertThat(config.parentTable()).isEqualTo("users");
        assertThat(config.junctionTable()).isEqualTo("membership");
        assertThat(config.parentJoinKeys()).containsExactlyElementsOf(parentKeys);
        assertThat(config.targetJoinKeys()).containsExactlyElementsOf(targetKeys);
        assertThat(config.targetObjectClass()).isEqualTo("groups");
    }
}
