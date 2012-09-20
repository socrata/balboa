package com.socrata.balboa.metrics.data.impl;

import com.socrata.balboa.metrics.Metric;
import com.socrata.balboa.metrics.Metrics;
import com.socrata.balboa.metrics.Timeslice;
import com.socrata.balboa.metrics.data.DataStore;
import com.socrata.balboa.metrics.data.DateRange;
import com.socrata.balboa.metrics.data.EntityMeta;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.*;

public class BufferedDataStoreTest {

    // Yeah. I know mockito exists. It's just sometimes too
    // much trouble. Or maybe not.
    class MockDataStore implements DataStore {
        Map<String, Metrics> metricMap = new HashMap<String, Metrics>();

        long persists = 0;
        public void persist(String entityId, long timestamp, Metrics metrics) throws IOException {
            metricMap.put(entityId, metrics);
            persists++;
        }
        public void onStart() {}
        public void onStop() {}
        public void heartbeat() {}
        public Iterator<String> entities(String pattern) throws IOException { throw new UnsupportedOperationException(); }
        public Iterator<String> entities() throws IOException { throw new UnsupportedOperationException(); }
        public EntityMeta meta(String entityId) throws IOException { throw new UnsupportedOperationException(); }
        public Iterator<Timeslice> slices(String entityId, DateRange.Period period, Date start, Date end) throws IOException { throw new UnsupportedOperationException(); }
        public Iterator<Metrics> find(String entityId, DateRange.Period period, Date date) throws IOException { throw new UnsupportedOperationException(); }
        public Iterator<Metrics> find(String entityId, DateRange.Period period, Date start, Date end) throws IOException { throw new UnsupportedOperationException(); }
        public Iterator<Metrics> find(String entityId, Date start, Date end) throws IOException { throw new UnsupportedOperationException(); }

    }

    class MockTimeService extends TimeService {
        long retVal = 0;
        public long currentTimeMillis() {
            return retVal;
        }
    }

    // Gah; Metrics merges are not immutable.
    Metrics getA() {
        Metrics A = new Metrics();
        A.put("fluffies", new Metric(Metric.RecordType.AGGREGATE, 1));
        A.put("bunnies", new Metric(Metric.RecordType.AGGREGATE, 2));
        A.put("growlies", new Metric(Metric.RecordType.AGGREGATE, 3));
        A.put("monkeys", new Metric(Metric.RecordType.ABSOLUTE, 999));
        return A;
    }

    Metrics getB() {
        Metrics B = new Metrics();
        B.put("fluffies", new Metric(Metric.RecordType.AGGREGATE, 1));
        B.put("growlies", new Metric(Metric.RecordType.AGGREGATE, 2));
        B.put("dinkies", new Metric(Metric.RecordType.AGGREGATE, 3));
        B.put("monkeys", new Metric(Metric.RecordType.ABSOLUTE, 888));
        B.put("peanuts", new Metric(Metric.RecordType.ABSOLUTE, 777));
        return B;
    }

    Metrics getAB() {
        Metrics AB = new Metrics();
        AB.merge(getA());
        AB.merge(getB());
        return AB;
    }


    @Test
    public void testHeartbeat() throws Exception {
        MockDataStore mockds = new MockDataStore();
        MockTimeService mockTime = new MockTimeService();
        BufferedDataStore bds = new BufferedDataStore(mockds, mockTime);
        mockTime.retVal = 1000;
        bds.heartbeat();
        bds.persist("one", 1000, getA());
        bds.persist("two", 1000, getA());
        bds.persist("two", 1000, getB());
        assertEquals(0, mockds.persists);
        mockTime.retVal = 1000 + bds.AGGREGATE_GRANULARITY;
        bds.heartbeat();
        assertEquals(2, mockds.persists);
    }

    @Test
    public void testPersist() throws Exception {
        MockDataStore mockds = new MockDataStore();
        MockTimeService mockTime = new MockTimeService();
        BufferedDataStore bds = new BufferedDataStore(mockds, mockTime);
        long ts = 100;

        bds.persist("one", ts, getA());
        bds.persist("two", ts, getA());
        bds.persist("two", ts, getB());
        assertEquals(0, mockds.persists);
        bds.persist("one", ts + bds.AGGREGATE_GRANULARITY, getB()); // should never be aggregated
        assertEquals(2, mockds.persists);
        assertEquals(getA().size(), mockds.metricMap.get("one").size());
        assertEquals(getA().get("fluffies"), mockds.metricMap.get("one").get("fluffies"));
        assertEquals(getAB().size(), mockds.metricMap.get("two").size());
        assertEquals(getAB().get("fluffies").getValue(), mockds.metricMap.get("two").get("fluffies").getValue());
        assertEquals(2, getAB().get("fluffies").getValue());
        assertEquals(2, mockds.metricMap.get("two").get("fluffies").getValue());
        assertEquals(getAB().get("bunnies").getValue(), mockds.metricMap.get("two").get("bunnies").getValue());
        assertEquals(getAB().get("dinkies").getValue(), mockds.metricMap.get("two").get("dinkies").getValue());
        assertEquals(getAB().get("growlies").getValue(), mockds.metricMap.get("two").get("growlies").getValue());
        assertEquals(getAB().get("monkeys").getValue(), mockds.metricMap.get("two").get("monkeys").getValue());
        //absolute value agg; should be covered in the MetricsTest
        assertEquals(888, mockds.metricMap.get("two").get("monkeys").getValue());
        assertEquals(777, mockds.metricMap.get("two").get("peanuts").getValue());
        assertEquals(2, getAB().get("fluffies").getValue());
    }

    @Test
    public void testMetricsFromThePast() throws Exception {
        MockDataStore mockds = new MockDataStore();
        MockTimeService mockTime = new MockTimeService();
        BufferedDataStore bds = new BufferedDataStore(mockds, mockTime);
        bds.persist("one", bds.AGGREGATE_GRANULARITY * 2, getA());
        bds.persist("one", bds.AGGREGATE_GRANULARITY * 2, getA()); // should be agg
        assertEquals(0, mockds.persists);

        // From the past!
        bds.persist("one", bds.AGGREGATE_GRANULARITY, getA()); // no agg
        assertEquals(1, mockds.persists);
        bds.persist("one", bds.AGGREGATE_GRANULARITY, getA()); // no agg
        assertEquals(2, mockds.persists);

        // Current time
        bds.persist("one", bds.AGGREGATE_GRANULARITY * 2, getA()); // should be agg
        assertEquals(2, mockds.persists);

        // From the Future!
        bds.persist("one", bds.AGGREGATE_GRANULARITY * 3, getA()); // should be agg
        assertEquals(3, mockds.persists);
    }

}
