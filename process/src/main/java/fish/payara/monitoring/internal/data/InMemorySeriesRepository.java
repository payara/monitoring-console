/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2019-2020 Payara Foundation and/or its affiliates. All rights reserved.
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

package fish.payara.monitoring.internal.data;

import static java.util.Arrays.asList;
import static java.util.Arrays.copyOf;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import fish.payara.monitoring.adapt.MonitoringConsoleRuntime;
import fish.payara.monitoring.collect.MonitoringData;
import fish.payara.monitoring.collect.MonitoringDataCollector;
import fish.payara.monitoring.collect.MonitoringDataSource;
import fish.payara.monitoring.data.ConsumingMonitoringDataCollector;
import fish.payara.monitoring.data.MonitoringAnnotationConsumer;
import fish.payara.monitoring.data.MonitoringDataConsumer;
import fish.payara.monitoring.data.SeriesRepository;
import fish.payara.monitoring.internal.util.JobHandle;
import fish.payara.monitoring.model.EmptyDataset;
import fish.payara.monitoring.model.Series;
import fish.payara.monitoring.model.SeriesAnnotation;
import fish.payara.monitoring.model.SeriesAnnotations;
import fish.payara.monitoring.model.SeriesDataset;

/**
 * A simple in-memory store for a fixed size sliding window for each {@link Series}.
 *
 *
 * <h3>Consistency Remarks</h3>
 *
 * The store uses two maps working like a doubled buffered image. While collection writes to the {@link #secondsWrite}
 * map request are served from the {@link #secondsRead} map. This makes sure that a consistent dataset across all series
 * can be used to create a consistent visualisation that isn't half updated while the response is composed. However
 * this requires that callers are provided with a method that returns all the {@link SeriesDataset}s they need in a
 * single method invocation. Making multiple calls to this stores methods does not guarantee a consistent dataset across
 * all series since the {@link #swapLocalBuffer()} can happen inbetween method calls.
 *
 * @author Jan Bernitt
 */
public class InMemorySeriesRepository implements SeriesRepository {

    private static final Logger LOGGER = Logger.getLogger("monitoring-console-core");

    private static final int MAX_ANNOTATIONS_PER_SERIES = 20;

    private final Set<String> sourcesFailingBefore = ConcurrentHashMap.newKeySet();


    private final String instanceName;
    private final boolean isDas;
    private final MonitoringConsoleRuntime runtime;
    private final Supplier<? extends List<MonitoringDataSource>> sources;

    private volatile Map<Series, SeriesDataset> secondsWrite = new ConcurrentHashMap<>();
    private volatile Map<Series, SeriesDataset> secondsRead = new ConcurrentHashMap<>();
    private final Map<Series, SeriesDataset[]> remoteInstanceDatasets = new ConcurrentHashMap<>();
    private final Map<Series, SeriesAnnotations> annotationsBySeries = new ConcurrentHashMap<>();
    private final Set<String> instances = ConcurrentHashMap.newKeySet();
    private final JobHandle dataCollectionJob = new JobHandle("monitoring data collection");
    private long collectedSecond;
    private int estimatedNumberOfSeries = 50;

    public InMemorySeriesRepository(String instanceName, boolean receiver, MonitoringConsoleRuntime runtime,
            Supplier<? extends List<MonitoringDataSource>> sources) {
        this.isDas = receiver;
        this.instanceName = instanceName;
        this.runtime = runtime;
        this.sources = sources;
        if (isDas) {
            runtime.receive(this::receiveMesssage);
        }
        instances.add(instanceName);
    }

    public void setEnabled(boolean enabled) {
        if (!enabled) {
            dataCollectionJob.stop();
        } else {
            LOGGER.info("Starting monitoring data collection for " + instanceName);
            dataCollectionJob.start(runtime, 1, SECONDS, isDas ? this::collectSourcesToMemory : this::collectSourcesToPublish);
        }
    }

    @Override
    public Set<String> instances() {
        return instances;
    }

