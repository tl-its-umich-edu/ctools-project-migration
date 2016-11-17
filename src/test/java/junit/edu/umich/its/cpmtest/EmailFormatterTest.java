package junit.edu.umich.its.cpmtest;

import edu.umich.its.cpm.AttachmentHandler;
import edu.umich.its.cpm.EmailFormatter;
import edu.umich.its.cpm.MailResultPair;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by pushyami on 8/17/16.
 */
public class EmailFormatterTest extends TestCase {
    Map<String, Object> messageSimple = null;
    EmailFormatter formatter;
    private EmailFormatter formatterForLargeAttachmentSizes;

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        HttpServletRequest request = null;
        AttachmentHandler attachmentHandler = new AttachmentHandler(request);
        attachmentHandler.setEnv(AttachmentHandlerTest.getMockEnvironment());
        //read json file data to String
        Path path = Paths.get(Paths.get(".").toAbsolutePath() + "/src/test/java/junit/edu/umich/its/cpmtest/message.json");
        byte[] jsonData = Files.readAllBytes(path);
        Path path1 = Paths.get(Paths.get(".").toAbsolutePath() + "/src/test/java/junit/edu/umich/its/cpmtest/message_1.json");
        byte[] jsonData1 = Files.readAllBytes(path1);

        formatter = new EmailFormatter(new String(jsonData, Charset.defaultCharset()), attachmentHandler);
        formatter.setEnv(AttachmentHandlerTest.getMockEnvironment());

