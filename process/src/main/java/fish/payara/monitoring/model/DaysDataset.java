package fish.payara.monitoring.model;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;

public final class DaysDataset extends AggregateDataset<DaysDataset> {

    private static final int SINGLE_CAPACITY = 31;
    private static final int DOUBLE_CAPACITY = 2 * SINGLE_CAPACITY;

    private static final long MILLIS_IN_ONE_DAY = Duration.ofDays(1).toMillis();

    public static final DaysDataset EMPTY = new DaysDataset();

    private DaysDataset() {
        super();
    }

    private DaysDataset(DaysDataset predecessor, HoursDataset day) {
        super(predecessor, SINGLE_CAPACITY, offset(predecessor, day), firstTime(predecessor, day));
        aggregate(day);
    }

    private DaysDataset(DaysDataset predecessor, HoursDataset day, int newCapacity) {
        super(predecessor, newCapacity);
        aggregate(day);
    }

    private DaysDataset(HoursDataset day, DaysDataset predecessor) {
        super(predecessor);
        aggregate(day);
    }

    private static long firstTime(DaysDataset predecessor, HoursDataset day) {
        return predecessor.size() > 0
                ? predecessor.firstTime()
                : atStartOfDay(day).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli();
    }

    private static int offset(DaysDataset predecessor, HoursDataset day) {
        return predecessor.size() > 0
                ? predecessor.offset
                : dayOfMonth(day);
    }

    private static int dayOfMonth(HoursDataset day) {
        return atStartOfDay(day).getDayOfMonth();
    }

    private static OffsetDateTime atStartOfDay(HoursDataset day) {
        return Instant.ofEpochMilli(day.firstTime()).atOffset(ZoneOffset.UTC);
    }

    private void aggregate(HoursDataset day) {

    }

    /**
     * @return true if this dataset contains data up to and including the last day of the month, else false.
     *
     *         Note that this day various with the length of the month.
     */
    public boolean isEndOfMonth() {
        if (size() == 0) {
            return false;
        }
        OffsetDateTime time = Instant.ofEpochMilli(getTime(lastIndex())).atOffset(ZoneOffset.UTC);
        return time.getDayOfMonth() == time.with(TemporalAdjusters.lastDayOfMonth()).getDayOfMonth();
    }

    @Override
    public long getTime(int dayOfMonth) {
        int daysIn = dayOfMonth - offset;
        if (daysIn < 0) { // wrap
            daysIn = (capacity() - offset);
        }
        return firstTime() + (daysIn * MILLIS_IN_ONE_DAY);
    }

    @Override
    public long getIntervalLength() {
        return MILLIS_IN_ONE_DAY;
    }
}
