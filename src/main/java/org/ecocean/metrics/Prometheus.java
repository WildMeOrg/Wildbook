package org.ecocean.metrics;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.ecocean.MetricsBot;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;

public class Prometheus
{
    
    
    private String context="context0";    
    
    static String cvsSplitBy = ",";
    
    //Default constructor
    public Prometheus()
    {
      
  
    }
    
    public Prometheus(String context)
    {
      this.context=context;
  
    }

    //Unit test constructor
    public Prometheus(boolean isTesting)
    {
      
    }
    
    /** Implementation borrowed from MetricsServlet class
    * Parses the default collector registery into the kind of 
    * output that prometheus likes
    * Visit https://github.com/prometheus/client_java/blob/master/simpleclient_servlet/src/main/java/io/prometheus/client/exporter/MetricsServlet.java
    */
    public void metrics(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
      Writer writer = new BufferedWriter(response.getWriter());
      response.setStatus(HttpServletResponse.SC_OK);
      String contentType = TextFormat.chooseContentType(request.getHeader("Accept"));
      response.setContentType(contentType);
      try
      {
        TextFormat.writeFormat(contentType, writer, CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(parse(request)));
        writer.flush();
      }
      finally
      {
        writer.close();
      }
    }
    
    //Helper method for metrics() also borrowed from MetricsServlet.java
    private Set<String> parse(HttpServletRequest req)
    {
      String[] includedParam = req.getParameterValues("name[]");
      if(includedParam == null)
      {
        return Collections.emptySet();
      }
      else
      {
        return new HashSet<String>(Arrays.asList(includedParam));
      }
    }
    
 
    
    public static String getValue(String key) {
      if(key==null)return null;
      String value=null;
      BufferedReader br = null;
      String line = "";
      
      try {

        br = new BufferedReader(new FileReader(MetricsBot.csvFile));
        while ((line = br.readLine()) != null) {

            // use comma as separator
            String[] vals = line.split(cvsSplitBy);
            if(vals[0]!=null && vals[1]!=null){
              String m_key=vals[0];
              String m_value=vals[1];

              if(m_key.trim().equals(key)) {value=m_value;} 
            }
        }        
      } 
      catch (FileNotFoundException e) {
          e.printStackTrace();
          System.out.println("Metrics failed to load value "+key+" because I could not find file: "+MetricsBot.csvFile);
      } 
      catch (IOException e) {
          e.printStackTrace();
      } 
      finally {
          if (br != null) {
              try {
                  br.close();
              } catch (IOException e) {
                  e.printStackTrace();
              }
          }
      }
      return value;
    }
    
    public void getValues() {
      
      
      
      ArrayList<String> metrics=new ArrayList<String>();
      
      BufferedReader br = null;
      String line = "";
      try {

        br = new BufferedReader(new FileReader(MetricsBot.csvFile));
        while ((line = br.readLine()) != null) {

          metrics.add(line);
            
        }        
      } 
      catch (FileNotFoundException e) {
          e.printStackTrace();
          System.out.println("Metrics failed to load because I could not find file: "+MetricsBot.csvFile);
      } 
      catch (IOException e) {
          e.printStackTrace();
      } 
      finally {
          if (br != null) {
              try {
                  br.close();
              } catch (IOException e) {
                  e.printStackTrace();
              }
          }
      }
      
      for(String metric:metrics) {
        
        
        String[] vals = metric.split(cvsSplitBy);
        
        if(vals.length>3) {
        
          try {
              String m_key=vals[0];
              String m_value=vals[1];
              String m_type=vals[2];
              String m_help=vals[3];
              System.out.println("Loading: "+m_key+","+m_value+","+m_type+","+m_help);
              if(m_type.trim().equals("gauge")) {
                System.out.println("Loading gauge: "+m_key);
                Gauge.build().name(m_key.trim())
                .help(m_help.trim()).register().inc(new Double(m_value.trim()));
              }
              else if(m_type.trim().equals("counter")) {
                System.out.println("Loading counter: "+m_key);
                Counter.build().name(m_key.trim())
                .help(m_help.trim()).register().inc(new Double(m_value.trim()));
              }
          }
          catch(Exception e) {
            e.printStackTrace();
          }
          
        
        }
          
        
      }

    }
    
    
    
    
    
    
    
}


