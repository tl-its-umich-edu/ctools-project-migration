package junit.edu.umich.its.cpmtest;

import edu.umich.its.cpm.AttachmentHandler;
import edu.umich.its.cpm.Utils;
import junit.framework.TestCase;

import org.junit.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;

import javax.servlet.http.HttpServletRequest;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Created by pushyami on 8/26/16.
 */

public class AttachmentHandlerIntegrationTest extends TestCase {

    private AttachmentHandler attachmentHandler;

    @Autowired
    public Environment env;


    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setUp() throws Exception {
        HttpServletRequest request = null;
        attachmentHandler = new AttachmentHandler(request);
        attachmentHandler.setEnv(getMockEnvironment());

    }

    public static MockEnvironment getMockEnvironment() throws IOException {
        MockEnvironment mockEnv = new MockEnvironment();
        Properties props = getProps();
        mockEnv.setProperty("ctools.server.url", (String)props.get("ctools.server.url"));
        mockEnv.setProperty(Utils.ENV_PROPERTY_USERNAME, (String)props.get(Utils.ENV_PROPERTY_USERNAME));
        mockEnv.setProperty(Utils.ENV_PROPERTY_PASSWORD, (String)props.get(Utils.ENV_PROPERTY_PASSWORD));
        mockEnv.setProperty(Utils.ENV_PROPERTY_ATTACHMENT_LIMIT, (String)props.get(Utils.ENV_PROPERTY_ATTACHMENT_LIMIT));
        return mockEnv;
    }

    @After
    public void tearDown() throws Exception {
    }

    public static Properties getProps() throws IOException {
        Properties props = new Properties();
        InputStream input = new FileInputStream(Paths.get(".").toAbsolutePath()+"/src/test/java/junit/edu/umich/its/cpmtest/test.properties");
        props.load(input);
        input.close();
        return props;
    }


    public void testAttachment() {
        String attachmentUrl = "https://ctdevsearch.dsc.umich.edu/access/content/attachment/03f0e860-5d58-45a6-9226-bbcde27830c2/_anon_/dad9cffd-7aec-4896-83f5-23a4b2acacc3/email_msg.txt";
        String attachmentContent = new String(attachmentHandler.getAttachmentContent(attachmentUrl));
        assertEquals("This is a test.", attachmentContent);

    }

}
