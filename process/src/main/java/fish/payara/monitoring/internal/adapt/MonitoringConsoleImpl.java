/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2020 Payara Foundation and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://github.com/payara/Payara/blob/master/LICENSE.txt
 * See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * The Payara Foundation designates this particular file as subject to the "Classpath"
 * exception as provided by the Payara Foundation in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package fish.payara.monitoring.internal.adapt;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Supplier;

import fish.payara.monitoring.adapt.GroupDataRepository;
import fish.payara.monitoring.adapt.MonitoringConsole;
import fish.payara.monitoring.adapt.MonitoringConsolePageConfig;
import fish.payara.monitoring.adapt.MonitoringConsoleRuntime;
import fish.payara.monitoring.alert.AlertService;
import fish.payara.monitoring.alert.AlertService.AlertStatistics;
import fish.payara.monitoring.collect.MonitoringData;
import fish.payara.monitoring.collect.MonitoringDataCollector;
import fish.payara.monitoring.collect.MonitoringDataSource;
import fish.payara.monitoring.collect.MonitoringWatchSource;
import fish.payara.monitoring.data.SeriesRepository;
import fish.payara.monitoring.internal.alert.InMemoryAlarmService;
import fish.payara.monitoring.internal.data.InMemorySeriesRepository;
import fish.payara.monitoring.model.SeriesLookup;

/**
 * In memory default implementation of the {@link MonitoringConsole} abstraction that uses the
 * {@link InMemorySeriesRepository} and the {@link InMemoryAlarmService}.
 *
 * @author Jan Bernitt
 * @since 1.0 (Payara 5.201)
 */
public class MonitoringConsoleImpl implements MonitoringConsole, MonitoringDataSource {

    private static final String ALERT_COUNT = "AlertCount";

    private final boolean receiver;
    private final MonitoringConsoleRuntime runtime;
    private final InMemorySeriesRepository data;
    private final InMemoryAlarmService alerts;

    MonitoringConsoleImpl(String instance, boolean receiver, MonitoringConsoleRuntime runtime,
            Supplier<? extends List<MonitoringDataSource>> dataSources,
            Supplier<? extends List<MonitoringWatchSource>> watchSources) {
        this.receiver = receiver;
        this.runtime = runtime;
        Supplier<? extends List<MonitoringDataSource>> extendedDataSource = () -> {
            List<MonitoringDataSource> extended = new ArrayList<>(dataSources.get());
            extended.add(MonitoringConsoleImpl.this);
            return extended;
        };
        data = new InMemorySeriesRepository(instance, receiver, runtime, extendedDataSource);
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
        if (type == MonitoringConsolePageConfig.class) {
            return (T) runtime.getPageConfig();
        }
        throw new NoSuchElementException("Unknown service: " + type.getName());
    }

    @Override
    @MonitoringData(ns = "monitoring")
    public void collect(MonitoringDataCollector collector) {
        if (receiver) {
            collector.collect("WatchLoopDuration", alerts.getEvaluationLoopTime());
            AlertStatistics stats = alerts.getAlertStatistics();
            if (stats != null) {
                collector.group("Red").collect(ALERT_COUNT, stats.unacknowledgedRedAlerts);
                collector.group("RedAck").collect(ALERT_COUNT, stats.acknowledgedRedAlerts);
                collector.group("Amber").collect(ALERT_COUNT, stats.unacknowledgedAmberAlerts);
                collector.group("AmberAck").collect(ALERT_COUNT, stats.acknowledgedAmberAlerts);
            }
        }
    }
}
