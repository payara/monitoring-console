package fish.payara.monitoring.model;

import static java.lang.System.arraycopy;

public abstract class AggregateDataset<T extends AggregateDataset<T>> {

    private final long[] mins;
    private final long[] maxs;
    private final double[] avgs;
    protected final int[] points;
    protected final long firstTime;
    /**
     * Data on or after the offset index is in the {@link #firstTime} hour when recording started,
     * data before the offset index is in the hour after the one when recording started
     */
    protected final int offset;
    protected int length;

    /**
     * Used to create a new empty dataset.
     *
     * @param capacity the initial capacity that should be equal to the number of points in the interval, for example 60
     *                 for minutes in an hour
     */
    protected AggregateDataset(int capacity) {
        this.mins = new long[capacity];
        this.maxs = new long[capacity];
        this.avgs = new double[capacity];
        this.points = new int[capacity];
        this.firstTime = -1L;
        this.offset = 0;
        this.length = 0;
    }

    /**
     * Used when appending a new point to a dataset of initial capacity.
     *
     * @param predecessor The dataset which has 1 datapoint less than this will have
     * @param offset      the offset of this dataset; this is the same as the one of the predecessor (if set) or the one
     *                    of the first datapoint
     * @param firstTime   the first time of this dataset; this is the same as the one of the predecessor (if set) or the
     *                    one of the first datapoint
     */
    protected AggregateDataset(AggregateDataset<T> predecessor, int offset, long firstTime) {
        this.mins = predecessor.mins;
        this.maxs = predecessor.maxs;
        this.avgs = predecessor.avgs;
        this.points = predecessor.points;
        this.offset = offset;
        this.length = predecessor.length + 1;
        this.firstTime = firstTime;
    }

    /**
     * Used when copying a section of the base dataset as a basis for a new sliding window dataset
     *
     * @param base The dataset which has the number of points that should be used as sliding window, all but the
     *                    oldest point of this will be copied as a basis for the created dataset
     * @param newCapacity must be reasonable larger then the length of the base dataset (usually 1.5 to 2 times
     *                    the length)
     */
    protected AggregateDataset(AggregateDataset<T> base, int newCapacity) {
        if (newCapacity <= base.length) {
            throw new IllegalArgumentException("Capacity must be larger than the sliding window size (length of base dataset)");
        }
        this.length = base.length; // length stays the same but we slide a window
        this.offset = base.length;
        this.mins = new long[newCapacity];
        this.maxs = new long[newCapacity];
        this.avgs = new double[newCapacity];
        this.points = new int[newCapacity];
        AggregateDataset<T> src = base;
        int copyLength = src.length - 1;
        int copyOffset = src.lastIndex() - copyLength;
        this.firstTime = base.getTime(copyOffset);
        arraycopy(src.mins, copyOffset, mins, 0, copyLength);
        arraycopy(src.maxs, copyOffset, maxs, 0, copyLength);
        arraycopy(src.avgs, copyOffset, avgs, 0, copyLength);
        arraycopy(src.points, copyOffset, points, 0, copyLength);
    }

    /**
     * Used when adding points to the sliding window.
     *
     * @param predecessor Must be an instance created with the copy constructor for new sliding windows
     */
    protected AggregateDataset(AggregateDataset<T> predecessor) {
        this.mins = predecessor.mins;
        this.maxs = predecessor.maxs;
        this.avgs = predecessor.avgs;
        this.points = predecessor.points;
        this.length = predecessor.length; // length stays the same but we slide a window
        this.offset = predecessor.offset + 1;
        this.firstTime = predecessor.getTime(predecessor.firstIndex() + 1);
    }

    protected final void setEntry(int points, long min, long max, double avg) {
        int index = lastIndex();
        this.points[index] = points;
        this.mins[index] = min;
        this.maxs[index] = max;
        this.avgs[index] = avg;
    }

    /**
     * @param index a value between {@link #firstIndex()} and {@link #lastIndex()} (inclusive)
     * @return The minimum value of all values recorded in the provided minute
     */
    public long getMinimum(int index) {
        return mins[index];
    }

    /**
     * @param index a value between {@link #firstIndex()} and {@link #lastIndex()} (inclusive)
     * @return The maximum value of all values recorded in the provided minute
     */
    public long getMaximum(int index) {
        return maxs[index];
    }

    /**
     * @param index a value between {@link #firstIndex()} and {@link #lastIndex()} (inclusive)
     * @return The average of all values recorded in the provided minute
     */
    public double getAverage(int index) {
        return avgs[index];
    }

    /**
     * @param index a value between {@link #firstIndex()} and {@link #lastIndex()} (inclusive)
     * @return The number of points recorded in the provided minute that were used to compute min, max and average values.
     */
    public int getNumberOfPoints(int index) {
        return points[index];
    }

    /**
     * @return The start of the minute when this {@link MinutesDataset} started to be filled with data
     */
    public long firstTime() {
        return firstTime;
    }

    /**
     * @return the number of data points in this set.
     *
     * For example the number of minutes in an hour (0-60)
     * or the numbers of hours in a day (0-24)
     */
    public int length() {
        return length;
    }

    /**
     * @return the total number of points that can be stored. This refers to the underlying shared array. Different
     *         instances of {@link AggregateDataset} instances reference to sections within the same array using
     *         {@link #offset} and {@link #length()}. While adding points writes to the array only so far unused cells
     *         are written and referenced. Once written the cell does not change.
     */
    public int capacity() {
        return points.length;
    }

    /**
     * @return Is the first index in the underlying array that is used by this dataset.
     *
     *         No index smaller than this must be passed to any of the methods accepting an index value.
     */
    public int firstIndex() {
        return offset;
    }

    /**
     * @return Is the last index in the underlying array that is used by this dataset.
     *
     *         No index larger than this must be passed to any of the methods accepting an index value.
     */
    public int lastIndex() {
        return offset + length;
    }

    /**
     * @param index a value between {@link #firstIndex()} and {@link #lastIndex()} (inclusive)
     * @return the timestamp value for the values of the given index.
     *
     *         This value is computed based on the {@link #firstTime()}, the {@link #firstIndex()} and the interval
     *         between data points which depends on the subclass.
     */
    public abstract long getTime(int index);

}
