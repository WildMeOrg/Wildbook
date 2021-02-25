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
    //Users
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

    //Encounters
    static final Guage encounters = Gauge.build()
        .name("numEncounters").help("Number of encounters.").register();
    static final Guage encountersInAgg = Gauge.build()
        .name("numEncountersInAgg").help("Number of encounters by species.").register();
    static final Guage encountersByWildbook = Gauge.build()
        .name("numEncountersByWildbook").help("Number of encounters by Wildbook.").register();
    static final Guage encountersBySpecies = Gauge.build()
        .name("numEncountersBySpecies").help("Number of encounters by species.").register();
    static final Guage encountersBySubmissionDate = Gauge.build()
        .name("numEncountersBySubmissionDate").help("Number of encounters by submission date.").register();                            
    static final Guage encountersByData = Gauge.build()
        .name("numEncountersByData").help("Number of encounters by encounter data.").register();
    static final Guage encountersByLocationID = Gauge.build()
        .name("numEncountersByLocationID").help("Number of encounters by location ID.").register();
    static final Guage encountersGPSCoords = Gauge.build()
        .name("encountersGPSCoords").help("Encounters' GPS coordinates.").register();
    static final Guage encounterGPSCoordsInAgg = Gauge.build()
        .name("encountersGPSCoordsInAgg").help("Encounters' GPS coordinates in aggregate.").register();
    static final Guage encountersGPSCoordsBySpecies = Gauge.build()
        .name("encountersGPSCoordsBySpecies").help("Encounters' GPS coordinates by species.").register(); 

    //Individuals 
    static final Guage individuals = Gauge.build()
        .name("numIndividuals").help("Number of individuals.").register();    
    static final Guage individualsInAgg = Gauge.build()
        .name("numIndividualsInAgg").help("Number of individuals in aggregate.").register();
    static final Guage individualsByWildbook = Gauge.build()
        .name("numIndividualsByWildbook").help("Number of individuals by Wildbook.").register();
    static final Guage individualsBySpecies = Gauge.build()
        .name("numIndividualsBySpecies").help("Number of individuals by species.").register();
    static final Guage individualsMaxYearsReSights = Gauge.build()
        .name("numIndividualsMaxYearsReSights").help("Number of individuals with max years between re-sights.").register();
    static final Guage individualsWithReSight = Gauge.build()
        .name("numIndividualsWithReSight").help("Number of individuals with at least one re-sight.").register(); 
           
}

//how do we connect this to the wildbook?

