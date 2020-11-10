package fish.payara.monitoring.model;

public abstract class AggregateDataset<T extends AggregateDataset<T>> {

    protected final long[] mins;
    protected final long[] maxs;
    protected final double[] avgs;
    protected final int[] points;
    protected final long firstTime;
    /**
     * Data on or after the offset index is in the {@link #firstTime} hour when recording started,
     * data before the offset index is in the hour after the one when recording started
     */
    protected final int offset;
    protected int length;

    public  AggregateDataset(int maxLength) {
        this.mins = new long[maxLength];
        this.maxs = new long[maxLength];
        this.avgs = new double[maxLength];
        this.points = new int[maxLength];
        this.firstTime = -1L;
        this.offset = 0;
        this.length = 0;
    }

    public AggregateDataset(AggregateDataset<T> predecessor, int offset, long firstTime) {
        this.mins = predecessor.mins;
        this.maxs = predecessor.maxs;
        this.avgs = predecessor.avgs;
        this.points = predecessor.points;
        this.offset = offset;
        this.length = predecessor.length + 1;
        this.firstTime = firstTime;
    }

    /**
     * @param index 0-59
     * @return The minimum value of all values recorded in the provided minute
     */
    public long getMinimum(int index) {
        return mins[index];
    }

    /**
     * @param index 0-59
     * @return The maximum value of all values recorded in the provided minute
     */
    public long getMaximum(int index) {
        return maxs[index];
    }

    /**
     * @param minuteOfHour 0-59
     * @return The average of all values recorded in the provided minute
     */
    public double getAverage(int minuteOfHour) {
        return avgs[minuteOfHour];
    }

    /**
     * @param minuteOfHour 0-59
     * @return The number of points recorded in the provided minute that were used to compute min, max and average values.
     */
    public int getNumberOfPoints(int minuteOfHour) {
        return points[minuteOfHour];
    }

    /**
     * @return The start of the minute when this {@link MinutesDataset} started to be filled with data
     */
    public long firstTime() {
        return firstTime;
    }

    /**
     * @return the number of minutes (in an hour) contained in this {@link MinutesDataset}, 0-60
     */
    public int length() {
        return length;
    }

    public int capacity() {
        return points.length;
    }
}
