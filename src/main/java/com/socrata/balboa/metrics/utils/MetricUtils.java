package com.socrata.balboa.metrics.utils;

import com.socrata.balboa.metrics.Summary;
import com.socrata.balboa.metrics.measurements.combining.Combinator;
import com.socrata.balboa.metrics.measurements.combining.Summation;
import com.socrata.balboa.metrics.measurements.serialization.JsonSerializer;
import com.socrata.balboa.metrics.measurements.serialization.Serializer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.util.*;

public class MetricUtils
{
    private static Log log = LogFactory.getLog(MetricUtils.class);

    public static Map<String, String> postprocess(Map<String, Object> map) throws IOException
    {
        Map<String, String> results = new HashMap<String, String>();
        Serializer serializer = new JsonSerializer();

        for (String key : map.keySet())
        {
            results.put(key, serializer.toString(map.get(key)));
        }
        return results;
    }

    public static Map<String, Object> summarize(Iterator<Summary> iter) throws IOException
    {
        int count = 0;

        Map<String, Object> results = new HashMap<String, Object>();
        Combinator com = new Summation();

        while (iter.hasNext())
        {
            Summary summary = iter.next();

            count += 1;

            for (String key : summary.getValues().keySet())
            {
                Object value = summary.getValues().get(key);
                results.put(key, com.combine(results.get(key), value));
            }
        }

        log.debug("Combined " + Integer.toString(count) + " columns.");

        return results;
    }

    /**
     * Merge two maps into one another and place the results in the first map.
     * The exclusion is just added to the first map while the intersection is
     * combined using the "Summation" combinator.
     *
     * @param first The first map and the map into which the results are placed
     * @param second The second map to merge.
     *
     * @return The resulting merged map.
     */
    public static Map<String, Object> merge(Map<String, Object> first, Map<String, Object> second)
    {
        // Get the union of the two key sets.
        Set<String> unionKeys = new HashSet<String>(first.keySet());
        unionKeys.addAll(second.keySet());

        Summation com = new Summation();

        // Combine the two maps.
        for (String key : unionKeys)
        {
            first.put(key, com.combine((Number)first.get(key), (Number)second.get(key)));
        }
        
        return first;
    }
}
