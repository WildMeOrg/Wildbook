package org.ecocean.servlet.export;
import javax.servlet.*;
import javax.servlet.http.*;

import java.io.*;
import java.util.*;

import org.ecocean.*;

import java.lang.StringBuffer;

import javax.jdo.Query;

import org.springframework.mock.web.MockHttpServletRequest;
import org.ecocean.genetics.*;
import org.ecocean.servlet.ServletUtilities;

import java.net.URI;
import java.text.NumberFormat;;




//adds spots to a new encounter
public class GenePopExport extends HttpServlet{
  


  
  public void init(ServletConfig config) throws ServletException {
      super.init(config);
    }

  
  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException,IOException {
      doPost(request, response);
  }
    


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException{
    
    //set the response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    
    //get our Shepherd
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("GenePopExport.class");



    try{


      int numResults = 0;

      //set up the vector for matching encounters
      Vector query1Individuals = new Vector();
      Vector query2Individuals = new Vector();

      //kick off the transaction
      myShepherd.beginDBTransaction();

      //start the query and get the results
      String order = "";
      HttpServletRequest request1=(MockHttpServletRequest)request.getSession().getAttribute("locationSearch1");
    
      if((request!=null)&&(request1!=null)){
    
        MarkedIndividualQueryResult queryResult1 = IndividualQueryProcessor.processQuery(myShepherd, request1, order);
        //System.out.println(((MockHttpServletRequest)session.getAttribute("locationSearch1")).getQueryString());
        query1Individuals = queryResult1.getResult();
        int numSearch1Individuals = query1Individuals.size();
        
        MarkedIndividualQueryResult queryResult2 = IndividualQueryProcessor.processQuery(myShepherd, request, order);
        query2Individuals = queryResult2.getResult();
        int numSearch2Individuals = query2Individuals.size();
        
        //now let's start writing output
        
        //Line 1: write the title
        String additionalSearchString="";
        if((request.getParameter("searchNameField")!=null)&&(!request.getParameter("searchNameField").trim().equals(""))&&(request1.getParameter("searchNameField")!=null)&&(!request1.getParameter("searchNameField").trim().equals(""))){
          additionalSearchString=": "+request1.getParameter("searchNameField")+" vs. "+request.getParameter("searchNameField");
          
        }
        out.println("Search Comparison GenePop Export"+additionalSearchString+"<br />");
        
        //Lines 2+: write the loci
        //let's calculate Fst for each of the loci
        //iterate through the loci
        List<String> loci=myShepherd.getAllLoci();
        int numLoci=loci.size();
        for(int r=0;r<numLoci;r++){
          String locus=loci.get(r);
          out.println(locus+"<br />");
        }
        List<String> haplos=myShepherd.getAllHaplotypes();
        int numHaplos=haplos.size();
        if(numHaplos>0){
          out.println("mtDNA<br/>");
        }
        
        //now write out POP1 for search1
        
        out.println("POP"+"<br />");
        for(int i=0;i<numSearch1Individuals;i++){
          MarkedIndividual indie=(MarkedIndividual)query1Individuals.get(i);
          
          String lociString="";
          NumberFormat myFormat = NumberFormat.getInstance();
          myFormat.setMinimumIntegerDigits(3);
          for(int r=0;r<numLoci;r++){
            String locus=loci.get(r);
            ArrayList<Integer> values=indie.getAlleleValuesForLocus(locus);
            if(indie.getAlleleValuesForLocus(locus).size()==2){
              lociString+=myFormat.format(values.get(0));
              lociString+=myFormat.format(values.get(1))+" ";
            }
            else if(indie.getAlleleValuesForLocus(locus).size()==1){
              lociString+=myFormat.format(values.get(0));
              lociString+=myFormat.format(values.get(0))+" ";
            }
            else{lociString+="000000 ";}
            
          }
          
          if(numHaplos>0){
          //now add the haplotype
            if(indie.getHaplotype()!=null){
              String haplo=indie.getHaplotype();
              Integer haploNum = new Integer(haplos.indexOf(haplo)+1);
              lociString+=(myFormat.format(haploNum)+" ");
            }
            else{lociString+="000 ";}
          }
          
          String indieID=indie.getIndividualID();
          if(i==(numSearch1Individuals-1)){
            
            if((request1.getParameter("searchNameField")!=null)&&(!request1.getParameter("searchNameField").equals(""))){indieID=request1.getParameter("searchNameField");}
            else{indieID="Search1";}
            
          }
          out.println(indieID+","+" "+lociString+"<br />");
          
        }
        
        
        //now write out POP2 for search2
        out.println("POP"+"<br />");
        for(int i=0;i<numSearch2Individuals;i++){
          MarkedIndividual indie=(MarkedIndividual)query2Individuals.get(i);
          
          String lociString="";
          NumberFormat myFormat = NumberFormat.getInstance();
          myFormat.setMinimumIntegerDigits(3);
          for(int r=0;r<numLoci;r++){
            String locus=loci.get(r);
            ArrayList<Integer> values=indie.getAlleleValuesForLocus(locus);
            if(indie.getAlleleValuesForLocus(locus).size()==2){
              lociString+=myFormat.format(values.get(0));
              lociString+=myFormat.format(values.get(1))+" ";
            }
            else if(indie.getAlleleValuesForLocus(locus).size()==1){
              lociString+=myFormat.format(values.get(0));
              lociString+=myFormat.format(values.get(0))+" ";
            }
            else{lociString+="000000 ";}
            
          }
          
          if(numHaplos>0){
          //now add the haplotype
            if(indie.getHaplotype()!=null){
              String haplo=indie.getHaplotype();
              Integer haploNum = new Integer(haplos.indexOf(haplo)+1);
              lociString+=(myFormat.format(haploNum)+" ");
            }
            else{lociString+="000 ";}
          }
          
          String indieID=indie.getIndividualID();
          if(i==(numSearch2Individuals-1)){
            
            if((request.getParameter("searchNameField")!=null)&&(!request.getParameter("searchNameField").trim().equals(""))){indieID=request.getParameter("searchNameField");}
            else{indieID="Search2";}
            
          }
          out.println(indieID+","+" "+lociString+"<br />");
          
        }

        
      }
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();

    }
    catch(Exception e) {
      out.println("<p><strong>Error encountered</strong></p>");
      out.println("<p>Please let the webmaster know you encountered an error at: GenePopExport servlet.</p>");
      e.printStackTrace();
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
    }
    myShepherd=null;
    out.close();
    out=null;
  }

  
  }