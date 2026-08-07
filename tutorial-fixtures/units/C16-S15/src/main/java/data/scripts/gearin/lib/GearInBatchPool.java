package data.scripts.gearin.lib;

import java.util.ArrayDeque;
import java.util.Deque;

import org.boxutil.units.standard.attribute.Instance2Data;

public final class GearInBatchPool {
    private final Deque<Instance2Data> free = new ArrayDeque<>();

    public Instance2Data acquire() {
        Instance2Data data = free.pollFirst();
        return data == null ? new Instance2Data() : data;
    }

    public void release(Instance2Data data) {
        data.reset();
        free.addFirst(data);
    }

    public int available() {
        return free.size();
    }
}
