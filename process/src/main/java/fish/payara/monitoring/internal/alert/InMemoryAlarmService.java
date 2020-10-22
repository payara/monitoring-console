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
package fish.payara.monitoring.internal.alert;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableCollection;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.logging.Level.FINE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Logger;

import fish.payara.monitoring.adapt.MonitoringConsoleRuntime;
import fish.payara.monitoring.adapt.MonitoringConsoleWatchConfig;
import fish.payara.monitoring.alert.Alert;
import fish.payara.monitoring.alert.AlertService;
import fish.payara.monitoring.alert.Watch;
import fish.payara.monitoring.alert.Alert.Level;
import fish.payara.monitoring.collect.MonitoringWatchCollector;
import fish.payara.monitoring.collect.MonitoringWatchSource;
import fish.payara.monitoring.data.SeriesRepository;
import fish.payara.monitoring.internal.util.JobHandle;
import fish.payara.monitoring.model.Metric;
import fish.payara.monitoring.model.Series;
import fish.payara.monitoring.model.Unit;

public class InMemoryAlarmService implements AlertService {

    private static final Logger LOGGER = Logger.getLogger("monitoring-console-core");

    private static final int MAX_ALERTS_PER_SERIES = 10;

    private final SeriesRepository monitoringData;
    private final String instance;
    private final boolean isDAS;
    private final JobHandle checker = new JobHandle("watch checker");
    private final Map<String, Watch> watchesByName = new ConcurrentHashMap<>();
    private final Map<Series, Map<String, Watch>> simpleWatches = new ConcurrentHashMap<>();
    private final Map<Series, Map<String, Watch>> patternWatches = new ConcurrentHashMap<>();
    private final Map<Series, Deque<Alert>> alerts = new ConcurrentHashMap<>();
    private final AtomicReference<AlertStatistics> statistics = new AtomicReference<>(new AlertStatistics());
    private final AtomicLong evalLoopTime = new AtomicLong();
    /**
     * Watches that are added during collection for each instance
     */
    private final Map<String, Map<String, Watch>> collectedWatchesByInstance = new ConcurrentHashMap<>();

    private final Supplier<? extends List<MonitoringWatchSource>> sources;
    private final MonitoringConsoleRuntime runtime;
    private final MonitoringConsoleWatchConfig watchConfig;

    public InMemoryAlarmService(String instance, boolean isDAS, MonitoringConsoleRuntime runtime,
            Supplier<? extends List<MonitoringWatchSource>> sources, SeriesRepository monitoringData) {
        this.instance = instance;
        this.isDAS = isDAS;
        this.runtime = runtime;
        this.sources = sources;
        this.watchConfig = runtime.getWatchConfig();
        this.monitoringData = monitoringData;
        if (isDAS) {
            addWatch(new Watch("Metric Collection Duration", new Metric(new Series("ns:monitoring CollectionDuration"), Unit.MILLIS))
                    .programmatic()
                    .red(800L, 2, true, 800L, 3, false)
                    .amber(600L, 2, true, 600L, 3, false)
                    .green(-400L, 1, false, null, null, false));
            addWatch(new Watch("Watch Loop Duration", new Metric(new Series("ns:monitoring WatchLoopDuration"), Unit.MILLIS))
                    .programmatic()
                    .red(800L, 2, true, 800L, 3, false)
                    .amber(600L, 3, true, 600L, 3, false)
                    .green(-400L, 1, false, null, null, false));
            addWatchesFromConfiguration();
        }
    }

    private void addWatchesFromConfiguration() {
        if (watchConfig != null) {
            for (String watch : watchConfig.list()) {
                addWatch(Watch.fromJSON(watch));
            }
        }
    }

