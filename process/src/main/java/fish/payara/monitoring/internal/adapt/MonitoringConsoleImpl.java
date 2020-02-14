package fish.payara.monitoring.internal.adapt;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import fish.payara.monitoring.adapt.GroupDataRepository;
import fish.payara.monitoring.adapt.MonitoringConsole;
import fish.payara.monitoring.adapt.MonitoringConsoleRuntime;
import fish.payara.monitoring.alert.AlertService;
import fish.payara.monitoring.collect.MonitoringDataSource;
import fish.payara.monitoring.collect.MonitoringWatchSource;
import fish.payara.monitoring.data.SeriesRepository;
import fish.payara.monitoring.internal.alert.InMemoryAlarmService;
import fish.payara.monitoring.internal.data.InMemorySeriesRepository;
import fish.payara.monitoring.model.SeriesLookup;

public class MonitoringConsoleImpl implements MonitoringConsole {

    private final MonitoringConsoleRuntime runtime;
    private final InMemorySeriesRepository data;
    private final InMemoryAlarmService alerts;

    MonitoringConsoleImpl(String instance, boolean receiver, MonitoringConsoleRuntime runtime, 
            Supplier<? extends List<MonitoringDataSource>> dataSources,
            Supplier<? extends List<MonitoringWatchSource>> watchSources) {
        this.runtime = runtime;
        data = new InMemorySeriesRepository(instance, receiver, runtime, dataSources);
        alerts = new InMemoryAlarmService(receiver, runtime, watchSources, data);
    }

    @Override
    public void setEnabled(boolean enabled) {
        data.setEnabled(enabled);
        alerts.setEnabled(enabled);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getService(Class<T> type) throws NoSuchElementException {
        if (type == SeriesLookup.class || type == SeriesRepository.class) {
            return (T) data;
        }
        if (type == AlertService.class) {
            return (T) alerts;
        }
        if (type == GroupDataRepository.class) {
            return (T) runtime.getGroupData();
        }
        throw new NoSuchElementException("Unknown service: " + type.getName());
    }
}
