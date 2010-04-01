package org.ecocean;

//import org.dom4j.io.DOMReader;
//import org.dom4j.Document;
//import org.dom4j.DocumentException;
//import org.dom4j.io.SAXReader;
//import org.dom4j.Element;
import java.util.*;
//import java.io.*;
import javax.mail.*;
import javax.mail.internet.*;
//import javax.activation.*;


public class NotificationMailer implements Runnable{
	String to="webmaster@whaleshark.org", host="localhost", from="webmaster@whaleshark.org", subject="", text="";
	Vector images=new Vector();
	Properties props;
	Session session;
	MimeMessage message;
	public Thread mailerObject;

	/**Constructor to create a new shepherd thread object*/
	public NotificationMailer(String host, String from, String to, String subject, String text, Vector images) {
		mailerObject=new Thread(this, "NotificationMailer");
		this.host=host;
		this.to=to;
		this.subject=subject;
		this.text=text;
		this.images=images;
		this.from=from;
		props=System.getProperties();
		props.put("mail.smtp.host", host);
		session=Session.getDefaultInstance(props, null);
		message=new MimeMessage(session);
		mailerObject.start();
	}
		

		
	/**main method of the shepherd thread*/
	public void run() {
			sendIt(host, from, to, subject, text, images);
		}
		
		
	public void sendIt(String host3, String from3, String to3, String subject3, String text3, Vector images3) {
		if (!(host3.equals("None"))) {
		   try { 
		    //set up to, from, and the text of the message
			message.setFrom(new InternetAddress(from3));
			message.addRecipients(Message.RecipientType.BCC, to3);
			message.setSubject(subject3);
			message.setText(text3);
			//set up the attachments - later
			//if (images3.size()>0) {
				//for (int i=0;i<images3.size();i++) {
					//messageBodyPart=new MimeBodyPart();
					//DataSource source=new URLDataSource();
					//}
				
				//}
			//message.setContent(multi);
			
			
			//send the message!	
			Transport.send(message);
			
		   }
			catch (Exception e) {e.printStackTrace();}
			
			}
		}
		

}