    public void addRemoteWatches(WatchesSnapshot msg) {
        if (!isDAS) {
            return; // should not have gotten the message but we prevent any further errors by ignoring it
        }
        Map<String, Watch> collectedBefore = collectedWatchesByInstance.computeIfAbsent(msg.instance,
                key -> new ConcurrentHashMap<>());
        Set<String> notYetCollectedWatches = new HashSet<>(collectedBefore.keySet());
        for (Watch w : msg.watches) {
            Watch watch = Watch.fromRemote(w);
            Watch existing = collectedBefore.get(watch.name);
            if (existing == null || !watch.equalsFunctionally(existing)) {
                addWatch(watch);
                collectedBefore.put(watch.name, watch);
            }
            notYetCollectedWatches.remove(watch.name);
        }
        for (String name : notYetCollectedWatches) {
            Watch removed = collectedBefore.remove(name);
            if (removed != null && !isCollectedByAnyInstance(name)) {
                removeWatch(removed);
            }
        }
    }

    private boolean isCollectedByAnyInstance(String name) {
        for (Map<String, Watch> collectedByInstance : collectedWatchesByInstance.values()) {
            if (collectedByInstance.containsKey(name)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean toggleWatch(String name, boolean disabled) {
        Watch watch = watchByName(name);
        if (watch == null) {
            return false;
        }
        if (disabled) {
            watch.disable();
            watchConfig.disable(watch.name);
        } else {
            watch.enable();
            watchConfig.enable(watch.name);
        }
        return true;
    }

    public void setEnabled(boolean enabled) {
        if (!enabled) {
            checker.stop();
        } else {
            LOGGER.info("Starting monitoring watch collection for " + instance);
            checker.start(runtime, 1, SECONDS, this::checkWatches);
        }
    }

    public long getEvaluationLoopTime() {
        return evalLoopTime.get();
    }

    @Override
    public AlertStatistics getAlertStatistics() {
        return statistics.get();
    }

    @Override
    public Collection<Alert> alertsMatching(Predicate<Alert> filter) {
        List<Alert> matches = new ArrayList<>();
        for (Queue<Alert> queue : alerts.values()) {
            for (Alert a : queue) {
                if (filter.test(a)) {
                    matches.add(a);
                }
            }
        }
        return matches;
    }

    @Override
    public Collection<Alert> alerts() {
        List<Alert> all = new ArrayList<>();
        for (Queue<Alert> queue : alerts.values()) {
            all.addAll(queue);
        }
        return all;
    }

    @Override
    public Collection<Alert> alertsFor(Series series) {
        if (!series.isPattern()) {
            // this is the usual path called while polling for data so this should not be too expensive
            Deque<Alert> alertsForSeries = alerts.get(series);
            return alertsForSeries == null ? emptyList() : unmodifiableCollection(alertsForSeries);
        }
        if (series.equalTo(Series.ANY)) {
            return alerts();
        }
        Collection<Alert> matches = null;
        for (Entry<Series, Deque<Alert>> e : alerts.entrySet()) {
            if (series.matches(e.getKey())) {
                if (matches == null) {
                    matches = e.getValue();
                } else {
                    if (!(matches instanceof ArrayList)) {
                        matches = new ArrayList<>(matches);
                    }
                    matches.addAll(e.getValue());
                }
            }
        }
        return matches == null ? emptyList() : unmodifiableCollection(matches);
    }

    @Override
    public Watch watchByName(String name) {
        return watchesByName.get(name);
    }

    @Override
    public void addWatch(Watch watch) {
        if (watchConfig.isDisabled(watch.name)) {
            watch.disable();
        }
        Watch existing = watchesByName.put(watch.name, watch);
        if (existing != null) {
            removeWatch(existing);
        }
        Series series = watch.watched.series;
        Map<Series, Map<String, Watch>> target = series.isPattern() ? patternWatches : simpleWatches;
        target.computeIfAbsent(series, key -> new ConcurrentHashMap<>()).put(watch.name, watch);
        if (!watch.isProgrammatic()) {
            watchConfig.add(watch.name, watch.toJSON().toString());
        }
    }

    @Override
    public void removeWatch(Watch watch) {
        watch.stop();
        String name = watch.name;
        if (watchesByName.get(name) == watch) {
            watchesByName.remove(name);
            Map<String, Watch> collectedByInstance = collectedWatchesByInstance.get(instance);
            if (collectedByInstance != null) {
                collectedByInstance.remove(name);
            }
            removeWatch(watch, simpleWatches);
            removeWatch(watch, patternWatches);
            if (!watch.isProgrammatic()) {
                watchConfig.remove(watch.name);
            }
        }
    }

    private static void removeWatch(Watch watch, Map<Series, Map<String, Watch>> map) {
        String name = watch.name;
        Iterator<Entry<Series, Map<String, Watch>>> iter = map.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<Series, Map<String, Watch>> entry = iter.next();
            Map<String, Watch> watches = entry.getValue();
            if (watches.get(name) == watch) {
                watches.remove(name);
                if (watches.isEmpty()) {
                    iter.remove();
                }
                return;
            }
        }
    }

    @Override
    public Collection<Watch> watches() {
        List<Watch> all = new ArrayList<>();
        for (Map<?, Watch> watches : simpleWatches.values()) {
            all.addAll(watches.values());
        }
        for (Map<?, Watch> watches : patternWatches.values()) {
            all.addAll(watches.values());
        }
        return all;
    }

    @Override
    public Collection<Watch> wachtesFor(Series series) {
        if (!series.isPattern()) {
            // this is the usual path called while polling for data so this should not be too expensive
            Map<?, Watch> watches = simpleWatches.get(series);
            return watches == null ? emptyList() : unmodifiableCollection(watches.values());
        }
        if (series.equalTo(Series.ANY)) {
            return watches();
        }
        Map<?, Watch> seriesWatches = patternWatches.get(series);
        Collection<Watch> watches = seriesWatches == null ? emptyList() : seriesWatches.values();
        for (Map<?, Watch> simple : simpleWatches.values()) {
            for (Watch w : simple.values()) {
                if (series.matches(w.watched.series)) {
                    if (!(watches instanceof ArrayList)) {
                        watches = new ArrayList<>(watches);
                    }
                    watches.add(w);
                }
            }
        }
        return unmodifiableCollection(watches);
    }

    private void checkWatches() {
        long start = System.currentTimeMillis();
        try {
            collectWatches();
        } catch (Exception ex) {
            LOGGER.log(FINE, "Failed to collect watches", ex);
        }
        if (isDAS) { // only evaluate watches on DAS
            try {
                checkWatches(simpleWatches.values());
                checkWatches(patternWatches.values());
                statistics.set(computeStatistics());
            } catch (Exception ex) {
                LOGGER.log(FINE, "Failed to check watches", ex);
            }
        } else {
            Map<String, Watch> instanceWachtes = collectedWatchesByInstance.get(instance);
            sendMessage(new WatchesSnapshot(instance, instanceWachtes.values().stream().toArray(Watch[]::new)));
        }
        evalLoopTime.set(System.currentTimeMillis() - start);
    }

    private void sendMessage(WatchesSnapshot msg) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(msg);
            oos.flush();
            runtime.send(bos.toByteArray());
        } catch (IOException ex) {
            LOGGER.log(FINE, "Failed to send collected watches message", ex);
        }
    }

