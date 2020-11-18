package fish.payara.monitoring.model;

import static java.time.ZoneOffset.UTC;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

public final class DaysDataset extends AggregateDataset<DaysDataset> {

    private static final int DAYS_PER_MONTH = 31;
    private static final long MILLIS_IN_ONE_DAY = Duration.ofDays(1).toMillis();

    public static final DaysDataset EMPTY = new DaysDataset();

    private DaysDataset() {
        super();
    }

    private DaysDataset(DaysDataset predecessor, HoursDataset day) {
        super(DAYS_PER_MONTH, predecessor, atStartOfDay(day.lastTime()));
        aggregate(day);
    }

    public static long atStartOfDay(long time) {
        return Instant.ofEpochMilli(time).atOffset(UTC)
                .withNano(0)
                .withSecond(0)
                .withMinute(0)
                .withHour(0)
                .toInstant().toEpochMilli();
    }

    public DaysDataset add(HoursDataset day) {
        if (!day.endsWithLastHourOfDay()) {
            return this;
        }
        return new DaysDataset(this, day);
    }

    private void aggregate(HoursDataset day) {
        int numberOfHoursInAggregate = day.size();
        int firstHourOfDay = day.offset;
        int lastHourOfDay = Math.min(23, firstHourOfDay + numberOfHoursInAggregate);
        int points = day.getNumberOfPoints(firstHourOfDay);
        long min = day.getMinimum(firstHourOfDay);
        long max = day.getMaximum(firstHourOfDay);
        BigDecimal avg = BigDecimal.valueOf(day.getAverage(firstHourOfDay));
        for (int i = firstHourOfDay + 1; i <= lastHourOfDay; i++) {
            points += day.getNumberOfPoints(i);
            min = Math.min(min, day.getMinimum(i));
            max = Math.max(max, day.getMaximum(i));
            avg = avg.add(BigDecimal.valueOf(day.getAverage(i)));
        }
        setEntry(points, min, max,
                avg.divide(BigDecimal.valueOf(numberOfHoursInAggregate), BigDecimal.ROUND_DOWN).doubleValue());
    }

    /**
     * @return true if this dataset contains data up to and including the last day of the month, else false.
     *
     *         Note that this day various with the length of the month.
     */
    public boolean endsWithLastDayOfMonth() {
        if (isEmpty()) {
            return false;
        }
        OffsetDateTime time = Instant.ofEpochMilli(getTime(lastIndex())).atOffset(ZoneOffset.UTC);
        return time.getDayOfMonth() == time.with(TemporalAdjusters.lastDayOfMonth()).getDayOfMonth();
    }

    @Override
    public long getIntervalLength() {
        return MILLIS_IN_ONE_DAY;
    }
}
