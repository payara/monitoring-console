package fish.payara.monitoring.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * A {@link HoursDataset} contains up to 24 hours of statistical data with one data point per hour for minimum, maximum,
 * average and the number of points these originate from.
 *
 * @author Jan Bernitt
 */
public final class HoursDataset extends AggregateDataset<HoursDataset> {

    private static final long MILLIS_IN_ONE_HOUR = Duration.ofHours(1).toMillis();

    /**
     * Creates an empty {@link HoursDataset}.
     */
    public HoursDataset() {
        super(24);
    }

    private HoursDataset(HoursDataset predecessor, MinutesDataset hour) {
        super(predecessor, offset(predecessor, hour), firstTime(predecessor, hour));
        if (lastIndex() != hourOfDay(hour)) {
            throw new IllegalArgumentException("Hour did not directly follow the end of the predecessor");
        }
        aggregate(hour);
    }

    private static int offset(HoursDataset predecessor, MinutesDataset hour) {
        return predecessor.length > 0
            ? predecessor.offset
            : hourOfDay(hour);
    }

    private static long firstTime(HoursDataset predecessor, MinutesDataset hour) {
        return predecessor.length > 0
                ? predecessor.firstTime
                : lastDateTime(hour).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli();
    }

    private static int hourOfDay(MinutesDataset hour) {
        return lastDateTime(hour).getHour();
    }

    private static OffsetDateTime lastDateTime(MinutesDataset hour) {
        return Instant.ofEpochMilli(hour.firstTime()).atOffset(ZoneOffset.UTC);
    }

    public HoursDataset add(MinutesDataset hour) {
        if (!hour.isEndOfHour()) {
            throw new IllegalArgumentException("Only add complete hour to an HoursDataset");
        }
        //TODO
        return new HoursDataset(this, hour);
    }

    private void aggregate(MinutesDataset hour) {
        int numberOfMinutesInAggregate = hour.length; // might be less then 60 when first starting to record in the middle of an hour
        int firstMinuteOfHour = hour.offset;
        int lastMiniteOfHour = Math.max(59, firstMinuteOfHour + numberOfMinutesInAggregate);
        int points = hour.getNumberOfPoints(firstMinuteOfHour);
        long min = hour.getMaximum(firstMinuteOfHour);
        long max = hour.getMaximum(firstMinuteOfHour);
        BigDecimal avg = BigDecimal.valueOf(hour.getAverage(firstMinuteOfHour));
        for (int i = firstMinuteOfHour + 1; i <= lastMiniteOfHour; i++) {
            points += hour.getNumberOfPoints(i);
            min = Math.min(min, hour.getMinimum(i));
            max = Math.max(max, hour.getMaximum(i));
            avg = avg.add(BigDecimal.valueOf(hour.getAverage(i)));
        }
        setEntry(points, min, max,
                avg.divide(BigDecimal.valueOf(numberOfMinutesInAggregate)).doubleValue());
    }

    /**
     * @return true if this dataset contains data up to and including the last hour of the day, else false
     */
    public boolean isEndOfDay() {
        return lastIndex() == 23;
    }

    @Override
    public long getTime(int hourOfDay) {
        int hoursIn = hourOfDay - offset;
        if (hoursIn < 0) { // next day
            hoursIn = (capacity() - offset) + hourOfDay;
        }
        return firstTime + (hoursIn * MILLIS_IN_ONE_HOUR);
    }
}
