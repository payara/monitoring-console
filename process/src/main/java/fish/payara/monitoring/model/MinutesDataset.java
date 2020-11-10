package fish.payara.monitoring.model;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * A {@link MinutesDataset} contains up to 60 minutes of statistical data with one data point per minute for minimum,
 * maximum, average and number of points that original {@link SeriesDataset} had for that minute.
 *
 * @author Jan Bernitt
 */
public final class MinutesDataset extends AggregateDataset<MinutesDataset> {

    public MinutesDataset() {
        super(60);
    }

    public MinutesDataset(MinutesDataset predecessor, SeriesDataset minute) {
        super(predecessor, offset(predecessor, minute), firstTime(predecessor, minute));
        aggregate(minute, minuteOfHour(minute));
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

    private void aggregate(SeriesDataset minute, int minuteOfHour) {
        long[] points = minute.points();
        this.points[minuteOfHour] = points.length / 2;
        long min = points[1];
        long max = points[1];
        BigInteger avg = BigInteger.valueOf(points[1]);
        for (int i = 3; i < points.length; i+=2) {
            long val = points[i];
            min = Math.min(min, val);
            max = Math.max(max, val);
            avg = avg.add(BigInteger.valueOf(val));
        }
        this.mins[minuteOfHour] = min;
        this.maxs[minuteOfHour] = max;
        this.avgs[minuteOfHour] = new BigDecimal(avg).divide(BigDecimal.valueOf(this.points[minuteOfHour])).doubleValue();
    }

    public long getTime(int minuteOfHour) {
        int minutesIn = minuteOfHour - offset;
        if (minutesIn < 0) { // next hour
            minutesIn = (capacity() - offset) + minuteOfHour;
        }
        return firstTime + (minutesIn * 60000L);
    }

}
