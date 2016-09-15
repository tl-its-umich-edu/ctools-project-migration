package edu.umich.its.cpm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by pushyami on 8/17/16.
 */
public class EmailFormatter {
    private static final Logger log = LoggerFactory.getLogger(EmailFormatter.class);
    public static final String FROM_WITH_COLON = "From:";
    public static final String DATE_WITH_COLON = "Date:";
    public static final String NEW_LINE = "\r\n";
    public static final String BOUNDARY = "boundary=";
    public static final String FROM = "From ";
    public AttachmentHandler attachmentHandler;

    private String emailMessage;

    Map<String, Object> messageMap = null;

    public EmailFormatter(String emailMgs, AttachmentHandler attachmentHandler) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        this.emailMessage = emailMgs;
        this.attachmentHandler = attachmentHandler;
        this.messageMap = mapper.readValue(this.emailMessage, new TypeReference<HashMap<String, Object>>(){});
    }

    public String rfc822Format() {
        ArrayList<String> headers = prunedHeadersWithoutContentType();
        return getFormattedEmailText(headers).toString();
    }

    /*
      Mbox Format standard is defined by RFC4155 document and extends RFC2822 Standard.
      Mbox format email messages file must start like "From doe@eg.edu Wed Aug 24 14:04:47 2016" and
      each message is separated by blank line
     */
    public String mboxFormat() {
        ArrayList<String> headers = mboxFormatHeaders();
        return getMboxFormattedEmailText(headers);
    }

    private StringBuilder getFormattedEmailText(ArrayList<String> headers) {
        StringBuilder emailFormat = new StringBuilder();
        for (String header : headers) {
            emailFormat.append(header);
            emailFormat.append(NEW_LINE);
        }
        String bodyAndAttachment = emailTextWithBodyAndAttachment(getEmailText());
        emailFormat.append(bodyAndAttachment);
        emailFormat.append(NEW_LINE);
        return emailFormat;
    }

    private String getMboxFormattedEmailText(ArrayList<String> headers) {
        StringBuilder emailFormat = new StringBuilder();
        for (String header : headers) {
            emailFormat.append(header);
            emailFormat.append(NEW_LINE);
        }
        String bodyAndAttachment = emailTextWithBodyAndAttachment(getEmailText());
        ArrayList<String> pureBodyText = mboxBodyFormatting(bodyAndAttachment);
        String[] split = bodyAndAttachment.split(NEW_LINE);
        List<String> formattedBodyAndAttachment = Arrays.asList(split);
        for (int i=0;i<4;i++) {
            pureBodyText.add(i,formattedBodyAndAttachment.get(i));
        }

        for(int i=0; i<pureBodyText.size();i++){
            formattedBodyAndAttachment.set(i,pureBodyText.get(i));
        }
        for (String format: formattedBodyAndAttachment) {
            emailFormat.append(format);
            emailFormat.append(NEW_LINE);
        }
        return emailFormat.toString() ;
    }

    public ArrayList<String> mboxBodyFormatting(String bodyAndAttachment) {
        String[] split = bodyAndAttachment.split(NEW_LINE);
        String boundaryValue = null;
        for (String boyd : split) {
            // boundary="----=_Part_0_1537051719.1473704631686"
            if (boyd.contains(BOUNDARY)) {
                boyd = boyd.trim();
                boundaryValue = boyd.substring(boyd.indexOf(BOUNDARY) + 10, boyd.length() - 1);
                //as per RFC822 standard -- is prefixed for starting of Boundary
                boundaryValue = "--" + boundaryValue;
                break;
            }
        }
        String textBetweenBoundaryValues = subStringFrom(bodyAndAttachment, boundaryValue);
        String pureBodyText = subStringInBetween(textBetweenBoundaryValues, boundaryValue);
        String[] bodyTextSplit = pureBodyText.split(NEW_LINE);
        ArrayList<String> modifiedSplit = new ArrayList<String>();
        for (String body : bodyTextSplit) {
            if (body.startsWith(FROM)) {
                body = body.replaceFirst(FROM,">From ");
            }
            modifiedSplit.add(body);
        }

        return modifiedSplit;
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

    public ArrayList<String> mboxFormatHeaders() {
        ArrayList<String> headers = prunedHeadersWithoutContentType();
        String startingHeader = mboxStartingHeader(headers);
        headers.add(0, startingHeader);
        return headers;
    }

    public String mboxStartingHeader(ArrayList<String> headers) {
        String from = null;
        String date = null;
        StringBuilder startingHeader = new StringBuilder();
        for (String header : headers) {
            // From: Jane Doe <jdoe@umich.edu>
            if (header.startsWith(FROM_WITH_COLON)) {
                from = header.split(FROM_WITH_COLON)[1].trim();
                if (from.indexOf('<') != -1) {
                    from = from.substring(from.indexOf('<') + 1, from.indexOf('>'));
                }
            }
//             Date: Wed, 24 Aug 2016 10:04:47 -0400
            if (header.startsWith(DATE_WITH_COLON)) {
                String dateTemp = header.split(DATE_WITH_COLON)[1].trim();
                try {
                    DateFormat localTimeFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
                    Date localDateAndTime = localTimeFormat.parse(dateTemp);
                    long epochTime = localDateAndTime.getTime();
                    DateFormat ascTimePattern = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy");
                    ascTimePattern.setTimeZone(TimeZone.getTimeZone("UTC"));
                    // Wed Aug 24 14:04:47 2016
                    date = ascTimePattern.format(new Date(epochTime));
                } catch (java.text.ParseException e) {
                    log.error("Error occurred while paring the Date: "+dateTemp+ " due to" + e.getMessage());
                }

            }

            if (date == null) {
                DateFormat formatter = new SimpleDateFormat("EEE MMM dd HH:mm:ss yyyy");
                Date now = new Date();
                date = formatter.format(now);
            }

        }
        return startingHeader.append("From").append(" ").append(from).append(" ").append(date).toString();

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
            if (!body.isEmpty()) {
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
            log.error("Error occurred while generating the email stream" + e.getMessage());

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

    public String subStringInBetween(String emailText, String chopper) {
        String emailTextSubstring = emailText.substring(chopper.length());
        int i = emailTextSubstring.indexOf(chopper);
        String text = emailTextSubstring.substring(0, i);
        return text.trim();
    }

}
