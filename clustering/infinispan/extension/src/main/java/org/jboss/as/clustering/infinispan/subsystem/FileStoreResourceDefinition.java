/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
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

package org.jboss.as.clustering.infinispan.subsystem;

import org.jboss.as.clustering.controller.AddStepHandler;
import org.jboss.as.clustering.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.clustering.controller.RemoveStepHandler;
import org.jboss.as.clustering.controller.ResourceServiceHandler;
import org.jboss.as.clustering.controller.SimpleAliasEntry;
import org.jboss.as.clustering.controller.SimpleResourceServiceHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ModelVersion;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.ResolvePathHandler;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.server.ServerEnvironment;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Resource description for the addressable resource /subsystem=infinispan/cache-container=X/cache=Y/store=STORE
 *
 * @author Richard Achmatowicz (c) 2011 Red Hat Inc.
 */
public class FileStoreResourceDefinition extends StoreResourceDefinition {

    static final PathElement LEGACY_PATH = PathElement.pathElement("file-store", "FILE_STORE");
    static final PathElement PATH = pathElement("file");

    enum Attribute implements org.jboss.as.clustering.controller.Attribute {
        RELATIVE_PATH("path", ModelType.STRING, null),
        RELATIVE_TO("relative-to", ModelType.STRING, new ModelNode(ServerEnvironment.SERVER_DATA_DIR)),
        ;
        private final AttributeDefinition definition;

        Attribute(String name, ModelType type, ModelNode defaultValue) {
            this.definition = new SimpleAttributeDefinitionBuilder(name, type)
                    .setAllowExpression(true)
                    .setAllowNull(true)
                    .setDefaultValue(defaultValue)
                    .setFlags(AttributeAccess.Flag.RESTART_RESOURCE_SERVICES)
                    .build();
        }

        @Override
        public AttributeDefinition getDefinition() {
            return this.definition;
        }
    }

    static void buildTransformation(ModelVersion version, ResourceTransformationDescriptionBuilder parent) {
        ResourceTransformationDescriptionBuilder builder = InfinispanModel.VERSION_4_0_0.requiresTransformation(version) ? parent.addChildRedirection(PATH, LEGACY_PATH) : parent.addChildResource(PATH);

        StoreResourceDefinition.buildTransformation(version, builder);
    }

    private final PathManager pathManager;

    FileStoreResourceDefinition(PathManager pathManager, boolean allowRuntimeOnlyRegistration) {
        super(PATH, new InfinispanResourceDescriptionResolver(PATH, WILDCARD_PATH), allowRuntimeOnlyRegistration);
        this.pathManager = pathManager;
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration registration) {
        super.registerAttributes(registration);
        new ReloadRequiredWriteAttributeHandler(Attribute.class).register(registration);
    }

    @Override
    public void registerOperations(ManagementResourceRegistration registration) {
        ResourceServiceHandler handler = new SimpleResourceServiceHandler<>(new FileStoreBuilderFactory());
        new AddStepHandler(this.getResourceDescriptionResolver(), handler).addAttributes(Attribute.class).addAttributes(StoreResourceDefinition.Attribute.class).register(registration);
        new RemoveStepHandler(this.getResourceDescriptionResolver(), handler).register(registration);

        if (this.pathManager != null) {
            ResolvePathHandler pathHandler = ResolvePathHandler.Builder.of(this.pathManager)
                    .setPathAttribute(Attribute.RELATIVE_PATH.getDefinition())
                    .setRelativeToAttribute(Attribute.RELATIVE_TO.getDefinition())
                    .build();
            registration.registerOperationHandler(pathHandler.getOperationDefinition(), pathHandler);
        }
    }

    @Override
    public void register(ManagementResourceRegistration registration) {
        registration.registerAlias(LEGACY_PATH, new SimpleAliasEntry(registration.registerSubModel(this)));
    }
}
