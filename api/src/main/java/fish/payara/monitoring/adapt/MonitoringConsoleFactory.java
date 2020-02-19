package fish.payara.monitoring.adapt;

import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.function.Supplier;

import fish.payara.monitoring.collect.MonitoringDataSource;
import fish.payara.monitoring.collect.MonitoringWatchSource;

/**
 * The monitoring console library provides an implementations for this interface that is accessed using the
 * {@link ServiceLoader} mechanism, {@link #getInstance()} can be used for convenience. 
 * 
 * This allows a monitored application to be in control of creating the {@link MonitoringConsole} using the resolved
 * factory instance. This is usually needed so that the monitored application can bootstrap the
 * {@link MonitoringConsole} at the right point in its bootstrapping process.
 * 
 * @author Jan Bernitt
 * @since 1.0 (Payara 5.201)
 */
public interface MonitoringConsoleFactory {

    /**
     * Creates a new instance of a {@link MonitoringConsole} processing unit.
     * 
     * @param instance the name of the instance the console represent, the instance it collects data from
     * @param receiver true, if the created instance is the single receiver of the cluster (the central instance), false
     *                 if it is one of the senders of the cluster.
     * @param runtime  the runtime to use by the created instance, cannot be {@code null}
     * @return A new logical instance of the {@link MonitoringConsole}
     */
    MonitoringConsole create(String instance, boolean receiver, MonitoringConsoleRuntime runtime,
            Supplier<? extends List<MonitoringDataSource>> dataSources,
            Supplier<? extends List<MonitoringWatchSource>> watchSources);

    /**
     * @return The {@link MonitoringConsole} instance previously created using
     *         {@link #create(String, boolean, MonitoringConsoleRuntime)}
     * @throws IllegalStateException if this method is called before
     *                               {@link #create(String, boolean, MonitoringConsoleRuntime)} was
     */
    MonitoringConsole getCreatedConsole() throws IllegalStateException;

    /**
     * @return A {@link MonitoringConsoleFactory} instance or {@code null} if none is available on classpath.
     */
    static MonitoringConsoleFactory getInstance() {
        Iterator<MonitoringConsoleFactory> iter = ServiceLoader.load(MonitoringConsoleFactory.class).iterator();
        return iter.hasNext() ? iter.next() : null;
    }
}
