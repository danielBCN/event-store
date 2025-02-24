package org.infinispan.creson.test;

import org.infinispan.Cache;
import org.infinispan.commons.api.BasicCacheContainer;
import org.infinispan.creson.Factory;
import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.test.fwk.TransportFlags;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.infinispan.creson.Factory.CRESON_CACHE_NAME;
import static org.infinispan.creson.utils.ConfigurationHelper.installCreson;

/**
 * @author Pierre Sutra
 */

@Test(testName = "BaseTest")
public abstract class BaseTest extends AbstractTest{

    private static List<Cache<Object, Object>> caches = new ArrayList<>();
    private static List<BasicCacheContainer> containers = new ArrayList<>();

    @Override
    public BasicCacheContainer container(int i) {
        return containers.get(i);
    }

    @Override
    public Collection<BasicCacheContainer> containers() {
        return containers;
    }

    @Override
    public synchronized boolean addContainer() {
        TransportFlags flags = new TransportFlags();
        flags.withFD(true).withMerge(true);
        EmbeddedCacheManager cm = addClusterEnabledCacheManager(flags);
        installCreson(
                cm,
                CACHE_MODE,
                REPLICATION_FACTOR, MAX_ENTRIES,
                PASSIVATION,
                PERSISTENT_STORAGE_DIR + "/" + containers().size(),
                true,
                false,
                true);
        waitForClusterToForm(CRESON_CACHE_NAME);
        Cache cache = cm.getCache(CRESON_CACHE_NAME);
        caches.add(cache);
        Factory.forCache(cache);
        System.out.println("Node " + cm + " added.");
        return true;
    }

    @Override
    public synchronized boolean deleteContainer() {
        assert caches.size() == containers().size();
        if (caches.size() == 0)
            return false;
        BasicCacheContainer container = container(containers().size() - 1);
        container.stop();
        containers().remove(container);
        caches.remove(caches.size() - 1);
        waitForClusterToForm();
        System.out.println("Node " + container + " deleted.");
        return true;
    }

}
