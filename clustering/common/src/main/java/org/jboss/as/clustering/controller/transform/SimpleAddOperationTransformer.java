/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.clustering.controller.transform;

import org.jboss.as.clustering.controller.Operations;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.operations.common.Util;
import org.jboss.as.controller.transform.OperationResultTransformer;
import org.jboss.as.controller.transform.TransformationContext;
import org.jboss.dmr.ModelNode;

/**
 * Simple operation transformer for add operations.
 * @author Paul Ferraro
 */
public class SimpleAddOperationTransformer implements org.jboss.as.controller.transform.OperationTransformer {

    private final OperationTransformer transformer;
    private final AttributeDefinition[] attributes;

    public SimpleAddOperationTransformer(final PathAddressTransformer transformer, AttributeDefinition... attributes) {
        this.transformer = new OperationTransformer() {
            @Override
            public ModelNode transformOperation(ModelNode operation) {
                return Util.createAddOperation(transformer.transform(Operations.getPathAddress(operation)));
            }
        };
        this.attributes = attributes;
    }

    public SimpleAddOperationTransformer(OperationTransformer transformer, AttributeDefinition... attributes) {
        this.transformer = transformer;
        this.attributes = attributes;
    }

    @Override
    public TransformedOperation transformOperation(TransformationContext context, PathAddress address, ModelNode operation) {
        ModelNode legacyOperation = this.transformer.transformOperation(operation);
        for (AttributeDefinition attribute: this.attributes) {
            String name = attribute.getName();
            if (operation.hasDefined(name)) {
                legacyOperation.get(name).set(operation.get(name));
            }
        }
        return new TransformedOperation(legacyOperation, OperationResultTransformer.ORIGINAL_RESULT);
    }
}
