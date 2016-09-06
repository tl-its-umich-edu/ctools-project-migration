package edu.umich.its.cpm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import javax.activation.DataHandler;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by pushyami on 8/17/16.
 */
public class EmailFormatter {
    private static final Logger log = LoggerFactory.getLogger(EmailFormatter.class);
    public AttachmentHandler attachmentHandler;

    private String emailMessage;

    @Autowired
    private Environment env;

    Map<String, Object> messageMap = null;

    public EmailFormatter(String emailMgs, AttachmentHandler attachmentHandler) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        this.emailMessage = emailMgs;
        this.attachmentHandler = attachmentHandler;
        this.messageMap = mapper.readValue(this.emailMessage, new TypeReference<HashMap<String, Object>>(){});
    }

    public String rfc822Format() {
        StringBuilder emailInRFCFormat = new StringBuilder();
        ArrayList<String> headers = prunedHeadersWithoutContentType();
        for (String header : headers) {
            emailInRFCFormat.append(header);
            emailInRFCFormat.append("\r\n");
        }
        String bodyAndAttachment = emailTextWithBodyAndAttachment(getEmailText());
        emailInRFCFormat.append(bodyAndAttachment);
        return emailInRFCFormat.toString();
    }

    public String getBody() {
        return (String) messageMap.get("body");
    }

    public ArrayList<String> getHeaders() {
        ArrayList<String> headers = (ArrayList<String>) messageMap.get("headers");
        return headers;
    }
    //we are taking out all the headers that  contains "content-type" as these are again created again with
    // getEmailText() call
    public ArrayList<String> prunedHeadersWithoutContentType() {
        ArrayList<String> prunedHeaders = new ArrayList<String>();
        for (String header : getHeaders()) {
            if (!StringUtils.containsIgnoreCase(header, "content-type")) {
                prunedHeaders.add(header);
            }
        }
        return prunedHeaders;
    }

    public HashMap<String, ArrayList<String>> getAttachments() {
        HashMap<String, ArrayList<String>> attachmentMap = new HashMap<String, ArrayList<String>>();
        ArrayList<HashMap<String, Object>> attachments = (ArrayList<HashMap<String, Object>>) messageMap.get("attachments");
        for (HashMap<String, Object> attachment : attachments) {
            final String mimeType = (String) attachment.get("type");
            final String url = (String) attachment.get("url");
            final String name = (String) attachment.get("name");
            attachmentMap.put((String) attachment.get("id"), new ArrayList<String>() {{
                add(mimeType);
                add(name);
                add(url);
            }});
        }
        return attachmentMap;

    }

    /*
     We are using java mailing services for generating the emailText. generally the mail services complaint with RFC822
     Mime format.
     */
    public String getEmailText() {
        Session session = null;
        MimeMessage message = new MimeMessage(session);
        String emailText = null;
        try {
            Multipart multipart = new MimeMultipart();

            //Body of the email
            BodyPart msgBodyPart = new MimeBodyPart();
            String body = getBody();
            if(!body.isEmpty()) {
                msgBodyPart.setContent(body, "text/plain; charset=UTF-8");
                multipart.addBodyPart(msgBodyPart);
            }

            // attachment part of email
            HashMap<String, ArrayList<String>> attachments = getAttachments();
            if (!attachments.isEmpty()) {
                Set<String> attachmentIDs = attachments.keySet();
                for (String id : attachmentIDs) {
                    msgBodyPart = new MimeBodyPart();
                    ArrayList<String> attachmentMetaData = attachments.get(id); // [Mimetype, fileName, attachmentUrl]
                    String attachmentUrl = attachmentMetaData.get(2);
                    String mimeType = attachmentMetaData.get(0);
                    String fileName = attachmentMetaData.get(1);
                    byte[] fileContent = attachmentHandler.getAttachmentContent(attachmentUrl);
                    msgBodyPart.setDataHandler(new DataHandler(new ByteArrayDataSource(fileContent, mimeType)));
                    msgBodyPart.setFileName(fileName);
                    msgBodyPart.addHeader("Content-Transfer-Encoding", "base64");
                    multipart.addBodyPart(msgBodyPart);

                }

            }
            message.setContent(multipart);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            message.writeTo(output);
            emailText = output.toString();

        } catch (MessagingException e) {
            log.error("Error occurred while generating the email stream" +e.getMessage());

        } catch (IOException ioe) {
            log.error("Error occurred while process writing the Mime message as stream" + ioe.getMessage());

        }
        return emailText;
    }

    public String emailTextWithBodyAndAttachment(String emailText) {
        return subStringFrom(emailText, "Content-Type");
    }

    // Returns a substring containing all content starting from a specified string.
    public String subStringFrom(String emailText, String contentToChopFrom) {
        int posA = emailText.indexOf(contentToChopFrom);
        if (posA == -1) {
            return "";
        }
        return emailText.substring(posA);
    }


}
