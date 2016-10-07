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
    public static final String SUBJECT_WITH_COLON = "Subject:";
    public AttachmentHandler attachmentHandler;

    public Environment getEnv() {
        return env;
    }

    public void setEnv(Environment env) {
        this.env = env;
    }

    @Autowired
    private Environment env;


    private String emailMessage;

    Map<String, Object> messageMap = null;

    public EmailFormatter(String emailMgs, AttachmentHandler attachmentHandler) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        this.emailMessage = emailMgs;
        this.attachmentHandler = attachmentHandler;
        this.messageMap = mapper.readValue(this.emailMessage, new TypeReference<HashMap<String, Object>>() {
        });
    }

    public MailResultPair rfc822Format() {
        MailResultPair emailMsgPlusStatus = rfcFormatWithoutAttachmentLimitCheck();
        String rfcFormattedText = emailMsgPlusStatus.getMessage();
        StatusReport status = emailMsgPlusStatus.getReport();

        // Something bad happened in formatting the email
        if (rfcFormattedText == null) {
            log.error("Email formatting is not successful");
            return emailMsgPlusStatus;
        }
        // Large email size check, if greater than proposed limit attachment will be dropped
        if (checkMsgSizeMoreThanExpectedLimit(rfcFormattedText)) {
            log.warn("Attachments are dropped for the message");
            rfcFormattedText = removeAttachments(rfcFormattedText);
            emailMsgPlusStatus.setMessage(rfcFormattedText);
            status.setStatus(Utils.STATUS_PARTIAL);
            status.setMsg("email Message size exceeds the expected limit, attachments "+status.getAllAttachments() +" are dropped");
            emailMsgPlusStatus.setReport(status);
            return emailMsgPlusStatus;
        }

        return emailMsgPlusStatus;
    }

    public MailResultPair rfcFormatWithoutAttachmentLimitCheck() {
        ArrayList<String> headers = prunedHeadersWithoutContentType();
        MailResultPair formattedEmailText = getFormattedEmailText(headers);
        return formattedEmailText;
    }

    public String removeAttachments(String rfcFormattedText) {
        String emailWithOutattachments = returnEmailTextDroppingAttachments(rfcFormattedText);
        return emailWithOutattachments;
    }

    private String returnEmailTextDroppingAttachments(String rfcFormattedText) {
        StringBuilder emailTextWithBodyNoAttachments = new StringBuilder();
        String attachmentRemovedText = subStringBefore(rfcFormattedText, "Content-Transfer-Encoding: base64");
        String[] msgBodyWithSomeExtra = attachmentRemovedText.split(NEW_LINE);
        List<String> emailTextList = new LinkedList(Arrays.asList(msgBodyWithSomeExtra));
        int size = emailTextList.size();
        emailTextList.remove(size - 1); // remove the last element in the list
        String lastItemInList = emailTextList.get(emailTextList.size() - 1) + "--"; // the replacing the new last element with new string
        emailTextList.set(emailTextList.size() - 1, lastItemInList);
        for (int i = 0; i < emailTextList.size(); i++) {
            if (i == (emailTextList.size() - 2)) {
                String lastLineInBody = emailTextList.get(i);
                String appendToBodyText = NEW_LINE + NEW_LINE +
                        "YOUR ATTACHMENTS ARE DROPPED DUE TO SIZE LIMIT";
                emailTextList.set(i, lastLineInBody + appendToBodyText);
            }
            emailTextWithBodyNoAttachments.append(emailTextList.get(i));
            emailTextWithBodyNoAttachments.append(NEW_LINE);
        }


        return emailTextWithBodyNoAttachments.toString();
    }

    /*
      Mbox Format standard is defined by RFC4155 document and extends RFC2822 Standard.
      Mbox format email messages file must start like "From doe@eg.edu Wed Aug 24 14:04:47 2016" and
      each message is separated by blank line
     */
    public MailResultPair mboxFormat() {
        ArrayList<String> headers = mboxFormatHeaders();
        MailResultPair mboxFormatEmailTextPlusStatus = getMboxFormattedEmailText(headers);
        return mboxFormatEmailTextPlusStatus;
    }

    private MailResultPair getFormattedEmailText(ArrayList<String> headers) {
        StringBuilder emailFormat = new StringBuilder();
        for (String header : headers) {
            emailFormat.append(header);
            emailFormat.append(NEW_LINE);
        }
        MailResultPair emailTextAndStatusObject = getEmailText();
        String emailText =  emailTextAndStatusObject.getMessage();
        if (emailText == null) {
            return emailTextAndStatusObject;
        }
        String bodyAndAttachment = emailTextWithBodyAndAttachment(emailText);
        emailFormat.append(bodyAndAttachment);
        emailFormat.append(NEW_LINE);
        emailTextAndStatusObject.setMessage(emailFormat.toString());
        return emailTextAndStatusObject;
    }

    public boolean checkMsgSizeMoreThanExpectedLimit(String rfc822FormatMessage) {
        long rfc822MgsSize = rfc822FormatMessage.length();
        log.debug("Email Message Size: " + rfc822MgsSize + " bytes");
        String attachLimit = env.getProperty(Utils.ENV_ATTACHMENT_LIMIT);
        log.debug("Attachment Size Limit : " + attachLimit + " bytes");
        long attachmentLimit = Long.parseLong(attachLimit);
        if (rfc822MgsSize > attachmentLimit) {
            log.info("The message with Id \"" + getItemId() + "\" is " +rfc822MgsSize+ " bytes exceed the expected limit that GGB can handle");
            return true;
        }
        return false;
    }

    private MailResultPair getMboxFormattedEmailText(ArrayList<String> headers) {
        StringBuilder emailFormat = new StringBuilder();
        for (String header : headers) {
            emailFormat.append(header);
            emailFormat.append(NEW_LINE);
        }
        MailResultPair emailTextAndStatusObject = getEmailText();
        String emailText = emailTextAndStatusObject.getMessage();
        if (emailText == null) {
            return emailTextAndStatusObject;
        }
        String bodyAndAttachment = emailTextWithBodyAndAttachment(emailText);
        ArrayList<String> pureBodyText = mboxBodyFormatting(bodyAndAttachment);
        String[] split = bodyAndAttachment.split(NEW_LINE);
        List<String> formattedBodyAndAttachment = Arrays.asList(split);
        for (int i = 0; i < 4; i++) {
            pureBodyText.add(i, formattedBodyAndAttachment.get(i));
        }

        for (int i = 0; i < pureBodyText.size(); i++) {
            formattedBodyAndAttachment.set(i, pureBodyText.get(i));
        }
        for (String format : formattedBodyAndAttachment) {
            emailFormat.append(format);
            emailFormat.append(NEW_LINE);
        }
        emailTextAndStatusObject.setMessage(emailFormat.toString());
        return emailTextAndStatusObject;
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
                body = body.replaceFirst(FROM, ">From ");
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
    public String getItemId(){
        ArrayList<String> headers = getHeaders();
        String date=null;
        String subject=null;
        String itemId;
        for (String header: headers) {
            if (header.startsWith(DATE_WITH_COLON)) {
                 date = header.split(DATE_WITH_COLON)[1].trim();
            }
            if(header.startsWith(SUBJECT_WITH_COLON)){
                 subject = header.split(SUBJECT_WITH_COLON)[1].trim();
            }
        }
        itemId=date+" "+subject;
        return itemId;
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
                    log.error("Error occurred while parsing the Date: " + dateTemp + " due to" + e.getMessage());
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

    public MailResultPair getEmailText() {
        Session session = null;
        MimeMessage message = new MimeMessage(session);
        String emailText = null;
        int attachmentFailureCount=0;
        StatusReport report=new StatusReport();
        report.setId(getItemId());
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
                    report.addAllAttachmnts(fileName);
                    byte[] fileContent = attachmentHandler.getAttachmentContent(attachmentUrl);
                    if (fileContent != null) {
                        msgBodyPart.setDataHandler(new DataHandler(new ByteArrayDataSource(fileContent, mimeType)));
                        msgBodyPart.setFileName(fileName);
                        msgBodyPart.addHeader("Content-Transfer-Encoding", "base64");
                        multipart.addBodyPart(msgBodyPart);
                    } else {
                        attachmentFailureCount++;
                        report.addFailedAttachments(fileName);
                    }
                }
            }

            message.setContent(multipart);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            message.writeTo(output);
            emailText = output.toString();

        } catch (MessagingException e) {
            String msg = "MessagingException occurred while generating the email stream";
            log.error(msg + e.getMessage());
            errHandlingInGetTextMail(report, msg);
            return  new MailResultPair(report,emailText);

        } catch (IOException ioe) {
            String msg = "IOException occurred while process writing the Mime message to stream";
            log.error(msg + ioe.getMessage());
            errHandlingInGetTextMail(report, msg);
            return new MailResultPair(report,emailText);
        } catch (Exception ioe) {
            String msg = "Expection occurred while generating a email stream";
            log.error(msg + ioe.getMessage());
            errHandlingInGetTextMail(report, msg);
            return new MailResultPair(report,emailText);
        }
        if(attachmentFailureCount>0){
            report.setStatus(Utils.STATUS_PARTIAL);
            report.setMsg(attachmentFailureCount+"/"+ getAttachments().size()+" attachments failed and they are " +StringUtils.join(report.getFailedAttachments())+" missing from the email");
            return new MailResultPair(report,emailText);
        }
        report.setStatus(Utils.STATUS_OK);
        report.setMsg(Utils.SUCCESS_MSG);
        return new MailResultPair(report,emailText);
    }

    private void errHandlingInGetTextMail(StatusReport report, String msg) {
        report.setMsg(msg);
        report.setStatus(Utils.STATUS_ERROR);
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

    public String subStringBefore(String emailText, String contentToChopFrom) {
        int posA = emailText.indexOf(contentToChopFrom);
        if (posA == -1) {
            return "";
        }
        return emailText.substring(0, posA);
    }

    public String subStringInBetween(String emailText, String chopper) {
        String emailTextSubstring = emailText.substring(chopper.length());
        int i = emailTextSubstring.indexOf(chopper);
        String text = emailTextSubstring.substring(0, i);
        return text.trim();
    }

}
