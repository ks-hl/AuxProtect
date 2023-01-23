package dev.heliosares.auxprotect.utils;

import java.util.Arrays;

public class MovingAverage {
    public final double[] data;
    private int index = -1;
    private long age;

    public MovingAverage(int datapoints) {
        data = new double[datapoints];
    }

    public void reset() {
        for (int i = 0; i < data.length; i++) {
            data[i] = 0;
        }
        age = 0;
    }

    public int getIndex() {
        return index;
    }

    public void addData(double value) {
        index++;
        age++;
        if (index >= data.length) {
            index = 0;
        }
        data[index] = value;
    }

    public long getAge() {
        return age;
    }

    public double getMean() {
        double total = 0;
        int count = 0;
        for (int i = 0; i < data.length && i < age; i++) {
            total += data[i];
            count++;
        }
        return total / (double) count;
    }

    public double getMedian() {
        double[] data1 = Arrays.copyOf(data, data.length);
        Arrays.sort(data1);

        return data1[data1.length / 2];
    }

    public double getPk() {
        double max = 0;
        for (double part : data) {
            if (part > max) {
                max = part;
            }
        }
        return max;
    }

    public double getLast() {
        if (index < 0) {
            return 0;
        }
        return data[index];
    }

    public int getSize() {
        return data.length;
    }
}
