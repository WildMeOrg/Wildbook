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
import java.util.StringTokenizer;

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
        
        
        String[] vals = metric.split(",(?=([^\"]*\"[^\"]*\")*[^\"]*$)");
        
        if(vals.length>3) {
        
          try {
              String m_key=vals[0].trim();
              String m_value=vals[1].trim();
              String m_type=vals[2].trim();
              String m_help=vals[3].trim();
              String m_labels=null;
              if(vals.length>4)m_labels=vals[4].trim();
              if(m_type.equals("gauge")) {
                
                //System.out.println("Loading gauge: "+m_key);
                
                if(m_labels!=null) {
                  
                  //preprocess labels
                  m_labels=m_labels.replaceAll("\"","");
                  ArrayList<String> labelNames=new ArrayList<String>();
                  ArrayList<String> labelValues=new ArrayList<String>();
                  StringTokenizer str=new StringTokenizer(m_labels,",");
                  while(str.hasMoreTokens()) {
                    String token=str.nextToken();
                    StringTokenizer str2=new StringTokenizer(token,":");
                    String name=str2.nextToken();
                    String value=str2.nextToken();
                    labelNames.add(name);
                    labelValues.add(value);
                  }

                  
                  //add label names and build gauge
                  
                  StringTokenizer str4=new StringTokenizer(labelNames.get(0),"_");
                  String prefix4= str4.nextToken();
    
                  Gauge g=Gauge.build().name(m_key).labelNames(prefix4).help(m_help).register();
                  //set gauge value overall
                  
                  
                  //add label values
                  for(int i=0;i<labelNames.size();i++) {
                    String name=labelNames.get(i);
                    String[] str3=name.split("_", 2);
                    //StringTokenizer str3=new StringTokenizer(name,"_");
                    String suffix=str3[1];
                    String value = labelValues.get(i);
                    if(name!=null && value!=null)g.labels(suffix).inc(new Double(value));
                    
                  }
                  
                  
                }
                //no labels, easy case!
                else{
                  Gauge.build().name(m_key).help(m_help).register().inc(new Double(m_value));
                }
                
              }
              /*
               //we don't support counters...yet
              else if(m_type.equals("counter")) {
                System.out.println("Loading counter: "+m_key);
                Counter.build().name(m_key)
                .help(m_help).register().inc(new Double(m_value));
              }
              */
          }
          catch(Exception e) {
            e.printStackTrace();
          }
          
        
        }
          
        
      }

    }
    
    
    
    
    
    
    
}


