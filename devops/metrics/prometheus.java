import io.prometheus.client.Counter;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.exporter.HTTPServer;

//How will data be accessed here....

public class metricsExample {
    //change the strings in this
    static final Counter functionCounter = Counter.build().name("something").help("# of times something is called? registered? seen? ").register();

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

public class flukebook 
{

    static final Gauge species = Gauge.build()
        .name("numSpecies").help("Number of species in Flukebook.").register();
    static final Gauge users = Gauge.build()
        .name("numberOfUsers").help("Number of users in Flukebook.").register();
    static final Guage usersWLoginP = Gauge.build()
        .name("numberOfUsersWLoginP").help("Number of users with login privileges in Flukebook").register(); 
    static final Guage usersWOLoginP = Gauge.build()
        .name("numberOfUsersWOLoginP").help("Number of users with out login privileges in Flukebook").register(); 
    static final Guage usersActive = Gauge.build()
        .name("numberofUsersActive").help("Number of active users.").register(); 
    
}

//how do we connect this to the wildbook?
//Adding comment
