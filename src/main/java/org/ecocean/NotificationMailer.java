/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;
import java.util.Vector;

/*
 * Sends out an email with a notification from this library.
 * This object is an independent thread.
 */
public class NotificationMailer implements Runnable {
  String to = "", host = "localhost", from = "", subject = "", text = "";
  Vector images = new Vector();
  Properties props;
  Session session;
  MimeMessage message;
  public Thread mailerObject;
  String context="context0";

  /**
   * Constructor to create a new shepherd thread object
   */
  public NotificationMailer(String host, String from, String to, String subject, String text, Vector images,String context) {
    mailerObject = new Thread(this, "NotificationMailer");
    this.host = host;
    this.to = to;
    this.subject = subject;
    this.text = text;
    this.images = images;
    this.from = from;
    props = System.getProperties();
    props.put("mail.smtp.host", host);
    session = Session.getDefaultInstance(props, null);
    message = new MimeMessage(session);
    this.context=context;
  }


  /**
   * main method of the shepherd thread
   */
  public void run() {
    sendIt(host, from, to, subject, text, images);
    mailerObject=null;
  }


  public void sendIt(String host3, String from3, String to3, String subject3, String text3, Vector images3) {
	if(CommonConfiguration.sendEmailNotifications(context)){
		if (!(host3.equals("None"))) {
	      try {
	        //set up to, from, and the text of the message
	        message.setFrom(new InternetAddress(from3));
	        message.addRecipients(Message.RecipientType.TO, to3);
	        message.setSubject(subject3);
	        message.setText(text3);
	        Transport.send(message);

	      } catch (Exception e) {
	        e.printStackTrace();
	        System.out.println("     from: "+from3);
	        System.out.println("     to: "+to3);
	        mailerObject.interrupt();
	      }

	    }
	}
  }


}
