package fish.payara.monitoring.model;

import static java.time.ZoneOffset.UTC;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;

/**
 * A {@link MinutesDataset} contains up to 60 minutes of statistical data with one data point per minute for minimum,
 * maximum, average and number of points that original {@link SeriesDataset} had for that minute.
 *
 * Each minute is stored at the index corresponding to the minute of the hour starting at index 0 for the first minute
 * of an hour.
 *
 * Note that a minute at an index before the offset belongs to an hour following the hour that corresponds to the
 * minutes from the offset and beyond it.
 *
 * @author Jan Bernitt
 */
public final class MinutesDataset extends AggregateDataset<MinutesDataset> {

    private static final int MINUTES_PER_HOUR = 60;
    private static final long MILLIS_IN_ONE_MINUTE = Duration.ofMinutes(1).toMillis();

    public static final MinutesDataset EMPTY = new MinutesDataset();

    private final HoursDataset recentHours;

    private MinutesDataset() {
        super();
        this.recentHours = HoursDataset.EMPTY;
    }

    private MinutesDataset(MinutesDataset predecessor, SeriesDataset minute) {
        super(MINUTES_PER_HOUR, predecessor, atStartOfMinute(minute.lastTime()));
        aggregate(minute);
        this.recentHours = predecessor.recentHours.add(this);
    }

    private static long atStartOfMinute(long time) {
        return Instant.ofEpochMilli(time).atOffset(UTC)
                .withNano(0)
                .withSecond(0)
                .toInstant().toEpochMilli();
    }

    /**
     * @return the history of the recent hours up to this minute (if it has been recorded)
     *
     *         Indirectly this gives also access to the {@link HoursDataset#getRecentDays()} history.
     */
    public HoursDataset getRecentHours() {
        return recentHours;
    }

    public MinutesDataset add(SeriesDataset minute) {
        if (!minute.endsWithLastSecondOfMinute()) {
            return this;
        }
        return new MinutesDataset(this, minute);
    }

    private void aggregate(SeriesDataset minute) {
        long[] points = minute.points();
        int numberOfPointsInAggregate = points.length / 2;
        long min = points[1];
        long max = points[1];
        BigInteger avg = BigInteger.valueOf(points[1]);
        for (int i = 3; i < points.length; i+=2) {
            long val = points[i];
            min = Math.min(min, val);
            max = Math.max(max, val);
            avg = avg.add(BigInteger.valueOf(val));
        }
        setEntry(numberOfPointsInAggregate, min, max,
                new BigDecimal(avg).divide(BigDecimal.valueOf(numberOfPointsInAggregate), BigDecimal.ROUND_DOWN).doubleValue());
    }

    /**
     * @return true if this dataset contains data up to and including the last minute of the hour, else false
     */
    public boolean endsWithLastMinuteOfHour() {
        return isEmpty()
                ? false
                : Instant.ofEpochMilli(lastTime()).atOffset(UTC).getMinute() == 59;
    }

    @Override
    public long getIntervalLength() {
        return MILLIS_IN_ONE_MINUTE;
    }
}
