import io.prometheus.client.Counter;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.exporter.HTTPServer;

public class metricsExample {
    //change the strings in this
    static final Counter functionCounter = Counter.build().name("my_function_calls_total").help("Number of times my_function was called").register();

    static void incrementCounter() 
    {
        functionCounter.inc();
    }

    public static void main(String[] args) throws Exception 
    {
        DefaultExports.initialize();
        HTTPServer server = new HTTPServer(8000);
        while (true) 
        {
            incrementCounter();
            Thread.sleep(1000);
        }
    }
}

//how do we connect this to the wildbook endpoints?

