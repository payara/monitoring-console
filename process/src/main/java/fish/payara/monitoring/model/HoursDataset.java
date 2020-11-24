package fish.payara.monitoring.model;

import static java.time.ZoneOffset.UTC;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

/**
 * A {@link HoursDataset} contains up to 24 hours of statistical data with one data point per hour for minimum, maximum,
 * average and the number of points these originate from.
 *
 * @author Jan Bernitt
 */
public final class HoursDataset extends AggregateDataset<HoursDataset> {

    private static final int HOURS_PER_DAY = 24;
    private static final long MILLIS_IN_ONE_HOUR = Duration.ofHours(1).toMillis();

    public static final HoursDataset EMPTY = new HoursDataset();

    private final DaysDataset recentDays;

    private HoursDataset() {
        super();
        this.recentDays = DaysDataset.EMPTY;
    }

    private HoursDataset(HoursDataset predecessor, MinutesDataset hour) {
        super(HOURS_PER_DAY, predecessor, atStartOfHour(hour.lastTime()));
        aggregate(hour);
        this.recentDays = predecessor.recentDays.add(this);
    }

    public static long atStartOfHour(long time) {
        return Instant.ofEpochMilli(time).atOffset(UTC)
                .withNano(0)
                .withSecond(0)
                .withMinute(0)
                .toInstant().toEpochMilli();
    }

    /**
     * @return the history of the recent days up to this hour (if it has been recorded)
     */
    public DaysDataset getRecentDays() {
        return recentDays;
    }

    public HoursDataset add(MinutesDataset hour) {
        if (!hour.endsWithLastMinuteOfHour()) {
            return this;
        }
        return new HoursDataset(this, hour);
    }

    private void aggregate(MinutesDataset hour) {
        int numberOfMinutesInAggregate = hour.size(); // might be less then 60 when first starting to record in the middle of an hour
        int firstMinuteOfHour = hour.offset;
        int lastMiniteOfHour = Math.min(59, firstMinuteOfHour + numberOfMinutesInAggregate);
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
                avg.divide(BigDecimal.valueOf(numberOfMinutesInAggregate), BigDecimal.ROUND_DOWN).doubleValue());
    }

    /**
     * @return true if this dataset contains data up to and including the last hour of the day, else false
     */
    public boolean endsWithLastHourOfDay() {
        return isEmpty() ? false :  Instant.ofEpochMilli(lastTime()).atOffset(UTC).getHour() == 23;
    }

    @Override
    public long getIntervalLength() {
        return MILLIS_IN_ONE_HOUR;
    }

}
