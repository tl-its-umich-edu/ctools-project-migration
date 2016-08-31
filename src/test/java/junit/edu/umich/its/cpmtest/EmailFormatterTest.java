package junit.edu.umich.its.cpmtest;

import edu.umich.its.cpm.AttachmentHandler;
import edu.umich.its.cpm.EmailFormatter;
import junit.framework.TestCase;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Created by pushyami on 8/17/16.
 */
public class EmailFormatterTest extends TestCase {
    Map<String, Object> messageSimple = null;
    EmailFormatter formatter;

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
        formatter = new EmailFormatter(new String(jsonData, Charset.defaultCharset()), attachmentHandler);
    }


    @After
    public void tearDown() throws Exception {
    }

    public void testMessageBody() {
        String body = formatter.getBody();
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
            } else
                System.out.println("The Attachment Id's in \"message.json\" and in the \"testAttachment()\" is different. Hence the passing test");
        }
    }

    public void testSimpleMsgInRFCFormat() {
        StringBuilder expected = new StringBuilder();
        expected.append("Received: from localhost ([127.0.0.1]) by special.dsc.umich.edu (JAMES SMTP Server 2.3.2) with SMTP ID 505 for <pushyami-test@ctqa.dsc.umich.edu>; Wed, 24 Aug 2016 10:05:49 -0400 (EDT)");expected.append("\r\n");
        expected.append("Received: from snowwhite.mr.itd.umich.edu (tl-nonprod-asb-nat1.dsc.umich.edu [141.211.192.39]) by special.dsc.umich.edu (Postfix) with ESMTP id E6687D12 for <pushyami-test@ctqa.dsc.umich.edu>; Wed, 24 Aug 2016 10:05:48 -0400 (EDT)");expected.append("\r\n");
        expected.append("Authentication-Results: snowwhite.mr.itd.umich.edu; iprev=pass policy.iprev=209.85.218.51 (mail-oi0-f51.google.com); spf=pass smtp.mailfrom=pushyami@umich.edu; dkim=pass header.d=@umich.edu; dmarc=pass header.from=pushyami@umich.edu");expected.append("\r\n");
        expected.append("Received: FROM mail-oi0-f51.google.com (mail-oi0-f51.google.com [209.85.218.51]) By snowwhite.mr.itd.umich.edu ID 57BDA9BC.A4ED8.16927; Wed, 24 Aug 2016 10:05:48 -0400");expected.append("\r\n");
        expected.append("Received: by mail-oi0-f51.google.com with SMTP id l203so23580020oib.1 for <pushyami-test@ctqa.dsc.umich.edu>; Wed, 24 Aug 2016 07:05:48 -0700 (PDT)");expected.append("\r\n");
        expected.append("DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/relaxed; d=umich.edu; s=google-2016-06-03; h=mime-version:from:date:message-id:subject:to; bh=oUoLQKlJPbgYSuIbM0Q9NKsvPzE4kQZdoQ0tLeQTVMI=; b=dX39Sn3PsAWrj3XtgUH16uKd7wh9rKcMs3nnQItRtDrrUeCemm5JvGCqI2Ijof00qX B6c+QiYiLKqErYmPjcus0pV9NPtIOVHjzfny9NTdN/x4W2t5RuAA4/UWdFeQLwUNu7Ob sWhfYFEZ/NLzaM8zx3HKEmyWME9WjNVzghTn+7XiJUK8NZhjK8JdkmMo6riinpjUKOuo rTacFY9D+2dZiwmbmdLpKbGQpmuMtji6JMzWb+w5V8mQNqxwKgmJWu28IRvFMXClj6pZ luZnwaVneT8IJXS9IITvtviBsAiP3VVa+7jCZ5bxps8qoaCzrFk6+5OWbHaoYCeXI1Rx 2Okw==");expected.append("\r\n");
        expected.append("X-Google-DKIM-Signature: v=1; a=rsa-sha256; c=relaxed/relaxed; d=1e100.net; s=20130820; h=x-gm-message-state:mime-version:from:date:message-id:subject:to; bh=oUoLQKlJPbgYSuIbM0Q9NKsvPzE4kQZdoQ0tLeQTVMI=; b=CuOrgWbfv8oDieFegCYnQvsp2uxOA6LXziOhqluGFOn8FWh/ip7ZT7jbW9HcTWviZX Evhy/ZFZzArLWcPyrTYOJte3efc8DTifqJdTMJegk3NkzBEHdSNsnutv6Bhq6ixV6mBY Gbwd/6fRZqwmXfQQU7hRfALrHDU/ToM2M7ik9MhLIe549PwckpvMh0itX7Iq76e5gxw9 Bu3mbGpp6Baps5376n4nMoL2FABFdx3aoZzfEnzX3V8vd6vaJcVsIlB6BK90U1Z74WFz 4Tcn4gn9s0ZI+GL1V5L5WY8HsxfUjxx86c7lvDLP6gnLXgPGOa4/rUO4HbFeC3XMeX9C ac9Q==");expected.append("\r\n");
        expected.append("X-Gm-Message-State: AE9vXwMpqaquA72LrOHQbYN5ihTcoMfjWfIDUjeG7pQusxFwQ2Kz8GfY9/UnJg1n/TtoVIuYOMdyOdA7F+motGbBOzU=");expected.append("\r\n");
        expected.append("X-Received: by 10.157.4.79 with SMTP id 73mr2560093otc.29.1472047487817; Wed, 24 Aug 2016 07:04:47 -0700 (PDT)");expected.append("\r\n");
        expected.append("MIME-Version: 1.0");expected.append("\r\n");
        expected.append("Received: by 10.202.86.196 with HTTP; Wed, 24 Aug 2016 07:04:47 -0700 (PDT)");expected.append("\r\n");
        expected.append("From: Pushyami Gundala <pushyami@umich.edu>");expected.append("\r\n");
        expected.append("Date: Wed, 24 Aug 2016 10:04:47 -0400");expected.append("\r\n");
        expected.append("Message-ID: <CAF8WsuqBzODBcc5-d_6yEgoyQhi13+bpLHjMNWP=g088YR=-5w@mail.gmail.com>");expected.append("\r\n");
        expected.append("Subject: I figured it out");expected.append("\r\n");
        expected.append("To: pushyami-test@ctqa.dsc.umich.edu");expected.append("\r\n");
        expected.append("List-Id: <main.localhost>");expected.append("\r\n");
        expected.append("Content-Type: multipart/mixed; ");expected.append("\r\n");
        expected.append("\tboundary=\"XXX\"");expected.append("\r\n");expected.append("\r\n");
        expected.append("--XXX");expected.append("\r\n");
        expected.append("Content-Type: text/plain; charset=UTF-8");expected.append("\r\n");
        expected.append("Content-Transfer-Encoding: 7bit");expected.append("\r\n");expected.append("\r\n");
        expected.append("This is so awesome to find out and unbelievable");expected.append("\r\n");
        expected.append("--XXX");expected.append("\r\n");
        expected.append("Content-Type: text/plain; charset=UTF-8; name=email_msg.txt");expected.append("\r\n");
        expected.append("Content-Transfer-Encoding: base64");expected.append("\r\n");
        expected.append("Content-Disposition: attachment; filename=email_msg.txt");expected.append("\r\n");expected.append("\r\n");
        expected.append("VGhpcyBpcyBhIHRlc3Qu");expected.append("\r\n");
        expected.append("--XXX--");
        String emailText = formatter.rfc822Format();
        System.out.println(emailText);
        assertEquals(expected.toString(), replaceTheBoundaryValue(emailText));
    }

    public String replaceTheBoundaryValue(String emailText) {
        int posA = emailText.indexOf("boundary=");
        String substring= emailText.substring(posA + 10);
        int endIndex = substring.indexOf("\"");
        String boundaryValue = substring.substring(0, endIndex);
        String editedEmailText = emailText.replaceAll(boundaryValue, "XXX").trim();
        return editedEmailText;
    }


}
