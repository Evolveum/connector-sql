/*
 * Copyright (c) 2025 Evolveum and contributors
 *
 * This work is licensed under European Union Public License v1.2. See LICENSE file for details.
 *
 */
package com.evolveum.polygon.conndev.build.api;

import com.evolveum.polygon.conndev.annotations.Script;
import com.evolveum.polygon.conndev.build.spi.SpiSchemaBuilder;
import com.evolveum.polygon.conndev.concepts.GroovyClosures;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;

/**
 * Schema-level builder for defining object classes and relationships.
 *
 * <p>This is the top-level builder interface that allows configuring the complete
 * connector schema: object classes with their attributes, and SCIM2 relationships
 * between object classes.</p>
 *
 * @param <SB> The concrete schema builder type (self-type for CRTP)
 * @param <OB> The object class schema builder type
 */
public interface SchemaBuilder<SB extends SchemaBuilder<SB, OB>, OB extends ObjectClassSchemaBuilder<OB,?,?>> extends SpiSchemaBuilder<SB, OB> {

    /**
     * Creates or gets an object class schema builder by name.
     *
     * @param name the object class name (used as the ConnId object class value)
     * @return the object class schema builder for further configuration
     */
    OB objectClass(String name);

    /**
     * Creates or gets an object class schema builder by name, applying a closure to configure it.
     *
     * @param name the object class name
     * @param closure a closure that configures the {@link ObjectClassSchemaBuilder} instance
     * @return the configured object class schema builder
     */
    default OB objectClass(String name,
                                         @Script.Initialization
                                         @DelegatesTo(ObjectClassSchemaBuilder.class)  Closure<?> closure) {
        return GroovyClosures.callAndReturnDelegate(closure, objectClass(name));
    }

    /**
     * Creates a relationship builder with the given name, applying a closure to configure it.
     *
     * @param name the relationship name
     * @param closure a closure that configures the {@link RelationshipBuilder} instance
     * @return the configured relationship builder
     */
    RelationshipBuilder relationship(String name,
                                     @Script.Initialization
                                     @DelegatesTo(RelationshipBuilder.class) Closure<?> closure);

}
