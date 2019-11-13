package eu.cloudbutton.composer;

import com.google.gson.Gson;
import eu.cloudbutton.composer.grpc.EventStoreServiceGrpc;
import eu.cloudbutton.composer.grpc.Store;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.function.Predicate;
import java.util.logging.Logger;

/**
 * This class processes the remote connections.
 * Add items to the event store.
 * Define conditions.
 */
public class StoreService {
    private static final Logger logger = Logger.getLogger(StoreService.class.getName());
    private static Gson gson = new Gson();

    private Server server;
    private int port;

    public StoreService(int port) {
        this(ServerBuilder.forPort(port), port);
    }

    /** Create a RouteGuide server using serverBuilder as a base and features as data. */
    public StoreService(ServerBuilder<?> serverBuilder, int port) {
        this.port = port;
        server = serverBuilder.addService(new StoreServiceImpl())
                .build();
    }

    public void start() throws IOException {
        logger.info("Starting Store Service gRPC");
        server.start();
        logger.info("Server started, listening on " + port);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            // Use stderr here since the logger may have been reset by its JVM shutdown hook.
            System.err.println("*** shutting down gRPC server since JVM is shutting down");
            StoreService.this.stop();
            System.err.println("*** server shut down");
        }));
    }

    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }
    /**
     * Await termination on the main thread since the grpc library uses daemon threads.
     */
    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    public void add(String jsonifiedEvent) {

    }

    /**
     * Conditions can be:
     * - Condition -> Action
     * - Condition -> Actions
     * - Aggregation -> Action/s (with or without aggregated result)
     *
     * @param eventCondition A pre-defined event condition. Not user-provided.
     *                       The framework offers a set of event patterns.
     */
    public void registerCondition(Predicate<String> eventCondition) {

    }

    static class StoreServiceImpl extends EventStoreServiceGrpc.EventStoreServiceImplBase {
        @Override
        public void addEvent(Store.EventDef request, StreamObserver<Store.OK> responseObserver) {
            // TODO
            String event = request.getEvent().toString();
            System.out.println(event);


            sendOK(responseObserver);
        }

        @Override
        public void registerTrigger(Store.TriggerDef request, StreamObserver<Store.OK> responseObserver) {
            // TODO
            sendOK(responseObserver);
        }

        private void sendOK(StreamObserver<Store.OK> responseObserver) {
            Store.OK ok = Store.OK.newBuilder().setResult(true).build();
            responseObserver.onNext(ok);
            responseObserver.onCompleted();
        }
    }
}