        formatterForLargeAttachmentSizes = new EmailFormatter(new String(jsonData1, Charset.defaultCharset()), attachmentHandler);
        formatterForLargeAttachmentSizes.setEnv(AttachmentHandlerTest.getMockEnvironment());
    }


    @After
    public void tearDown() throws Exception {
    }

    public void testMessageBody() {
        String body = formatter.getBody(formatter.FORMAT_TYPE_RFC822);
        assertEquals("This is so awesome to find out and unbelievable", body);
    }

    public void testMessageHeader() {
        ArrayList<String> headers = formatter.prunedHeadersWithoutContentType();
        assertEquals(17, headers.size());
    }

    public void testAttachment() {
        HashMap<String, ArrayList<String>> attachments = formatter.getAttachments();
        assertEquals(1, attachments.size());
        Set<String> attachmentIDs = attachments.keySet();
        for (String id : attachmentIDs) {
            ArrayList<String> attachmentData = attachments.get(id);
            if (id.equals("234")) {
                assertEquals("text/plain", attachmentData.get(0));
                assertEquals("email_msg.txt", attachmentData.get(1));
                assertEquals("https://ctdevsearch.dsc.umich.edu/access/content/attachment/03f0e860-5d58-45a6-9226-bbcde27830c2/_anon_/dad9cffd-7aec-4896-83f5-23a4b2acacc3/email_msg.txt", attachmentData.get(2));
            }
        }
    }


    public void testFormattingMboxStartingHeader() {
        ArrayList<String> headers = formatter.prunedHeadersWithoutContentType();
        String actual = formatter.mboxStartingHeader(headers);
        String expected = "From jdoe@umich.edu Wed Aug 24 14:04:47 2016";
        assertEquals(expected, actual);
    }

    /*
    This test is ensuring that the starting header starts with "From" as that is required and don't want to test the
    large header list. Also testing number of mbox headers
     */
    public void testMboxFormatStartingHeaderInListOfHeaders() {
        ArrayList<String> mboxHeaders = formatter.mboxFormatHeaders();
        assertEquals("From jdoe@umich.edu Wed Aug 24 14:04:47 2016", mboxHeaders.get(0));
        assertEquals(18, mboxHeaders.size());
    }

    public void testSimpleMsgInRFCFormat() {
        String expected = expectedEmailTextValue().toString();
        MailResultPair emailTextPlusStatus = formatter.rfc822Format();
        String actualEmailText = emailTextPlusStatus.getMessage();
//        System.out.println(actualEmailText);
//        System.out.println("StatusReport: "+emailTextPlusStatus.getReport().getJsonReportObject().toString());
        assertEquals(expected, replaceTheBoundaryValue(actualEmailText));
    }


    public void testSimpleMsgInRFCFormatWithAttachmentSizeExceedingLimit() {
        MailResultPair emailTextPlusStatus = formatterForLargeAttachmentSizes.rfc822Format();
        String actual = emailTextPlusStatus.getMessage();
//        System.out.println(actual);
//        System.out.println("StatusReport: "+emailTextPlusStatus.getReport().getJsonReportObject().toString());
        assertEquals(expected(), replaceTheBoundaryValue(actual.trim()));
    }
    private  String expected(){
        StringBuilder expectedEmailText = new StringBuilder();
        expectedEmailText.append("Received: from localhost ([127.0.0.1]) by prefect.dsc.umich.edu (JAMES SMTP Server 2.3.2) with SMTP ID 920 for <mbox-2@ctdev.dsc.umich.edu>; Tue, 20 Sep 2016 12:59:30 -0400 (EDT)");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Received: from snowwhite.mr.itd.umich.edu (tl-nonprod-asb-nat1.dsc.umich.edu [141.211.192.39]) by prefect.dsc.umich.edu (Postfix) with ESMTP id 24761CB2 for <mbox-2@ctdev.dsc.umich.edu>; Tue, 20 Sep 2016 12:59:30 -0400 (EDT)");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Authentication-Results: snowwhite.mr.itd.umich.edu; iprev=pass policy.iprev=209.85.218.52 (mail-oi0-f52.google.com); spf=pass smtp.mailfrom=pushyami@umich.edu; dkim=pass header.d=@umich.edu; dmarc=pass header.from=pushyami@umich.edu");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Received: FROM mail-oi0-f52.google.com (mail-oi0-f52.google.com [209.85.218.52]) By snowwhite.mr.itd.umich.edu ID 57E16AEB.94005.395; Tue, 20 Sep 2016 12:59:23 -0400");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Received: by mail-oi0-f52.google.com with SMTP id r126so29242339oib.0 for <mbox-2@ctdev.dsc.umich.edu>; Tue, 20 Sep 2016 09:59:23 -0700 (PDT)");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/relaxed; d=umich.edu; s=google-2016-06-03; h=mime-version:from:date:message-id:subject:to; bh=VYJct9w+SZhk1u2A9ed2h9zI1xnv8bn0jew0VXnsL+o=; b=QQamvo3fHuV5dqh9NIaTNNRQNl6+jYkkOCC1EGyK7F5MU3eNBLuPo9QJ1nSTB9gntf UTcKh0F8fNKedILr+a/fHVbYsn0uGYVO5bwECcZe8iURxxLblaZZMDNGfwpOK30eDRfO Yttzs30pmujkmtccM51x64QtS1s+X6CGd2bM9tluG+7yWNMlPS7ILQ5+GKcq1qMpZXHk f/AeL8hfLTm9DTiDN27Yh3QaWs9ydthUCK3GgQpurBszizgSaHmNI1YHSjEo1kwPjpEo ESeMZSJaqVeH1eHq5Nu8coyKxgJsGhf9VysAReN9UVlEz6unLxFNt3l9DI4bO6GetL9g 7QKw==");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("X-Google-DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/relaxed; d=1e100.net; s=20130820; h=x-gm-message-state:mime-version:from:date:message-id:subject:to; bh=VYJct9w+SZhk1u2A9ed2h9zI1xnv8bn0jew0VXnsL+o=; b=WOfdpzoKJrra9nq/ua6qaA+YqrCqAKqqSpDAxB14z91oL0sla2icrY7STFqBgJZEq+ JzozHBXTYf19pdzFRlIOWKTTJihcVSHU3zZGuvj2hu55X75qE4CK+xlrHqMAZYXnn+q/ cKyoswplMDO7X5Ltk2zVXQNc0E60Sa7zdGLKHNuywTqOxN5dDfhAjp5+aDS1EEO9ufFF IEXuMarCsTXpwc0+QzwUshf8CmTcnb7YS2bHpXvaTrkLd+6lDmIjFvLPEgGZ60Oe/ijQ IZ0qezDRbBy/sW1WLZ935udRq+Ypt6ZexuhdgtHvw52O5m76YzRaOd8mKFopOJPzt8Nj 385g==");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("X-Gm-Message-State: AE9vXwNlUPJ9GDAQGDMS0tJsRksNGjNEcFWU84C2ED2Gyx6P3VlZYS6+AG5g+2UQB1bcb8HGd8X66OYZeCG1ZhKplnw=");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("X-Received: by 10.202.221.138 with SMTP id u132mr31622718oig.120.1474390702367; Tue, 20 Sep 2016 09:58:22 -0700 (PDT)");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("MIME-Version: 1.0");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Received: by 10.202.186.70 with HTTP; Tue, 20 Sep 2016 09:58:21 -0700 (PDT)");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("From: Pushyami Gundala <pushyami@umich.edu>");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Date: Tue, 20 Sep 2016 12:58:21 -0400");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Message-ID: <CAF8Wsuop8XDMKrjDMPqQEFH8L+EprUQRs=+pYGYzMN1K7BxowQ@mail.gmail.com>");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Subject: Attachment with 5mb size");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("To: mbox-2@ctdev.dsc.umich.edu");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("List-Id: <main.localhost>");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Content-Type: multipart/mixed; ");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("\tboundary=\"XXX\"");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("--XXX");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Content-Type: text/plain; charset=UTF-8");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Content-Transfer-Encoding: 7bit");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("This is Super");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("ATTACHMENTS THAT EXCEED THE SIZE LIMIT HAVE BEEN REMOVED");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("--XXX--");

        return expectedEmailText.toString();
    }


    public void testCheckMsgSizeMoreThanExpectedLimit() {
        boolean actual = false;
        MailResultPair emailMsgPlusStatus = formatterForLargeAttachmentSizes.rfcFormatWithoutAttachmentLimitCheck();
        String emailMessage = (String) emailMsgPlusStatus.getMessage();
        actual = formatterForLargeAttachmentSizes.checkMsgSizeMoreThanExpectedLimit(emailMessage);
        assertEquals(true, actual);

    }

    public void testStringInbetween() {
        Path path = Paths.get(Paths.get(".").toAbsolutePath() + "/src/test/java/junit/edu/umich/its/cpmtest/body_chunk.txt");
        Path path1 = Paths.get(Paths.get(".").toAbsolutePath() + "/src/test/java/junit/edu/umich/its/cpmtest/expected_subString_in_between.txt");
        byte[] data = null;
        byte[] data1 = null;
        try {
            data = Files.readAllBytes(path);
            data1 = Files.readAllBytes(path1);

        } catch (IOException io) {
            fail("Problem reading the file");
        }
        String actual = formatter.subStringInBetween(new String(data), "------=_Part_0_366590980.1473353467805");
        assertEquals(new String(data1), actual);

    }

    private StringBuilder expectedEmailTextValue() {
        StringBuilder expectedEmailText = new StringBuilder();
        expectedEmailText.append("Received: from localhost ([127.0.0.1]) by special.dsc.umich.edu (JAMES SMTP Server 2.3.2) with SMTP ID 505 for <pushyami-test@ctqa.dsc.umich.edu>; Wed, 24 Aug 2016 10:05:49 -0400 (EDT)");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Received: from snowwhite.mr.itd.umich.edu (tl-nonprod-asb-nat1.dsc.umich.edu [141.211.192.39]) by special.dsc.umich.edu (Postfix) with ESMTP id E6687D12 for <pushyami-test@ctqa.dsc.umich.edu>; Wed, 24 Aug 2016 10:05:48 -0400 (EDT)");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Authentication-Results: snowwhite.mr.itd.umich.edu; iprev=pass policy.iprev=209.85.218.51 (mail-oi0-f51.google.com); spf=pass smtp.mailfrom=pushyami@umich.edu; dkim=pass header.d=@umich.edu; dmarc=pass header.from=pushyami@umich.edu");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Received: FROM mail-oi0-f51.google.com (mail-oi0-f51.google.com [209.85.218.51]) By snowwhite.mr.itd.umich.edu ID 57BDA9BC.A4ED8.16927; Wed, 24 Aug 2016 10:05:48 -0400");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Received: by mail-oi0-f51.google.com with SMTP id l203so23580020oib.1 for <pushyami-test@ctqa.dsc.umich.edu>; Wed, 24 Aug 2016 07:05:48 -0700 (PDT)");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/relaxed; d=umich.edu; s=google-2016-06-03; h=mime-version:from:date:message-id:subject:to; bh=oUoLQKlJPbgYSuIbM0Q9NKsvPzE4kQZdoQ0tLeQTVMI=; b=dX39Sn3PsAWrj3XtgUH16uKd7wh9rKcMs3nnQItRtDrrUeCemm5JvGCqI2Ijof00qX B6c+QiYiLKqErYmPjcus0pV9NPtIOVHjzfny9NTdN/x4W2t5RuAA4/UWdFeQLwUNu7Ob sWhfYFEZ/NLzaM8zx3HKEmyWME9WjNVzghTn+7XiJUK8NZhjK8JdkmMo6riinpjUKOuo rTacFY9D+2dZiwmbmdLpKbGQpmuMtji6JMzWb+w5V8mQNqxwKgmJWu28IRvFMXClj6pZ luZnwaVneT8IJXS9IITvtviBsAiP3VVa+7jCZ5bxps8qoaCzrFk6+5OWbHaoYCeXI1Rx 2Okw==");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("X-Google-DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/relaxed; d=1e100.net; s=20130820; h=x-gm-message-state:mime-version:from:date:message-id:subject:to; bh=oUoLQKlJPbgYSuIbM0Q9NKsvPzE4kQZdoQ0tLeQTVMI=; b=CuOrgWbfv8oDieFegCYnQvsp2uxOA6LXziOhqluGFOn8FWh/ip7ZT7jbW9HcTWviZX Evhy/ZFZzArLWcPyrTYOJte3efc8DTifqJdTMJegk3NkzBEHdSNsnutv6Bhq6ixV6mBY Gbwd/6fRZqwmXfQQU7hRfALrHDU/ToM2M7ik9MhLIe549PwckpvMh0itX7Iq76e5gxw9 Bu3mbGpp6Baps5376n4nMoL2FABFdx3aoZzfEnzX3V8vd6vaJcVsIlB6BK90U1Z74WFz 4Tcn4gn9s0ZI+GL1V5L5WY8HsxfUjxx86c7lvDLP6gnLXgPGOa4/rUO4HbFeC3XMeX9C ac9Q==");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("X-Gm-Message-State: AE9vXwMpqaquA72LrOHQbYN5ihTcoMfjWfIDUjeG7pQusxFwQ2Kz8GfY9/UnJg1n/TtoVIuYOMdyOdA7F+motGbBOzU=");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("X-Received: by 10.157.4.79 with SMTP id 73mr2560093otc.29.1472047487817; Wed, 24 Aug 2016 07:04:47 -0700 (PDT)");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("MIME-Version: 1.0");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Received: by 10.202.86.196 with HTTP; Wed, 24 Aug 2016 07:04:47 -0700 (PDT)");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("From: Jane Doe <jdoe@umich.edu>");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Date: Wed, 24 Aug 2016 10:04:47 -0400");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Message-ID: <CAF8WsuqBzODBcc5-d_6yEgoyQhi13+bpLHjMNWP=g088YR=-5w@mail.gmail.com>");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Subject: I figured it out");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("To: pushyami-test@ctqa.dsc.umich.edu");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("List-Id: <main.localhost>");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Content-Type: multipart/mixed; ");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("\tboundary=\"XXX\"");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("--XXX");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Content-Type: text/plain; charset=UTF-8");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Content-Transfer-Encoding: 7bit");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("This is so awesome to find out and unbelievable");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("--XXX");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Content-Type: text/plain; charset=UTF-8; name=email_msg.txt");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Content-Transfer-Encoding: base64");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("Content-Disposition: attachment; filename=email_msg.txt");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("VGhpcyBpcyBhIHRlc3Qu");
        expectedEmailText.append(formatter.NEW_LINE);
        expectedEmailText.append("--XXX--");
        return expectedEmailText;
    }

    public String replaceTheBoundaryValue(String emailText) {
        int posA = emailText.indexOf("boundary=");
        String substring = emailText.substring(posA + 10);
        int endIndex = substring.indexOf("\"");
        String boundaryValue = substring.substring(0, endIndex);
        String editedEmailText = emailText.replaceAll(boundaryValue, "XXX").trim();
        return editedEmailText;
    }


}
