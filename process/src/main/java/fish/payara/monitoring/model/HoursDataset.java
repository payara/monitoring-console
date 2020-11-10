package fish.payara.monitoring.model;

public final class HoursDataset extends AggregateDataset<HoursDataset> {

    public HoursDataset() {
        super(24);
    }

    public HoursDataset(HoursDataset predecessor, MinutesDataset hour) {
        super(predecessor, 0, 0);
    }

    private static int offset(HoursDataset predecessor, MinutesDataset hour) {
        return predecessor.length > 0
            ? predecessor.offset
            : minuteOfHour(minute);
    }

    private static long firstTime(HoursDataset predecessor, MinutesDataset hour) {
        return predecessor.length > 0
                ? predecessor.firstTime
                : lastDateTime(minute).withSecond(0).withNano(0).toInstant().toEpochMilli();
    }
}
