package fish.payara.monitoring.adapt;

import java.io.Serializable;
import java.util.ServiceLoader;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * A monitored application provides its implementation of low level processing required to run the
 * {@link MonitoringConsole} when creating it using the {@link MonitoringConsoleFactory}. 
 *
 * The provided implementation is used by the monitoring implementation to run its low level work of collecting metrics
 * and connecting instances to a network of multiple data senders and a central data receiver.
 * 
 * @author Jan Bernitt
 * @since 1.0
 */
public interface MonitoringConsoleRuntime {

    /**
     * Asynchronously runs the task as done by
     * {@link ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)}.
     * 
     * @see ScheduledExecutorService#scheduleAtFixedRate(Runnable, long, long, TimeUnit)
     */
    ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit);

    /**
     * Sends a "package" of monitoring data from a sender (secondary instance) to the receiver (primary instance).
     * 
     * @param type     Java type of the package
     * @param snapshot the package data
     * @return true, if the message has been send, else false
     */
    <T extends Serializable> boolean send(Class<T> type, T snapshot);

    /**
     * Registers the receiver {@link Consumer} for messages of the given type when those are received.
     * 
     * @param type     Java type of the expected messages
     * @param receiver the callback to call with the received message when a message is received by the underlying
     *                 implementation.
     * @return true, if the callback was installed successful, else false
     */
    <T extends Serializable> boolean receive(Class<T> type, Consumer<T> receiver);

    /**
     * Lists the available {@link ClassLoader} contexts to use to look for {@link Monitored} and {@link Watched}
     * implementations using the {@link ServiceLoader} mechanism.
     * 
     * Naturally this list can change in-between two invocations of the method.
     * 
     * @return a list of available {@link ClassLoader} contexts. In case of an application server this includes the
     *         server main context as well as application specific contexts.
     */
    Iterable<ClassLoader> contexts();

    MonitoringConsoleWatchConfig getWatchConfig();

    GroupDataRepository getGroupData();
}
