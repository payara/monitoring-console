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

    private static final long MILLIS_IN_ONE_MINUTE = Duration.ofMinutes(1).toMillis();

    /**
     * Creates an empty {@link MinutesDataset}.
     */
    public MinutesDataset() {
        super(60);
    }

    private MinutesDataset(MinutesDataset predecessor, SeriesDataset minute) {
        super(predecessor, offset(predecessor, minute), firstTime(predecessor, minute));
        if (lastIndex() != minuteOfHour(minute)) {
            throw new IllegalArgumentException("Minute did not directly continue the end of the predecessor");
        }
        aggregate(minute);
    }

    private static int offset(MinutesDataset predecessor, SeriesDataset minute) {
        return predecessor.length > 0
            ? predecessor.offset
            : minuteOfHour(minute);
    }

    private static long firstTime(MinutesDataset predecessor, SeriesDataset minute) {
        return predecessor.length > 0
            ? predecessor.firstTime
            : lastDateTime(minute).withSecond(0).withNano(0).toInstant().toEpochMilli();
    }

    private static int minuteOfHour(SeriesDataset minute) {
        return lastDateTime(minute).getMinute();
    }

    private static OffsetDateTime lastDateTime(SeriesDataset minute) {
        return Instant.ofEpochMilli(minute.lastTime()).atOffset(ZoneOffset.UTC);
    }

    public MinutesDataset add(SeriesDataset minute) {
        if (capacity() == 60) {
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
                new BigDecimal(avg).divide(BigDecimal.valueOf(numberOfPointsInAggregate)).doubleValue());
    }

    /**
     * @return true if this dataset contains data up to and including the last minute of the hour, else false
     */
    public boolean isEndOfHour() {
        return lastIndex() == 59;
    }

    @Override
    public long getTime(int minuteOfHour) {
        int minutesIn = minuteOfHour - offset;
        if (minutesIn < 0) { // next hour
            minutesIn = (capacity() - offset) + minuteOfHour;
        }
        return firstTime + (minutesIn * MILLIS_IN_ONE_MINUTE);
    }

}
