package org.infinispan.creson.container;

import javassist.util.proxy.MethodFilter;
import org.infinispan.commons.logging.Log;
import org.infinispan.commons.logging.LogFactory;
import org.infinispan.creson.object.Call;
import org.infinispan.creson.object.CallResponse;
import org.infinispan.creson.object.Reference;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.infinispan.creson.utils.Reflection.hasReadOnlyMethods;

/**
 * Uninstalling a listener is not necessary, as when all clients disconnect,
 * it is automatically removed.
 *
 * @author Pierre Sutra
 */
public abstract class AbstractContainer {

    private static final int TIMEOUT_TIME = 1000;
    private static final int MAX_ATTEMPTS = 3;
    private static final Map<UUID, CompletableFuture<CallResponse>> registeredCalls = new ConcurrentHashMap<>();
    static final Log log = LogFactory.getLog(AbstractContainer.class);
    static final MethodFilter methodFilter = m -> !m.getName().equals("finalize");

    boolean readOptimization;
    boolean isIdempotent;
    Object proxy;
    Object state;
    boolean forceNew;
    Object[] initArgs;

    AbstractContainer(
            Class clazz,
            final boolean readOptimization,
            final boolean isIdempotent,
            final boolean forceNew,
            final Object... initArgs) {
        this.readOptimization = readOptimization && hasReadOnlyMethods(clazz);
        this.isIdempotent = isIdempotent;
        this.forceNew = forceNew;
        this.initArgs = initArgs;
    }

    public final Object getProxy() {
        return proxy;
    }

    public abstract Reference getReference();

    public abstract void doExecute(Call call);

    Object execute(Call call)
            throws Throwable {

        if (log.isTraceEnabled())
            log.trace(this + " Executing " + call);

        CompletableFuture<CallResponse> future = new CompletableFuture<>();

        registeredCalls.put(call.getCallID(), future);

        CallResponse response = null;
        Object ret = null;
        int attempts = 0;
        while (!future.isDone()) {
            try {
                attempts++;
                doExecute(call);
                response = future.get(TIMEOUT_TIME, TimeUnit.MILLISECONDS);
                ret = response.getResult();
//            if (ret instanceof Throwable)
//               throw new ExecutionException((Throwable) ret);
            } catch (TimeoutException e) {
                if (!future.isDone())
                    log.warn(" Failed " + call + " (" + e.getMessage() + ")");
                if (attempts == MAX_ATTEMPTS) {
                    registeredCalls.remove(call.getCallID());
                    throw new TimeoutException(call + " failed");
                }
                Thread.sleep(TIMEOUT_TIME);
            }
            if (ret instanceof Throwable)
                throw (Throwable) ret;
        }

        registeredCalls.remove(call.getCallID());

        if (readOptimization && response != null && response.getState() != null) {
            this.state = response.getState();
        }

        if (log.isTraceEnabled())
            log.trace(this + " Returning " + ret);

        return ret;
    }

    static void handleFuture(CallResponse response) {
        try {
            if (!registeredCalls.containsKey(response.getCallID())) {
                log.trace("Future " + response.getCallID() + " ignored");
                return; // duplicate received
            }

            CompletableFuture<CallResponse> future = registeredCalls.get(response.getCallID());
            assert (future != null);

            registeredCalls.remove(response.getCallID());

            future.complete(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
