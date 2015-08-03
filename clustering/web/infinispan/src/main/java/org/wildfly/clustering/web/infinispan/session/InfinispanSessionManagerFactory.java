/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
package org.wildfly.clustering.web.infinispan.session;

import java.util.Map;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.Configuration;
import org.infinispan.context.Flag;
import org.infinispan.remoting.transport.Address;
import org.infinispan.transaction.LockingMode;
import org.infinispan.util.concurrent.IsolationLevel;
import org.wildfly.clustering.marshalling.MarshalledValue;
import org.wildfly.clustering.marshalling.MarshalledValueFactory;
import org.wildfly.clustering.marshalling.MarshalledValueMarshaller;
import org.wildfly.clustering.marshalling.Marshaller;
import org.wildfly.clustering.marshalling.MarshallingContext;
import org.wildfly.clustering.marshalling.SimpleMarshalledValueFactory;
import org.wildfly.clustering.marshalling.SimpleMarshallingContextFactory;
import org.jboss.modules.Module;
import org.wildfly.clustering.dispatcher.CommandDispatcherFactory;
import org.wildfly.clustering.ee.Batcher;
import org.wildfly.clustering.ee.infinispan.InfinispanBatcher;
import org.wildfly.clustering.ee.infinispan.TransactionBatch;
import org.wildfly.clustering.group.NodeFactory;
import org.wildfly.clustering.web.IdentifierFactory;
import org.wildfly.clustering.web.LocalContextFactory;
import org.wildfly.clustering.web.infinispan.AffinityIdentifierFactory;
import org.wildfly.clustering.web.infinispan.session.coarse.CoarseSessionCacheEntry;
import org.wildfly.clustering.web.infinispan.session.coarse.CoarseSessionFactory;
import org.wildfly.clustering.web.infinispan.session.coarse.SessionAttributesCacheKey;
import org.wildfly.clustering.web.infinispan.session.fine.FineSessionCacheEntry;
import org.wildfly.clustering.web.infinispan.session.fine.FineSessionFactory;
import org.wildfly.clustering.web.infinispan.session.fine.SessionAttributeCacheKey;
import org.wildfly.clustering.web.session.SessionContext;
import org.wildfly.clustering.web.session.SessionManager;
import org.wildfly.clustering.web.session.SessionManagerConfiguration;
import org.wildfly.clustering.web.session.SessionManagerFactory;

/**
 * Factory for creating session managers.
 * @author Paul Ferraro
 */
public class InfinispanSessionManagerFactory implements SessionManagerFactory<TransactionBatch> {

    private final InfinispanSessionManagerFactoryConfiguration config;

    public InfinispanSessionManagerFactory(InfinispanSessionManagerFactoryConfiguration config) {
        this.config = config;
    }

    @Override
    public <L> SessionManager<L, TransactionBatch> createSessionManager(final SessionContext context, IdentifierFactory<String> identifierFactory, LocalContextFactory<L> localContextFactory) {
        final Batcher<TransactionBatch> batcher = new InfinispanBatcher(this.config.getCache());
        final Cache<String, ?> cache = this.config.getCache();
        final IdentifierFactory<String> factory = new AffinityIdentifierFactory<>(identifierFactory, cache, this.config.getKeyAffinityServiceFactory());
        final CommandDispatcherFactory dispatcherFactory = this.config.getCommandDispatcherFactory();
        final NodeFactory<Address> nodeFactory = this.config.getNodeFactory();
        final int maxActiveSessions = this.config.getSessionManagerConfiguration().getMaxActiveSessions();
        InfinispanSessionManagerConfiguration config = new InfinispanSessionManagerConfiguration() {
            @Override
            public SessionContext getSessionContext() {
                return context;
            }

            @Override
            public Cache<String, ?> getCache() {
                return cache;
            }

            @Override
            public IdentifierFactory<String> getIdentifierFactory() {
                return factory;
            }

            @Override
            public Batcher<TransactionBatch> getBatcher() {
                return batcher;
            }

            @Override
            public CommandDispatcherFactory getCommandDispatcherFactory() {
                return dispatcherFactory;
            }

            @Override
            public NodeFactory<Address> getNodeFactory() {
                return nodeFactory;
            }

            @Override
            public int getMaxActiveSessions() {
                return maxActiveSessions;
            }
        };
        return new InfinispanSessionManager<>(this.getSessionFactory(context, localContextFactory), config);
    }

    private <L> SessionFactory<?, L> getSessionFactory(SessionContext context, LocalContextFactory<L> localContextFactory) {
        SessionManagerConfiguration config = this.config.getSessionManagerConfiguration();
        Module module = config.getModule();
        MarshallingContext marshallingContext = new SimpleMarshallingContextFactory().createMarshallingContext(new SessionAttributeMarshallingContext(module), module.getClassLoader());
        MarshalledValueFactory<MarshallingContext> factory = new SimpleMarshalledValueFactory(marshallingContext);

        switch (config.getAttributePersistenceStrategy()) {
            case FINE: {
                Cache<String, FineSessionCacheEntry<L>> sessionCache = this.getSessionCache();
                Cache<SessionAttributeCacheKey, MarshalledValue<Object, MarshallingContext>> attributeCache = this.config.getCache();
                Marshaller<Object, MarshalledValue<Object, MarshallingContext>> marshaller = new MarshalledValueMarshaller<>(factory, marshallingContext);
                return new FineSessionFactory<>(sessionCache, attributeCache, context, marshaller, localContextFactory);
            }
            case COARSE: {
                Cache<String, CoarseSessionCacheEntry<L>> sessionCache = this.getSessionCache();
                Cache<SessionAttributesCacheKey, MarshalledValue<Map<String, Object>, MarshallingContext>> attributesCache = this.config.getCache();
                Marshaller<Map<String, Object>, MarshalledValue<Map<String, Object>, MarshallingContext>> marshaller = new MarshalledValueMarshaller<>(factory, marshallingContext);
                return new CoarseSessionFactory<>(sessionCache, attributesCache, context, marshaller, localContextFactory);
            }
            default: {
                // Impossible
                throw new IllegalStateException();
            }
        }
    }

    private <V> Cache<String, V> getSessionCache() {
        Cache<String, V> cache = this.config.getCache();
        Configuration cacheConfig = cache.getCacheConfiguration();
        boolean lockOnRead = cacheConfig.transaction().transactionMode().isTransactional() && (cacheConfig.transaction().lockingMode() == LockingMode.PESSIMISTIC) && cacheConfig.locking().isolationLevel() == IsolationLevel.REPEATABLE_READ;
        return lockOnRead ? cache.getAdvancedCache().withFlags(Flag.FORCE_WRITE_LOCK) : cache;
    }
}
