package fish.payara.monitoring.internal.adapt;

import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import fish.payara.monitoring.adapt.MonitoringConsole;
import fish.payara.monitoring.adapt.MonitoringConsoleFactory;
import fish.payara.monitoring.adapt.MonitoringConsoleRuntime;
import fish.payara.monitoring.collect.MonitoringDataSource;
import fish.payara.monitoring.collect.MonitoringWatchSource;

public class MonitoringConsoleFactoryImpl implements MonitoringConsoleFactory {

    /**
     * This has to be a static field since each {@link ClassLoader} or each {@link ServiceLoader} invocation does create
     * its own instance of the class yet this class is meant to give access to the instance created previously.
     */
    private final static AtomicReference<MonitoringConsole> console = new AtomicReference<>();

    @Override
    public MonitoringConsole create(String instance, boolean receiver, MonitoringConsoleRuntime runtime,
            Supplier<? extends List<MonitoringDataSource>> dataSources,
            Supplier<? extends List<MonitoringWatchSource>> watchSources) {
        return console.updateAndGet(value -> value != null 
                ? value 
                : new MonitoringConsoleImpl(instance, receiver, runtime, dataSources, watchSources));
    }

    @Override
    public MonitoringConsole getCreatedConsole() {
        MonitoringConsole res = console.get();
        if (res == null) {
            throw new IllegalStateException("Console has not yet been created.");
        }
        return res;
    }

}
