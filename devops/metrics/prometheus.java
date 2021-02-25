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
//Added by Sarah
public class MediaAssets
{
    static final Gauge mediaAssets = Gauge.build();
        .name("numMediaAssests").help("Number of Media Assets in Total.").reigster();
    //Seperate media assets for each wildbook possibly??
    static final Gauge assetsByWildbook = Gauge.build();
        .name("numMediaAssestsWB").help("Number of Media Assets by WildBook.").reigster();
    static final Gauge assetsBySpecies = Gauge.build();
        .name("numMediaAssestsSpecies").help("Number of Media Assets by species.").reigster();
}

public class BulkImports
{
    static final Gauge averageImportSize = Gauge.build();
        .name("avgImportSize").help("Average Size of all imports.").reigster();
    static final Gauge sizeImport = Gauge.build();
        .name("sizeOfImport").help("Size of Import").reigster();
    static final Gauge timeToBringIn = Gauge.build();
        .name("timeToBringInImports").help("Total Time to Bring in a set of imports.").reigster();
    static final Gauge numAnnotationsImport = Gauge.build();
        .name("numAnnotations").help("Number of Annotations from import.").reigster();
}

public class NumOfExceptions
{
    static final Gauge numofExceptions = Gauge.build();
        .name("numExceptions").help("Number of Exceptions from each book.").register();
}

public class InstanceOfMatching
{
    static final Counter newIndividuals = Gauge.build();
        .name("numIndiv").help("New Individual.").reigster();
    static final Counter existingIndividual = Gauge.build();
        .name("exisitingIndiv").help("Existing Individual.").reigster();
    static final Counter mergeIndividual = Gauge.build();
        .name("mergeIndivs").help("Merge Individuals.").reigster();
}
