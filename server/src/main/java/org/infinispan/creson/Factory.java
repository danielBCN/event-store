package org.infinispan.creson;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import org.infinispan.creson.container.AbstractContainer;
import org.infinispan.creson.container.BaseContainer;
import org.infinispan.creson.object.Reference;
import org.infinispan.creson.utils.ConfigurationHelper;
import org.infinispan.creson.utils.ContextManager;
import org.infinispan.creson.utils.Reflection;
import org.infinispan.commons.CacheException;
import org.infinispan.commons.api.BasicCache;
import org.infinispan.commons.logging.Log;
import org.infinispan.commons.logging.LogFactory;
import org.infinispan.manager.EmbeddedCacheManager;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Pierre Sutra
 */
public class Factory {

    // Class fields
    public static final String CRESON_CACHE_NAME = "__creson";
    private static final int DEFAULT_MAX_CONTAINERS = Integer.MAX_VALUE;

    private static Log log = LogFactory.getLog(Factory.class);
    private static Factory singleton;
    private static Map<BasicCache, Factory> factories = new HashMap<>();

    /**
     * Return a Factory built on top of cache <i>c</i>.
     *
     * @param c a cache,  key must be synchronous.and non-transactional
     */
    private Factory(BasicCache c) throws CacheException {
        this(c, DEFAULT_MAX_CONTAINERS);
    }

    /**
     * Returns an object factory built on top of cache <i>c</i> with a bounded amount <i>m</i> of
     * containers in key. Upon the removal of a container, the object is stored persistently in the cache.
     *
     * @param c key must be synchronous and non-transactional.
     * @param m max amount of containers kept by this factory.
     * @throws CacheException problem building the cache as map.
     */
    private Factory(BasicCache c, int m) throws CacheException {
        cache = c;
        registeredContainers = CacheBuilder.newBuilder()
                .maximumSize(m)
                .removalListener((RemovalListener<Reference, AbstractContainer>) notification -> {
                    try {
                        disposeInstanceOf(notification.getValue().getReference());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                })  // RemovalListener is Unstable
                .build().asMap();
        log.info(this + " Created");
    }

    public static Factory getSingleton() {
        assert singleton != null;
        return singleton;
    }

    public synchronized static Factory forCache(BasicCache cache) {
        return Factory.forCache(cache, DEFAULT_MAX_CONTAINERS, false);
    }

    public synchronized static Factory forCache(BasicCache cache, boolean force) {
        return Factory.forCache(cache, DEFAULT_MAX_CONTAINERS, force);
    }

    public synchronized static Factory forCache(BasicCache cache, int maxContainers, boolean force) {
        if (!factories.containsKey(cache))
            factories.put(cache, new Factory(cache, maxContainers));

        if ((singleton == null | force) && cache.getName().equals(CRESON_CACHE_NAME)) {
            singleton = factories.get(cache);
            log.info("AOF singleton  is " + singleton);
        }

        return factories.get(cache);
    }

    public static Factory get(long seed) {
        ContextManager.seedGenerator(seed);
        return get();
    }

    public static Factory get() {
        EmbeddedCacheManager cm = ConfigurationHelper.getCacheManager();
        return forCache(cm.getCache(CRESON_CACHE_NAME));
    }

    // Object fields
    private final ConcurrentMap<Reference, AbstractContainer> registeredContainers;
    private BasicCache cache;

    public <T> T getInstanceOf(Class<T> clazz) throws CacheException {
        return (T) getInstanceOf(clazz, null, false, false, false);
    }

    public <T> T getInstanceOf(Reference<T> reference) throws CacheException {
        return getInstanceOf(reference.getClazz(), reference.getKey(), false, false, false);
    }

    public <T> T getInstanceOf(Class<T> clazz, Object key) throws CacheException {
        return getInstanceOf(clazz, key, false, false, false);
    }

    /**
     * Returns an object of class <i>clazz</i>.
     * The class of this object must be initially serializable, as well as all
     * the parameters of its methods.
     * Furthermore, the class must be deterministic.
     * <p>
     * The object is atomic if <i>withReadOptimization</i> equals false;
     * otherwise key is sequentially consistent..
     * In more details, if <i>withReadOptimization</i> is set, every call to
     * the object is first executed locally on a copy of the object, and in case
     * the call does not modify the state of the object, the value returned is
     * the result of this tentative execution.
     *
     * @param clazz                a class object
     * @param withReadOptimization set the read optimization on/off.
     * @return an object of the class <i>clazz</i>
     * @throws CacheException
     */
    public <T> T getInstanceOf(Class<T> clazz, boolean withReadOptimization)
            throws CacheException {
        return getInstanceOf(clazz, null, withReadOptimization, false, false, false);
    }

    /**
     * Returns an object of class <i>clazz</i>.
     * The class of this object must be initially serializable, as well as all the parameters of its methods.
     * Furthermore, the class must be deterministic.
     * <p>
     * The object is atomic if <i>withReadOptimization</i> equals false; otherwise key is sequentially consistent..
     * In more details, if <i>withReadOptimization</i>  is set, every call to the object is executed locally on a copy of the object, and in case
     * the call does not modify the state of the object, the value returned is the result of this tentative execution.
     * If the method <i>equalsMethod</i>  is not null, key overrides the default <i>clazz.equals()</i> when testing that the state of the object and
     * its copy are identical.
     *
     * @param clazz                a class object
     * @param withReadOptimization set the read optimization on/off.
     * @param withIdempotence      set idempotence on/off.
     * @param forceNew             force the creation of the object, even if key exists already in the cache
     * @return an object of the class <i>clazz</i>
     * @throws CacheException
     */
    public <T> T getInstanceOf(Class<T> clazz, Object key,
                               boolean withReadOptimization, boolean withIdempotence,
                               boolean forceNew, Object... initArgs)
            throws CacheException {

        Reference reference;
        AbstractContainer container = null;

        try {

            if (key != null) {
                reference = new Reference<>(clazz, key);
                container = registeredContainers.get(reference);
            }

            if (container == null) {
                try {
                    Reflection.getConstructor(clazz, initArgs);
                } catch (Exception e) {
                    throw new CacheException(clazz + " no constructor with " + Arrays.toString(initArgs));
                }
                container = new BaseContainer(cache, clazz, key, withReadOptimization, withIdempotence, forceNew, initArgs);
                reference = container.getReference();
                if (registeredContainers.putIfAbsent(reference, container) == null) {
                    if (log.isTraceEnabled())
                        log.trace(this + " adding " + container + " with " + container.getReference());
                }
                container = registeredContainers.get(reference);
            }

        } catch (Exception e) {
            e.printStackTrace();
            throw new CacheException(e.getCause());
        }

        assert container != null;

        return (T) container.getProxy();

    }

    public <T> void disposeInstanceOf(Reference<T> reference)
            throws CacheException {
        disposeInstanceOf(reference.getClazz(), reference.getKey());
    }

    public <T> void disposeInstanceOf(Class<T> clazz, Object key)
            throws CacheException {

        Reference reference = new Reference<>(clazz, key);

        AbstractContainer container = registeredContainers.get(reference);

        if (container == null) return;

        if (log.isDebugEnabled())
            log.debug(" disposing " + container);

        registeredContainers.remove(reference);

    }

    public void close() {
        log.info("closed");
    }

    @Override
    public String toString() {
        return "Factory[" + cache.toString() + "]";
    }

    public void clear() {
        log.info("cleared");
        cache.clear();
    }

}
