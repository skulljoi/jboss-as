/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.clustering.controller;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceName;

/**
 * {@link org.jboss.as.controller.RestartParentWriteAttributeHandler} that leverages a {@link ResourceServiceBuilderFactory} for service recreation.
 * @author Paul Ferraro
 */
public class RestartParentWriteAttributeHandler<T> extends org.jboss.as.controller.RestartParentWriteAttributeHandler implements Registration {

    private final ResourceServiceBuilderFactory<T> builderFactory;
    private final Map<String, AttributeDefinition> attributes = new HashMap<>();

    public <E extends Enum<E> & Attribute> RestartParentWriteAttributeHandler(ResourceServiceBuilderFactory<T> builderFactory, Class<E> enumClass) {
        this(builderFactory, EnumSet.allOf(enumClass));
    }

    public RestartParentWriteAttributeHandler(ResourceServiceBuilderFactory<T> builderFactory, Attribute... attributes) {
        this(builderFactory, Arrays.asList(attributes));
    }

    public RestartParentWriteAttributeHandler(ResourceServiceBuilderFactory<T> builderFactory, Iterable<? extends Attribute> attributes) {
        super(null);
        this.builderFactory = builderFactory;
        for (Attribute attribute : attributes) {
            AttributeDefinition definition = attribute.getDefinition();
            this.attributes.put(definition.getName(), definition);
        }
    }

    @Override
    protected AttributeDefinition getAttributeDefinition(String name) {
        return this.attributes.get(name);
    }

    @Override
    protected void recreateParentService(OperationContext context, PathAddress parentAddress, ModelNode parentModel) throws OperationFailedException {
        this.builderFactory.createBuilder(parentAddress).configure(context, parentModel).build(context.getServiceTarget()).install();
    }

    @Override
    protected ServiceName getParentServiceName(PathAddress parentAddress) {
        return this.builderFactory.createBuilder(parentAddress).getServiceName();
    }

    @Override
    protected PathAddress getParentAddress(PathAddress address) {
        return address.getParent();
    }

    @Override
    public void register(ManagementResourceRegistration registration) {
        for (AttributeDefinition attribute : this.attributes.values()) {
            registration.registerReadWriteAttribute(attribute, null, this);
        }
    }
}
