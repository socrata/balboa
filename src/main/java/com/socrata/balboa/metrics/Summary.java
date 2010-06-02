package com.socrata.balboa.metrics;

import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Summary
{
    private static Log log = LogFactory.getLog(Summary.class);

    public static enum Type
    {
        REALTIME,
        DAILY,
        WEEKLY,
        MONTHLY,
        YEARLY;

        @Override
        public String toString()
        {
            return this.name().toLowerCase();
        }
    };

    public Summary(long timestamp, Map<String, String> values)
    {
        this.timestamp = timestamp;
        this.values = values;
    }

    public long getTimestamp()
    {
        return timestamp;
    }

    public Type getType()
    {
        return type;
    }

    public Map<String, String> getValues()
    {
        return values;
    }

    long timestamp;
    Map<String, String> values;
    Type type;
}