    private void collectWatches() {
        List<MonitoringWatchSource> sources = this.sources.get();
        Map<String, Watch> collectedBefore = collectedWatchesByInstance.computeIfAbsent(instance,
                key -> new ConcurrentHashMap<>());
        if (sources.isEmpty() && collectedBefore.isEmpty()) {
            return; // nothing to do
        }
        Set<String> notYetCollectedWatches = new HashSet<>(collectedBefore.keySet());
        MonitoringWatchCollector collector = new MonitoringWatchCollector() {
            @Override
            public WatchBuilder watch(CharSequence series, String name, String unit) {
                return new WatchBuilder() {
                    @Override
                    public WatchBuilder with(String level, long startThreshold, Number startForLast,
                            boolean startOnAverage, Long stopTheshold, Number stopForLast, boolean stopOnAverage) {
                        notYetCollectedWatches.remove(name);
                        Watch watch = collectedBefore.get(name);
                        if (watch == null) {
                            Metric watched = new Metric(new Series(series.toString()), Unit.fromShortName(unit));
                            watch = new Watch(name, watched).programmatic();
                        }
                        Watch updated = watch.with(level, startThreshold, startForLast, startOnAverage, stopTheshold,
                                stopForLast, stopOnAverage);
                        if (updated != watch) {
                            addWatch(updated); // this stops and removes existing watch for that name
                            collectedBefore.put(name, updated);
                        }
                        return this;
                    }
                };
            }
        };
        for (MonitoringWatchSource source : sources) {
            try {
                source.collect(collector);
            } catch (Exception ex) {
                LOGGER.log(FINE, "Failed to collect watch source " + source.getClass().getSimpleName(), ex);
            }
        }
        if (!notYetCollectedWatches.isEmpty()) {
            for (String name : notYetCollectedWatches) {
                removeWatch(collectedBefore.get(name));
                collectedBefore.remove(name);
            }
        }
    }

