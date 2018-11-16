package org.ecocean.ai.weka;


import java.util.List;
import java.util.ArrayList;
import weka.classifiers.Classifier;
import weka.core.Instance;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;


public class Classify {


    public static String getClassifierFileFullPath(String dataDir) {
        return dataDir + "/wekaModels/youtubeRandomForest.model";
    }

  /*
   * Assumes you're using a Weka FilteredClassifier that will do any necessary filter to the Instance (e.g., StringToWord) as a part of the classification.
   * 
   */
  public static Double classifyWithFilteredClassifier(weka.core.Instance instance, String fullPathToClassifierFile) {
    Double result=-1.0;

    try {
      // load classifier from file
      Classifier cls_co = (Classifier) weka.core.SerializationHelper.read(fullPathToClassifierFile);

      return new Double(cls_co.classifyInstance(instance));
    }
    catch(Exception e) {
      e.printStackTrace();
    }
    return result;   
  }

  /*
  //partially complete
  public static Double createNewModel(String classifyMe, String classifierName, String classifierOps, String filterName, String filterOps) {
    
    
    ArrayList<String> classifierOptions = new ArrayList<String>();
    ArrayList<String> filterOptions = new ArrayList<String>();
    
    Classifier m_Classifier = null;
    

    Filter m_Filter = null;


    String m_TrainingFile = null;


    Instances m_Training = null;

    Evaluation m_Evaluation = null;
    
    StringTokenizer strClassifier=new StringTokenizer(classifierOps,"-");
    while(strClassifier.hasMoreTokens()) {classifierOptions.add("-"+strClassifier.nextToken());}
    
    StringTokenizer strFilter=new StringTokenizer(filterOps,"-");
    while(strFilter.hasMoreTokens()) {filterOptions.add("-"+strFilter.nextToken());}
    
    try {
    
      //set classifier
      m_Classifier = AbstractClassifier.forName(classifierName, (String[])classifierOptions.toArray());
  
      //set filter
      m_Filter = (Filter) Class.forName(filterName).newInstance();
      if (m_Filter instanceof OptionHandler) ((OptionHandler) m_Filter).setOptions((String[])filterOptions.toArray());
      
      return new Double(m_Classifier.classifyInstance(myInstance));

    }
    
    catch(Exception e) {
      e.printStackTrace();
      return null;
    }
    finally {return null;}
   
    
    
    
  }
  */

  public static Instances makeInstance(List<String> attributesToClassifyNames, List<String> valuesToClassify, List<String> classValues) {

    //check for attribute names and values iin equal number
    if(attributesToClassifyNames.size()!=(valuesToClassify.size()))return null;
    
    int numAttributes=attributesToClassifyNames.size();
    
    ArrayList<Attribute> attributeList = new ArrayList<Attribute>(numAttributes);
    for(int i=0;i<numAttributes;i++) {
      Attribute merged = new Attribute(attributesToClassifyNames.get(i), true);
      attributeList.add(merged);
    }

    attributeList.add(new Attribute("@@class@@",classValues));

    Instances data = new Instances("TestInstances",attributeList,1);
    data.setClassIndex(data.numAttributes()-1);

    Instance weka_instance = new DenseInstance(data.numAttributes());
    data.add(weka_instance);
    weka_instance.setDataset(data);
    
    //set attribute values
    for(int i=0;i<numAttributes;i++) {
      weka_instance.setValue(attributeList.get(i), valuesToClassify.get(i));
    }
    
    return data;
    
  }
  
  


}
