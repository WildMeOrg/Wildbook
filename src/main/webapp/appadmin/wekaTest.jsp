<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
java.io.*,java.util.*, weka.classifiers.Classifier,
weka.core.Instance,
weka.core.Attribute,
weka.core.DenseInstance, org.ecocean.ai.weka.Classify,weka.core.Instances,java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,org.ecocean.media.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>

<%!
public static Double classify(weka.core.Instance instance, String fullPathToClassifierFile) {
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
%>

<%

String context="context0";
context=ServletUtilities.getContext(request);





%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>

<ul>
<%

String positive="whaleshark guru expeditions at dive site koh tao";
if(request.getParameter("test")!=null){
	positive=request.getParameter("test");
};

String path="/data/whaleshark_data_dirs/shepherd_data_dir/wekaModels/youtubeRandomForest.model";
String locIDpath="/data/whaleshark_data_dirs/shepherd_data_dir/wekaModels/whaleSharkLocationIDClassifier.model";


ArrayList<Attribute> attributeList = new ArrayList<Attribute>(2);
ArrayList<Attribute> attributeList2 = new ArrayList<Attribute>(2);

Attribute merged = new Attribute("merged", true);
Attribute desc = new Attribute("description", true);


ArrayList<String> classVal = new ArrayList<String>();
classVal.add("good");
classVal.add("poor");

Shepherd myShepherd=new Shepherd(context);
myShepherd.beginDBTransaction();
List<String> classVal2 = myShepherd.getAllLocationIDs();
classVal2.remove(0);
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();

attributeList.add(merged);
attributeList.add(new Attribute("@@class@@",classVal));

attributeList2.add(desc);
attributeList2.add(new Attribute("@@class@@",classVal2));

Instances data = new Instances("TestInstances",attributeList,2);
data.setClassIndex(data.numAttributes()-1);

Instances data2 = new Instances("TestInstances",attributeList2,2);
data2.setClassIndex(data2.numAttributes()-1);

//pos
Instance pos = new DenseInstance(data.numAttributes());
Instance pos2 = new DenseInstance(data2.numAttributes());
//pos.setClassMissing();
pos.setValue(merged, positive);
data.add(pos);
pos.setDataset(data);

pos2.setValue(desc, positive);
data2.add(pos2);
pos2.setDataset(data2);




%>
<li><%=positive %>: <%=pos.classAttribute().value(classify(pos, path).intValue()) %></li>
<li><%=positive %>: <%=pos2.classAttribute().value(classify(pos2, locIDpath).intValue()) %></li>

</ul>

</body>
</html>
