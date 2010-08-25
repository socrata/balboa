package com.socrata.balboa.server;

import com.socrata.balboa.metrics.config.Configuration;
import com.socrata.balboa.metrics.data.DateRange;
import com.socrata.balboa.metrics.messaging.Receiver;
import com.socrata.balboa.metrics.messaging.ReceiverFactory;
import com.socrata.balboa.server.exceptions.HttpException;
import com.socrata.balboa.server.exceptions.InternalException;
import com.socrata.balboa.server.exceptions.InvalidRequestException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.pojava.datetime.DateTime;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class BalboaServlet extends HttpServlet
{
    private static Log log = LogFactory.getLog(BalboaServlet.class);

    private static final long REQUEST_TIME_WARN_THRESHOLD = 2000;

    /**
     * Not used, but assigned so that the receiver doesn't get garbage
     * collected.
     */
    Receiver receiver;
    Thread writer;
    
    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
    {
        long startTime = System.currentTimeMillis();

        String requestInfo = "Servicing request '" + request.getPathInfo() + "'.\n";
        requestInfo +=  "\tParameters {\n";
        for (Object k : request.getParameterMap().keySet())
        {
            requestInfo += "\t\t" + k + " => " + request.getParameter((String)k) + "\n";
        }
        requestInfo += "\t}";
        log.info(requestInfo);
        
        // We're always JSON, no matter what.
        response.setContentType("application/json; charset=utf-8");

        if (!TimeZone.getDefault().equals(TimeZone.getTimeZone("UTC")))
        {
            throw new InternalException("Default timezone is not UTC so this " +
                    "request will not be serviced so our data stays consistent.");
        }

        try
        {
            String entityId = request.getPathInfo().replaceFirst("/", "");

            if (!"GET".equals(request.getMethod()))
            {
                throw new InvalidRequestException("Unsupported method '" + request.getMethod() + "'.");
            }

            log.debug("Request path info: " + request.getPathInfo());

            Object result = fulfillGet(entityId, ServiceUtils.getParameters(request));

            // Write the response out.
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
            mapper.writeValue(response.getOutputStream(), result);
        }
        catch (HttpException e)
        {
            // Write out any "expected" errors.
            log.warn("Unable to fullfil request because there was an HTTP error.", e);
            response.setStatus(e.getStatus());
            response.getOutputStream().write(e.getMessage().getBytes());
        }
        catch (Throwable e)
        {
            // Any other problems were things we weren't expecting.
            log.fatal("Unexpected exception handling a request.", e);
            response.setStatus(500);

            Map<String, Object> error = new HashMap<String, Object>();
            error.put("error", true);
            error.put("message", "Internal error.");
            
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
            mapper.writeValue(response.getOutputStream(), error);
        }
        finally
        {
            long requestTime = System.currentTimeMillis() - startTime;
            log.debug("Fulfilled request " + requestTime + " (ms)");

            if (requestTime > REQUEST_TIME_WARN_THRESHOLD)
            {
                log.warn("Slow request (" + request.getPathInfo() + ").");
            }
        }
    }

    @Override
    public void init(ServletConfig config) throws ServletException
    {
        super.init(config);

        // Force our timezone to always be UTC
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        try
        {
            // Initialize the configuration so that we set any log4j or external
            // configuration values properly.
            Configuration properties = Configuration.get();
            PropertyConfigurator.configure(properties);
        }
        catch (IOException e)
        {
            throw new ServletException("Unable to load the configuration.", e);
        }

        // Initialize our receiver and it will automatically connect.
        try
        {
            Runnable r = new Runnable() {
                @Override
                public void run()
                {
                    receiver = ReceiverFactory.get();
                }
            };

            writer = new Thread(r);
            writer.start();
        }
        catch (InternalException e)
        {
            log.warn("Unable to create an ActiveMQReceiver. New items will not be consumed.");
        }
    }

    Object range(String id, Map<String, String> params) throws InvalidRequestException, IOException
    {
        MetricsService service = new MetricsService();
        ServiceUtils.validateRequired(params, new String[] {"start", "end"});

        DateTime start = DateTime.parse(params.get("start"));
        DateTime end = DateTime.parse(params.get("end"));

        DateRange range = new DateRange(start.toDate(), end.toDate());

        if (params.containsKey("field"))
        {
            return service.range(id, (String)params.get("field"), range);
        }
        else if (params.containsKey("combine"))
        {
            String[] fields = ((String)params.get("combine")).split(",");
            return service.range(id, fields, range);
        }
        else
        {
            return service.range(id, range);
        }
    }

    Object single(String id, Map<String, String> params) throws InvalidRequestException, IOException
    {
        MetricsService service = new MetricsService();
        ServiceUtils.validateRequired(params, new String[] {"type", "date"});

        DateRange.Type type = DateRange.Type.valueOf(params.get("type"));
        DateTime date = DateTime.parse(params.get("date"));

        if (date == null)
        {
            throw new InvalidRequestException("Unrecognized date format '" + params.get("date") + "'.");
        }

        DateRange range = DateRange.create(type, date.toDate());

        if (params.containsKey("field"))
        {
            return service.get(id, type, (String)params.get("field"), range);
        }
        else if (params.containsKey("combine"))
        {
            String[] fields = ((String)params.get("combine")).split(",");
            return service.get(id, type, fields, range);
        }
        else
        {
            return service.get(id, type, range);
        }
    }

    Object series(String id, Map<String, String> params) throws InvalidRequestException, IOException
    {
        MetricsService service = new MetricsService();
        ServiceUtils.validateRequired(params, new String[] {"series", "start", "end"});

        DateRange.Type type = DateRange.Type.valueOf(params.get("series"));

        DateTime nominalStart = DateTime.parse(params.get("start"));
        DateTime nominalEnd = DateTime.parse(params.get("end"));

        DateRange range = new DateRange(
                DateRange.create(type, nominalStart.toDate()).start,
                DateRange.create(type, nominalEnd.toDate()).end
        );

        if (params.containsKey("field"))
        {
            return service.series(id, type, (String)params.get("field"), range);
        }
        else if (params.containsKey("combine"))
        {
            String[] fields = ((String)params.get("combine")).split(",");
            return service.series(id, type, fields, range);
        }
        else
        {
            return service.series(id, type, range);
        }
    }

    Object batch(Map<String, String> params) throws InvalidRequestException, IOException
    {
        ServiceUtils.validateRequired(params, new String[] {"ids"});

        String[] ids = params.get("ids").split(",");

        Map<String, Object> results = new HashMap<String, Object>();

        for (String id : ids)
        {
            results.put(id, fulfillGet(id, params));
        }

        return results;
    }

    Object fulfillGet(String id, Map<String, String> params) throws IOException, InvalidRequestException
    {
        if ("__batch__".equals(id))
        {
            return batch(params);
        }

        if (params.containsKey("series"))
        {
            return series(id, params);
        }
        else if (params.containsKey("range"))
        {
            return range(id, params);
        }
        else
        {
            return single(id, params);
        }
    }
}
