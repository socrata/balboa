package com.socrata.balboa.agent;

import com.socrata.balboa.metrics.Metric;

/**
 * A Metrics Record is an immutable class that represents a metric
 * that occurred with a specific entity-id, name, value, type, and time.
 *
 * Created by michaelhotan on 2/2/16.
 */
public class MetricsRecord {

    private final String entityId;
    private final String name;
    private final Number value;
    private final Metric.RecordType type;
    private final long timestamp;

    public MetricsRecord(String entityId,
                         String name,
                         Number value,
                         long timestamp,
                         Metric.RecordType type)
    {
        this.entityId = entityId;
        this.name = name;
        this.value = value;
        this.timestamp = timestamp;
        this.type = type;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getName() {
        return name;
    }

    public Number getValue() {
        return value;
    }

    public Metric.RecordType getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MetricsRecord that = (MetricsRecord) o;

        if (timestamp != that.timestamp) return false;
        if (entityId != null ? !entityId.equals(that.entityId) : that.entityId != null) return false;
        if (name != null ? !name.equals(that.name) : that.name != null) return false;
        if (value != null ? !value.equals(that.value) : that.value != null) return false;
        return type == that.type;

    }

    @Override
    public int hashCode() {
        int result = entityId != null ? entityId.hashCode() : 0;
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (int) (timestamp ^ (timestamp >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "MetricsRecord{" +
                "entityId='" + entityId + '\'' +
                ", name='" + name + '\'' +
                ", value=" + value +
                ", type=" + type +
                ", timestamp=" + timestamp +
                '}';
    }
}