<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,org.ecocean.ia.plugin.*,
org.joda.time.format.ISODateTimeFormat,java.net.*,org.json.JSONObject,org.apache.commons.io.*,
org.ecocean.grid.*,org.ecocean.media.*,org.ecocean.mmutil.*,org.ecocean.identity.IBEISIA,
java.io.*,java.util.*, 
java.util.concurrent.*,
java.io.FileInputStream, java.io.File, java.io.FileNotFoundException, org.ecocean.*,org.ecocean.servlet.*,javax.jdo.*, java.lang.StringBuffer, java.util.Vector, java.util.Iterator, java.lang.NumberFormatException"%>


<%

String context="context0";
context=ServletUtilities.getContext(request);

Shepherd myShepherd=new Shepherd(context);

int numFixes=0;

%>

<html>
<head>
<title>Fix Some Fields</title>

</head>


<body>


<%

int crCount=0;
int matchAgainst=0;
int mismatch=0;
int mmaCompatible=0;
int encHasMatchAgainst=0;
int mismatchEncs =0;

ArrayList<Annotation> annots = new ArrayList<Annotation>();

System.out.println("!!ZZZ0!!!: "+annots.size());

myShepherd.beginDBTransaction();


try{

	
	WildbookIAM wim = new WildbookIAM(context);
	
	
	
	
	String uuids="f1a52fcd-7c4c-4fc8-8f43-ddf84ed9d834,520e1ba1-a75c-4ac7-8b58-280159780083,6f1ff155-20e5-4139-b560-c24e1dc64ee0,75ab20d2-0c7b-47f9-b9a7-4a371fe41910,4f41866c-f3a3-4dbf-91e1-63bc1afbfcbc,61708627-152f-4a49-b67b-f58fd82c1b6e,a9a93e01-d097-4dbd-87f1-10c132f21c68,90581505-9df0-4c54-8ad0-4871299e2a48,506751d9-c29b-404e-a0e6-429ba7188b39,9e6fba52-5924-423b-8c05-23f02ade7e4b,d9ed5247-6a2c-4a0d-8bf1-77a0781444a6,eb95c6f1-6aba-47d3-a311-eb78390e56b3,e3328d65-1fdb-4239-ae5e-5006f63088e9,53d910eb-f949-4f66-a5bb-16ec5288271d,a0d6ae3b-0871-406d-9d76-f741a8a4a523,8096ecf4-f8c2-4502-84eb-678191588224,8c72c886-2857-429c-b67c-f3508026962e,ffc4ed0f-a234-4b88-91e1-49a6155a51da,034e2254-a9e5-4ebb-863e-f08362565c0f,66a795cd-9f87-411c-b47b-b41a8c06c9ac,1732fb10-b751-48d2-83df-53f9acac8d4c,912bcaef-1e3a-4e0a-a191-ef403d38645c,48383b3f-50e1-4ec0-8fbf-f0d488ce740c,24e5c861-b419-41be-b0fa-f379ba00e341,41ee5df8-77ba-41dc-a9b5-8c4b705ee6ba,4f752850-69d2-445f-954f-09833334d544,854be6f9-d578-4d6d-ab5b-335c0ed49d6d,d3b595ae-9bcc-45ef-b453-05c9ae92dcd7,d9bb15ef-a561-4a3d-8505-f41d236ac1fe,0807e4ac-dc23-44b3-9e1d-331aed64ef50,19e9d7c6-7637-4552-a6ca-824d9016ee7d,799e941c-3f04-4a1d-b22c-8cc2ce0ad27e,95aea353-0751-44e5-b84b-38b1701d07f6,f1878f14-c087-4227-89de-a347e3572166,494ebb84-6f3a-4365-a849-8f149a251f10,dea7cf07-2a99-45d3-91eb-adc2ebe50ef0,f978b089-17b5-4637-8181-d848869b1688,8dac8082-153f-47af-838f-43285aff0ce2,8b106400-310b-4e87-a2ae-6db7047ee292,a9938d31-a118-4b98-8aa1-4f89a01f0987,e5ebcb5a-50a7-4f6b-b4c1-7adf001af3f0,285f13d7-9bea-4619-8dc6-5e74e243430e,03037610-d26d-4ee9-b7d3-79f2fde8ff03,846ebc4c-a39d-44ee-8395-966e09f15127,466ddc99-3650-4c59-9046-aa5b72211600,33467876-857d-44e2-9abb-baed23dd5688,b451bfac-f21d-4fd4-8f13-d24e8851018b,5b56425f-0806-4a90-9907-e26594532f10,a34bca89-f3fa-46fb-8ca9-5b3bd63353ac,27576f1b-c1b7-47f0-afa9-4d9da3794d00,249ed6fe-c30e-4245-8b6e-2836b76f8f1e,a7fdde23-906a-436f-9ce1-139851078602,7dd89270-e3a1-4b65-9f84-a09c0ff54885,9d96c0c3-5c9c-4134-b303-a08728d5a212,ed9feb5f-5529-4d16-9f19-e1224b7145d6,06290620-ab07-43f9-8c79-dc59575796ba,de659c4d-6283-4c01-876e-92fe6463579c,91120e44-3ed0-4e86-8a9a-ff0cde7748cf,701795a0-b4bf-4cdb-b2db-0e4662a357ba,19682e65-e85f-4d55-b0fe-380d6df154f3,23839fa4-72f4-467c-897d-883d95138c46,f702f693-93fd-4f9a-bdf5-2e92d815553d,c8f0e19d-8226-4eda-90cf-08bc5ff15cb2,a254d3e5-e587-48df-a497-70ac9e73900d,a6d58f37-7ae0-45c3-8357-5d8599e9af37,ada19293-28da-4ef5-a39f-1881b9e56e52,20728af3-55fd-44e1-8f84-dba91c9bbfec,c7661fa1-48ec-4777-b085-2f42acc3aeab,140b01f6-2a2b-4d35-825d-924e3e5b8404,40a7b6b8-ec7a-46d3-ad6c-92bfb7e5bb64,32c26755-83e7-4767-beda-cef60fadbfea";
	
	
	StringTokenizer str=new StringTokenizer(uuids,",");
		
	while(str.hasMoreTokens()){
		String acmId=str.nextToken();
		ArrayList<Annotation> annotz=myShepherd.getAnnotationsWithACMId(acmId);
		for(Annotation annot:annotz){
			annot.setAcmId(null);
			myShepherd.updateDBTransaction();
			annots.add(annot);
		}
	}
	
	wim.sendAnnotations(annots, true, myShepherd);
	myShepherd.updateDBTransaction();
	
	
	
	
	/*
	ConcurrentHashMap<String, AnnotationLite> chm=AnnotationLite.getCache();
	System.out.println("!!ZZZ1!!!: "+annots.size());
	Enumeration<String> enums=chm.keys();
	System.out.println("!!ZZZ2!!!: "+annots.size());
	while(enums.hasMoreElements()){
		String annotID = enums.nextElement();
		System.out.println("!!ZZZa!!!: "+annots.size());
		Annotation annot=myShepherd.getAnnotation(annotID);
		
		if(annot.getAcmId()==null){
			annots.add(annot);
		}
		
	}
	*/
	
	System.out.println("!!ZZZ4!!!: "+annots.size());
	
	wim.sendAnnotations(annots, true, myShepherd);
	myShepherd.updateDBTransaction();
	
    
	myShepherd.rollbackDBTransaction();
	
}
catch(Exception e){
	e.printStackTrace();
	myShepherd.rollbackDBTransaction();
}
finally{
	myShepherd.closeDBTransaction();

}

%>
</ul>

</body>
</html>
