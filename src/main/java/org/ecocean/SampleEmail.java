package org.ecocean;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

public class SampleEmail {


    public  void sendEmail() throws Exception {

        String smtpUsername = "AKIAWK5CFOFM6IN2AVOK"; // From AWS SES SMTP credentials
        String smtpPassword = "BESuiE7HXSoYC4XPPqTU06uISDOL5sTlnnAuNJD5bQK5"; // From AWS SES SMTP credentials
        String fromEmail = "info@arguswild.ai";
        String toEmail = "xofvon@gmail.com";





        Properties props = new Properties();
        props.setProperty("mail.transport.protocol", "smtps");
        props.setProperty("mail.smtps.host", "email-smtp.us-east-1.amazonaws.com");
        props.setProperty("mail.smtps.port", "465");
        props.setProperty("mail.smtps.auth", "true");
        props.setProperty("mail.smtps.ssl.protocols", "TLSv1.2");
        props.setProperty("mail.debug", "true");

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(smtpUsername, smtpPassword);
            }
        });

        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(fromEmail));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
        message.setSubject("Test Email from AWS SES via JavaMail");
        message.setText("Hello, this is a test email sent through AWS SES!");

        Transport transport = session.getTransport();
        transport.connect();
        transport.sendMessage(message, message.getAllRecipients());
        transport.close();

        System.out.println("Email sent successfully!");
    }

    
}
