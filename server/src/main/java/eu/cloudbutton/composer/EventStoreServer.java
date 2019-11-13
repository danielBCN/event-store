package eu.cloudbutton.composer;

import org.infinispan.configuration.cache.CacheMode;
import org.infinispan.creson.Factory;
import org.infinispan.creson.utils.ConfigurationHelper;
import org.infinispan.manager.EmbeddedCacheManager;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import sun.misc.Signal;
import sun.misc.SignalHandler;

import static org.infinispan.creson.Factory.CRESON_CACHE_NAME;
import static org.infinispan.creson.utils.ConfigurationHelper.installCreson;

/**
 * Main server executable.
 */
public class EventStoreServer {
    private static final String DEFAULT_SERVER = "localhost:50051";     // gRPC server now
    private static final String STORAGE_PATH_PREFIX = "./tmp";

    @Option(name = "-server", usage = "ip:port or ip of the server")
    private String server = DEFAULT_SERVER;

    @Option(name = "-rf", usage = "replication factor")
    private int replicationFactor = 1;

    @Option(name = "-me", usage = "max #entries in the object cache (implies -p)")
    private long maxEntries = -1;

    public static void main(String[] args) {
        new EventStoreServer().doMain(args);
    }

    private void doMain(String[] args) {
        CmdLineParser parser = new CmdLineParser(this);
        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            parser.printUsage(System.err);
            System.err.println();
            return;
        }

        String host = server.split(":")[0];
        int port = Integer.parseInt(server.split(":").length == 2 ?
                server.split(":")[1] : "50051");

        // RUN CRESON CLUSTER
        ConfigurationHelper.setUpManager(host);
        EmbeddedCacheManager cm = ConfigurationHelper.getCacheManager();

        installCreson(cm,
                CacheMode.DIST_ASYNC,
                replicationFactor,
                maxEntries,
                false,
                STORAGE_PATH_PREFIX + "/" + host,
                true,
                false,
                false);

        SignalHandler sh = s -> {
            System.out.println("CLOSING");
            try {
                Factory factory = Factory.forCache(cm.getCache(CRESON_CACHE_NAME));
                if (factory != null)
                    factory.close();
                cm.stop();
                System.exit(0);
            } catch (Throwable t) {
                System.exit(-1);
            }
        };
        Signal.handle(new Signal("INT"), sh);
        Signal.handle(new Signal("TERM"), sh);

        // RUN EVENT STORE SERVER
        StoreService server = null;
        try {
            server = new StoreService(port);
            server.start();
        } catch (Exception e) {
            System.out.println("Could not start the gRPC server:");
            e.printStackTrace();
            System.exit(1);
        }

        System.out.println("LAUNCHED");

        try {
            server.blockUntilShutdown();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