    private void receiveMesssage(byte[] msg) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(msg))) {
            addRemoteDatasets((SeriesDatasetsSnapshot) ois.readObject());
        } catch (Exception ex) {
            LOGGER.log(Level.FINE, "Failed to receive monitoring data message", ex);
        }
    }

    private void addRemoteDatasets(SeriesDatasetsSnapshot snapshot) {
        String instance = snapshot.instance;
        instances.add(instance);
        long time = snapshot.time;
        if (snapshot.annotations != null) {
            for (SeriesAnnotation a : snapshot.annotations) {
                addRemoteAnnotation(a);
            }
        }
        for (int i = 0; i < snapshot.numberOfSeries; i++) {
            Series series = null;
            try {
                series = new Series(snapshot.series[i]);
            } catch (IllegalArgumentException ex) {
                LOGGER.log(Level.FINEST, "Failed to add remote series: " + snapshot.series[i], ex);
            }
            if (series != null) {
                long value = snapshot.values[i];
                remoteInstanceDatasets.compute(series, //
                        (key, seriesByInstance) -> addRemotePoint(seriesByInstance, instance, key, time, value));
            }
        }
    }

    private static SeriesDataset[] addRemotePoint(SeriesDataset[] seriesByInstance, String instance, Series series, long time, long value) {
        if (seriesByInstance == null) {
            return new SeriesDataset[] { new EmptyDataset(instance, series, 60).add(time, value) };
        }
        for (int i = 0; i < seriesByInstance.length; i++) {
            SeriesDataset instanceSet = seriesByInstance[i];
            if (instanceSet.getInstance().equals(instance)) {
                seriesByInstance[i] = seriesByInstance[i].add(time, value);
                return seriesByInstance;
            }
        }
        seriesByInstance = Arrays.copyOf(seriesByInstance, seriesByInstance.length + 1);
        seriesByInstance[seriesByInstance.length - 1] = new EmptyDataset(instance, series, 60).add(time, value);
        return seriesByInstance;
    }

    private void collectSourcesToMemory() {
        tick();
        for (Entry<Series, SeriesDataset> e : secondsRead.entrySet()) {
            secondsWrite.put(e.getKey(), e.getValue());
        }
        collectAll(new ConsumingMonitoringDataCollector(this::addLocalPoint, this::addLocalAnnotation));
        swapLocalBuffer();
    }

    private void collectSourcesToPublish() {
        tick();
        SeriesDatasetsSnapshot msg = new SeriesDatasetsSnapshot(instanceName, collectedSecond, estimatedNumberOfSeries);
        collectAll(new ConsumingMonitoringDataCollector(msg, msg));
        estimatedNumberOfSeries = msg.numberOfSeries;
        sendMessaage(msg);
    }

    private void sendMessaage(SeriesDatasetsSnapshot msg) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(msg);
            oos.flush();
            runtime.send(bos.toByteArray());
        } catch (IOException ex) {
            LOGGER.log(Level.FINE, "Failed to send monitoring data message", ex);
        }
    }

    private void collectAll(MonitoringDataCollector collector) {
        long collectionStart = System.currentTimeMillis();
        int collectedSources = 0;
        int failedSources = 0;
        final long second = collectedSecond / 1000;
        MonitoringDataCollector monitoringCollector = collector.in("monitoring");
        for (MonitoringDataSource source : sources.get()) {
            String sourceId = source.getClass().getSimpleName(); // for now this is the ID, we might want to replace that later
            MonitoringData meta = getMetaAnnotation(source);
            if (collectNow(second, meta)) {
                try {
                    collectedSources++;
                    long sourceStart = System.currentTimeMillis();
                    source.collect(meta == null ? collector : collector.in(meta.ns()));
                    sourcesFailingBefore.remove(sourceId);
                    monitoringCollector.group(sourceId)
                        .collect("CollectionDuration", System.currentTimeMillis() - sourceStart);
                } catch (RuntimeException e) {
                    if (!sourcesFailingBefore.contains(sourceId)) {
                        // only long once unless being successful again
                        LOGGER.log(Level.FINE, "Error collecting metrics", e);
                    }
                    failedSources++;
                    sourcesFailingBefore.add(sourceId);
                }
            }
        }
        long estimatedTotalBytesMemory = 0L;
        for (SeriesDataset set : secondsWrite.values()) {
            estimatedTotalBytesMemory += set.estimatedBytesMemory();
        }
        int seriesCount = secondsWrite.size();
        monitoringCollector
            .collect("CollectionDuration", System.currentTimeMillis() - collectionStart)
            .collectNonZero("SeriesCount", seriesCount)
            .collectNonZero("TotalBytesMemory", estimatedTotalBytesMemory)
            .collectNonZero("AverageBytesMemoryPerSeries", seriesCount == 0 ? 0L : estimatedTotalBytesMemory / seriesCount)
            .collect("CollectedSourcesCount", collectedSources)
            .collect("CollectedSourcesErrorCount", failedSources);
    }

    private static boolean collectNow(final long now, MonitoringData meta) {
        return meta == null || now % meta.intervalSeconds() == 0;
    }

    private static MonitoringData getMetaAnnotation(MonitoringDataSource source) {
        try {
            Method collect = source.getClass().getMethod("collect", MonitoringDataCollector.class);
            return collect.getAnnotation(MonitoringData.class);
        } catch (NoSuchMethodException | SecurityException e) {
           return null; // assume no annotation
        }
    }

    /**
     * Forwards the collection time to the current second (milliseconds are stripped)
     */
    private void tick() {
        collectedSecond = (System.currentTimeMillis() / 1000L) * 1000L;
    }

    private void swapLocalBuffer() {
        Map<Series, SeriesDataset> tmp = secondsRead;
        secondsRead = secondsWrite;
        secondsWrite = tmp;
    }

    private void addLocalPoint(CharSequence key, long value) {
        Series series = seriesOrNull(key);
        if (series != null) {
            secondsWrite.compute(series, (s, dataset) -> dataset == null
                ?  emptySet(s).add(collectedSecond, value)
                : dataset.add(collectedSecond, value));
        }
    }

    private void addLocalAnnotation(CharSequence series, long value, boolean keyed, String[] annotations) {
        Series s = seriesOrNull(series);
        if (s != null) {
            addLocalAnnotation(new SeriesAnnotation(collectedSecond, s, instanceName, value, keyed, annotations));
        }
    }

    private void addLocalAnnotation(SeriesAnnotation annotation) {
        if (annotation.getValue() == 0L && !secondsRead.containsKey(annotation.getSeries())) {
            addAnnotation(annotation.permanent());
            return;
        }
        addAnnotation(annotation);
    }

    private void addRemoteAnnotation(SeriesAnnotation annotation) {
        if (annotation.getValue() == 0L
                && !instanceSetExists(remoteInstanceDatasets.get(annotation.getSeries()), annotation.getInstance())) {
            addAnnotation(annotation.permanent());
            return;
        }
        addAnnotation(annotation);
    }

    private static boolean instanceSetExists(SeriesDataset[] sets, String instance) {
        if (sets == null) {
            return false;
        }
        for (SeriesDataset remoteSet : sets) {
            if (remoteSet.getInstance().equals(instance)) {
                return true;
            }
        }
        return false;
    }

    private void addAnnotation(SeriesAnnotation annotation) {
        annotationsBySeries.computeIfAbsent(annotation.getSeries(), //
                key -> new SeriesAnnotations(MAX_ANNOTATIONS_PER_SERIES)).add(annotation);
    }

    static Series seriesOrNull(CharSequence key) {
        try {
            return new Series(key.toString());
        } catch (Exception ex) {
            LOGGER.log(Level.FINEST, "Failed to create local series: " + key, ex);
            return null;
        }
    }

    private SeriesDataset emptySet(Series series) {
        return new EmptyDataset(instanceName, series, 60);
    }

    @Override
    public List<SeriesAnnotation> selectAnnotations(Series series, String... instances) {
        if (!isDas) {
            return emptyList();
        }
        if (series.isPattern()) {
            return selectAnnotationsForPattern(series, instances);
        }
        SeriesAnnotations annotations = annotationsBySeries.get(series);
        if (annotations == null || annotations.isEmpty()) {
            return emptyList();
        }
        if (instances == null || instances.length == 0) {
            return annotations.toList();
        }
        Set<String> filter = new HashSet<>(asList(instances));
        return annotations.stream().filter(a -> filter.contains(a.getInstance())).collect(toList());
    }

    private List<SeriesAnnotation> selectAnnotationsForPattern(Series pattern, String... instances) {
        List<SeriesAnnotation> matches = new ArrayList<>();
        Set<String> filter = createInstanceFilter(instances);
        for (Entry<Series, SeriesAnnotations> entry : annotationsBySeries.entrySet()) {
            if (pattern.matches(entry.getKey())) {
                for (SeriesAnnotation a : entry.getValue()) {
                    if (filter.contains(a.getInstance())) {
                        matches.add(a);
                    }
                }
            }
        }
        return matches;
    }

    @Override
    public List<SeriesDataset> selectSeries(Series series, String... instances) {
        if (!isDas) {
            return emptyList();
        }
        List<SeriesDataset> res = new ArrayList<>();
        selectSeries(res, singleton(series), createInstanceFilter(instances));
        return res;
    }

    public Set<String> createInstanceFilter(String... instances) {
        return instances == null || instances.length == 0
                ? this.instances
                : new HashSet<>(asList(instances));
    }

    private void selectSeries(List<SeriesDataset> res, Set<Series> seriesSet, Set<String> instanceFilter) {
        for (Series series : seriesSet) {
            if (series.isPattern()) {
                selectSeries(res, seriesMatchingPattern(series), instanceFilter);
            } else {
                SeriesDataset localSet = secondsRead.get(series);
                SeriesDataset[] remoteSets = remoteInstanceDatasets.get(series);

                if (localSet != null && isRelevantSet(localSet, instanceFilter)) {
                    res.add(localSet);
                }
                if (remoteSets != null && remoteSets.length > 0) {
                    for (SeriesDataset remoteSet : remoteSets) {
                        if (isRelevantSet(remoteSet, instanceFilter)) {
                            res.add(remoteSet);
                        }
                    }
                }
            }
        }
    }

    private static boolean isRelevantSet(SeriesDataset set, Set<String> instanceFilter) {
        return instanceFilter.contains(set.getInstance());
    }

    private Set<Series> seriesMatchingPattern(Series pattern) {
        Set<Series> matches = new HashSet<>();
        for (Series candidate : secondsRead.keySet()) {
            if (pattern.matches(candidate)) {
                matches.add(candidate);
            }
        }
        for (Series candidate : remoteInstanceDatasets.keySet()) {
            if (pattern.matches(candidate)) {
                matches.add(candidate);
            }
        }
        return matches;
    }

    @Override
    public Iterable<SeriesDataset> selectAllSeries() {
        return secondsRead.values();
    }

    static final class SeriesDatasetsSnapshot
            implements Serializable, MonitoringDataConsumer, MonitoringAnnotationConsumer {

        final String instance;
        final long time;
        // data
        int numberOfSeries;
        String[] series;
        long[] values;
        // annotations
        ArrayList<SeriesAnnotation> annotations;

        SeriesDatasetsSnapshot(String instance, long time, int estimatedNumberOfSeries) {
            this.instance = instance;
            this.time = time;
            this.series = new String[estimatedNumberOfSeries];
            this.values = new long[estimatedNumberOfSeries];
        }

        @Override
        public void accept(CharSequence series, long value) {
            if (numberOfSeries >= this.series.length) {
                this.series = copyOf(this.series, Math.round(this.series.length * 1.3f));
                values = copyOf(values, this.series.length);
            }
            this.series[numberOfSeries] = series.toString();
            values[numberOfSeries++] = value;
        }

        @Override
        public void accept(CharSequence series, long value, boolean keyed, String[] attrs) {
            if (this.annotations == null) {
                this.annotations = new ArrayList<>();
            }
            Series s = seriesOrNull(series.toString());
            if (s != null) {
                this.annotations.add(new SeriesAnnotation(time, s, instance, value, keyed, attrs));
            }
        }
    }

}