    private AlertStatistics computeStatistics() {
        AlertStatistics stats = new AlertStatistics();
        stats.changeCount = Alert.getChangeCount();
        stats.watches = watchesByName.size();
        if (alerts.isEmpty()) {
            return stats;
        }
        List<Integer> redSerials = new ArrayList<>();
        List<Integer> amberSerials = new ArrayList<>();
        for (Deque<Alert> seriesAlerts : alerts.values()) {
            for (Alert a : seriesAlerts) {
                if (a.getLevel() == Level.RED) {
                    if (a.isAcknowledged()) {
                        stats.acknowledgedRedAlerts++;
                    } else {
                        stats.unacknowledgedRedAlerts++;
                    }
                    redSerials.add(a.serial);
                } else if (a.getLevel() == Level.AMBER) {
                    if (a.isAcknowledged()) {
                        stats.acknowledgedAmberAlerts++;
                    } else {
                        stats.unacknowledgedAmberAlerts++;
                    }
                    amberSerials.add(a.serial);
                }
            }
        }
        stats.ongoingRedAlerts = redSerials.isEmpty() ? new int[0] : redSerials.stream().mapToInt(e -> e).toArray();
        stats.ongoingAmberAlerts = amberSerials.isEmpty() ? new int[0] : amberSerials.stream().mapToInt(e -> e).toArray();
        return stats;
    }

    private void checkWatches(Collection<? extends Map<?, Watch>> watches) {
        for (Map<?, Watch> group : watches) {
            for (Watch watch : group.values()) {
                if (watch.isStopped()) {
                    removeWatch(watch);
                } else {
                    try {
                        checkWatch(watch);
                    } catch (Exception ex) {
                        LOGGER.log(java.util.logging.Level.FINE, "Failed to check watch : " + watch, ex);
                    }
                }
            }
        }
    }

    private void checkWatch(Watch watch) {
        if (watch.isDisabled()) {
            return;
        }
        for (Alert newlyRaised : watch.check(monitoringData)) {
            Deque<Alert> seriesAlerts = alerts.computeIfAbsent(newlyRaised.getSeries(),
                    key -> new ConcurrentLinkedDeque<>());
            seriesAlerts.add(newlyRaised);
            limitQueueSize(seriesAlerts);
        }
    }

    private static void limitQueueSize(Deque<Alert> seriesAlerts) {
        if (seriesAlerts.size() > MAX_ALERTS_PER_SERIES) {
            if (!removeFirst(seriesAlerts, alert -> alert.getLevel().isLessSevereThan(Level.AMBER))) {
                if (!removeFirst(seriesAlerts, Alert::isAcknowledged)) {
                    if (!removeFirst(seriesAlerts, alert -> alert.getLevel() == Level.AMBER)) {
                        seriesAlerts.removeFirst();
                    }
                }
            }
        }
    }

    private static boolean removeFirst(Deque<Alert> alerts, Predicate<Alert> test) {
        Iterator<Alert> iter = alerts.iterator();
        while (iter.hasNext()) {
            Alert a = iter.next();
            if (test.test(a)) {
                iter.remove();
                return true;
            }
        }
        return false;
    }

    public static final class WatchesSnapshot implements Serializable {

        final String instance;
        final Watch[] watches;

        WatchesSnapshot(String instance, Watch[] watches) {
            this.instance = instance;
            this.watches = watches;
        }
    }
}
