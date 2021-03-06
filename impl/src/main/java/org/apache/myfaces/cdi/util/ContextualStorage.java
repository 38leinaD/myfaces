/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.myfaces.cdi.util;


import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.PassivationCapable;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * This Storage holds all information needed for storing Contextual Instances in a Context.
 *
 * It also addresses Serialisation in case of passivating scopes.
 */
public class ContextualStorage implements Serializable
{
    private static final long serialVersionUID = 1L;

    protected final Map<Object, ContextualInstanceInfo<?>> contextualInstances;
    protected final BeanManager beanManager;
    protected final boolean concurrent;

    /**
     * @param beanManager is needed for serialisation
     * @param concurrent whether the ContextualStorage might get accessed concurrently by different threads
     */
    public ContextualStorage(BeanManager beanManager, boolean concurrent)
    {
        this.beanManager = beanManager;
        this.concurrent = concurrent;
        if (concurrent)
        {
            contextualInstances = new ConcurrentHashMap<>();
        }
        else
        {
            contextualInstances = new HashMap<>();
        }
    }

    /**
     * @return the underlying storage map.
     */
    public Map<Object, ContextualInstanceInfo<?>> getStorage()
    {
        return contextualInstances;
    }

    /**
     * @return whether the ContextualStorage might get accessed concurrently by different threads.
     */
    public boolean isConcurrent()
    {
        return concurrent;
    }

    /**
     *
     * @param bean
     * @param creationalContext
     * @param <T>
     * @return
     */
    public <T> T createContextualInstance(Contextual<T> bean, CreationalContext<T> creationalContext)
    {
        Object beanKey = getBeanKey(bean);
        if (isConcurrent())
        {
            // locked approach
            ContextualInstanceInfo<T> instanceInfo = new ContextualInstanceInfo<>();

            ConcurrentMap<Object, ContextualInstanceInfo<?>> concurrentMap
                = (ConcurrentHashMap<Object, ContextualInstanceInfo<?>>) contextualInstances;

            ContextualInstanceInfo<T> oldInstanceInfo
                = (ContextualInstanceInfo<T>) concurrentMap.putIfAbsent(beanKey, instanceInfo);

            if (oldInstanceInfo != null)
            {
                instanceInfo = oldInstanceInfo;
            }

            synchronized (instanceInfo)
            {
                T instance = instanceInfo.getContextualInstance();
                if (instance == null)
                {
                    instance = bean.create(creationalContext);
                    instanceInfo.setContextualInstance(instance);
                    instanceInfo.setCreationalContext(creationalContext);
                }

                return instance;
            }

        }
        else
        {
            // simply create the contextual instance
            ContextualInstanceInfo<T> instanceInfo = new ContextualInstanceInfo<T>();
            instanceInfo.setCreationalContext(creationalContext);
            instanceInfo.setContextualInstance(bean.create(creationalContext));

            contextualInstances.put(beanKey, instanceInfo);

            return instanceInfo.getContextualInstance();
        }
    }

    /**
     * If the context is a passivating scope then we return the passivationId of the Bean.
     * Otherwise we use the Bean directly.
     *
     * @param bean
     * 
     * @return the key to use in the context map
     */
    public <T> Object getBeanKey(Contextual<T> bean)
    {
        if (bean instanceof PassivationCapable)
        {
            // if the
            return ((PassivationCapable) bean).getId();
        }

        return bean;
    }

    /**
     * Restores the Bean from its beanKey.
     * @see #getBeanKey(javax.enterprise.context.spi.Contextual)
     */
    public Contextual<?> getBean(Object beanKey)
    {
        if (beanKey instanceof String)
        {
            // If the beanKey is a String, it was generated by PassivationCapable#getId().
            return beanManager.getPassivationCapableBean((String) beanKey);
        }

        return (Contextual<?>) beanKey;
    }

}
