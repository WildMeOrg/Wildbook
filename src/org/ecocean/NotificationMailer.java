package org.ecocean;

import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;

/*
 * Sends out an email with a notification from this library.
 * This object is an independent thread.
 */
public class NotificationMailer implements Runnable{
	String to="", host="localhost", from="", subject="", text="";
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
		     message.addRecipients(Message.RecipientType.TO, to3);
		     message.setSubject(subject3);
		     message.setText(text3);
		     Transport.send(message);
			
		  }
			catch (Exception e) {e.printStackTrace();}
			
		}
	}
		

}