package fish.payara.monitoring.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

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

    private static final int SINGLE_CAPACITY = 60;
    private static final int DOUBLE_CAPACITY = 2 * SINGLE_CAPACITY;

    private static final long MILLIS_IN_ONE_MINUTE = Duration.ofMinutes(1).toMillis();

    public static final MinutesDataset EMPTY = new MinutesDataset();

    private final HoursDataset recentHours;

    private MinutesDataset() {
        super();
        this.recentHours = HoursDataset.EMPTY;
    }

    private MinutesDataset(MinutesDataset predecessor, SeriesDataset minute) {
        super(predecessor, SINGLE_CAPACITY, offset(predecessor, minute), firstTime(predecessor, minute));
        if (lastIndex() != minuteOfHour(minute)) {
            throw new IllegalArgumentException("Minute did not directly continue the end of the predecessor");
        }
        aggregate(minute);
        this.recentHours = predecessor.recentHours.add(this);
    }

    private MinutesDataset(MinutesDataset predecessor, SeriesDataset minute, int newCapacity) {
        super(predecessor, newCapacity);
        aggregate(minute);
        this.recentHours = predecessor.recentHours.add(this);
    }

    private MinutesDataset(SeriesDataset minute, MinutesDataset predecessor) {
        super(predecessor);
        aggregate(minute);
        this.recentHours = predecessor.recentHours.add(this);
    }

    private static int offset(MinutesDataset predecessor, SeriesDataset minute) {
        return !predecessor.isEmpty()
            ? predecessor.offset
            : minuteOfHour(minute);
    }

    private static long firstTime(MinutesDataset predecessor, SeriesDataset minute) {
        return !predecessor.isEmpty()
            ? predecessor.firstTime()
            : atEndOfMinute(minute).withSecond(0).withNano(0).toInstant().toEpochMilli();
    }

    private static int minuteOfHour(SeriesDataset minute) {
        return atEndOfMinute(minute).getMinute();
    }

    private static OffsetDateTime atEndOfMinute(SeriesDataset minute) {
        return Instant.ofEpochMilli(minute.lastTime()).atOffset(ZoneOffset.UTC);
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
        if (!minute.isEndOfMinute()) {
            return this;
        }
        if (capacity() == 0) {
            return new MinutesDataset(this, minute);
        }
        if (capacity() == SINGLE_CAPACITY) { // offset => end, start => offset
            return size() == SINGLE_CAPACITY
                ? new MinutesDataset(this, minute, DOUBLE_CAPACITY) // complete, start sliding
                : new MinutesDataset(this, minute); // add (with wrap)
        }
        // double capacity
        return isEndOfHour()
                ? new MinutesDataset(this, minute, DOUBLE_CAPACITY)
                : new MinutesDataset(minute, this);
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
                new BigDecimal(avg).divide(BigDecimal.valueOf(numberOfPointsInAggregate)).doubleValue());
    }

    /**
     * @return true if this dataset contains data up to and including the last minute of the hour, else false
     */
    public boolean isEndOfHour() {
        return lastIndex() == SINGLE_CAPACITY - 1;
    }

    @Override
    public long getTime(int minuteOfHour) {
        int minutesIn = minuteOfHour - offset;
        if (minutesIn < 0) { // next hour
            minutesIn = (capacity() - offset) + minuteOfHour;
        }
        return firstTime() + (minutesIn * MILLIS_IN_ONE_MINUTE);
    }

    @Override
    public long getIntervalLength() {
        return MILLIS_IN_ONE_MINUTE;
    }
}
