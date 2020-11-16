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

    private static final int SINGLE_CAPACITY = 24;
    private static final int DOUBLE_CAPACITY = 2 * SINGLE_CAPACITY;

    private static final long MILLIS_IN_ONE_HOUR = Duration.ofHours(1).toMillis();

    public static final HoursDataset EMPTY = new HoursDataset();

    private final DaysDataset recentDays;

    private HoursDataset() {
        super();
        this.recentDays = DaysDataset.EMPTY;
    }

    private HoursDataset(HoursDataset predecessor, MinutesDataset hour) {
        super(predecessor, SINGLE_CAPACITY, offset(predecessor, hour), firstTime(predecessor, hour));
        if (lastIndex() != hourOfDay(hour)) {
            throw new IllegalArgumentException("Hour did not directly follow the end of the predecessor");
        }
        aggregate(hour);
        this.recentDays = predecessor.recentDays.add(this);
    }

    private HoursDataset(HoursDataset predecessor, MinutesDataset hour, int newCapacity) {
        super(predecessor, newCapacity);
        aggregate(hour);
        this.recentDays = predecessor.recentDays.add(this);
    }

    private HoursDataset(MinutesDataset hour, HoursDataset predecessor) {
        super(predecessor);
        aggregate(hour);
        this.recentDays = predecessor.recentDays.add(this);
    }

    private static int offset(HoursDataset predecessor, MinutesDataset hour) {
        return !predecessor.isEmpty()
            ? predecessor.offset
            : hourOfDay(hour);
    }

    private static long firstTime(HoursDataset predecessor, MinutesDataset hour) {
        return !predecessor.isEmpty()
                ? predecessor.firstTime()
                : atStartOfHour(hour).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli();
    }

    private static int hourOfDay(MinutesDataset hour) {
        return atStartOfHour(hour).getHour();
    }

    private static OffsetDateTime atStartOfHour(MinutesDataset hour) {
        return Instant.ofEpochMilli(hour.firstTime()).atOffset(ZoneOffset.UTC);
    }

    /**
     * @return the history of the recent days up to this hour (if it has been recorded)
     */
    public DaysDataset getRecentDays() {
        return recentDays;
    }

    public HoursDataset add(MinutesDataset hour) {
        if (!hour.isEndOfHour()) {
            return this;
        }
        if (capacity() == 0) {
            return new HoursDataset(this, hour);
        }
        if (capacity() == SINGLE_CAPACITY) {
            return size() == SINGLE_CAPACITY
                    ? new HoursDataset(this, hour, DOUBLE_CAPACITY)
                    : new HoursDataset(this, hour);
        }
        return isEndOfDay()
                ? new HoursDataset(this, hour, DOUBLE_CAPACITY)
                : new HoursDataset(hour, this);
    }

    private void aggregate(MinutesDataset hour) {
        int numberOfMinutesInAggregate = hour.size(); // might be less then 60 when first starting to record in the middle of an hour
        int firstMinuteOfHour = hour.offset;
        int lastMiniteOfHour = Math.max(59, firstMinuteOfHour + numberOfMinutesInAggregate);
        int points = hour.getNumberOfPoints(firstMinuteOfHour);
        long min = hour.getMinimum(firstMinuteOfHour);
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
        return lastIndex() == SINGLE_CAPACITY - 1;
    }

    @Override
    public long getTime(int hourOfDay) {
        int hoursIn = hourOfDay - offset;
        if (hoursIn < 0) { // next day
            hoursIn = (capacity() - offset) + hourOfDay;
        }
        return firstTime() + (hoursIn * MILLIS_IN_ONE_HOUR);
    }

    @Override
    public long getIntervalLength() {
        return MILLIS_IN_ONE_HOUR;
    }

}
