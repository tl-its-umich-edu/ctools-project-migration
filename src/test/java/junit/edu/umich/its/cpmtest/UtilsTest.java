package junit.edu.umich.its.cpmtest;

import static org.junit.Assert.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import junit.framework.TestCase;

import edu.umich.its.cpm.Utils;

public class UtilsTest extends TestCase {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testSanitizeName() {
		// escape back slash
		assertEquals(Utils.sanitizeName(Utils.COLLECTION_TYPE, "test \\ folder"), "test _ folder");
		
		// escape forward slash
		assertEquals(Utils.sanitizeName(Utils.COLLECTION_TYPE, "test / folder"), "test _ folder");
		
		// escape : 
		assertEquals(Utils.sanitizeName(Utils.COLLECTION_TYPE, "test : folder"), "test _ folder");
		
		// URL as name
		assertEquals(Utils.sanitizeName(Utils.CTOOLS_RESOURCE_TYPE_CITATION,
				"http://www.npr.org/"), "http___www.npr.org_.html");

		// string without extension
		assertEquals(Utils.sanitizeName(Utils.CTOOLS_RESOURCE_TYPE_CITATION, "test_url"), "test_url.html");
		
		// URL as name, not ending in "/"
		assertEquals(Utils.sanitizeName(Utils.CTOOLS_RESOURCE_TYPE_CITATION, "http://www.npr.org"), "http___www.npr.org.html");
	}
	
	@Test
	public void testGetWebLinkContent() {
		// escape back slash
		assertEquals(Utils.getWebLinkContent("http://www.npr.org", "http://www.npr.org"), "<a href=\"http://www.npr.org\">http://www.npr.org</a>");

	}

	@Test
	public void testGetCopyrightAcceptUrl() {
		// copyright alert is set to true
		assertEquals(
				Utils.getCopyrightAcceptUrl(
						"True",
						"https://example.edu/access/content/group/821918a5-c69f-48ca-aa82-3c4b40ac5c91/License%20Keys/Kaspersky-US%20Keys.zip"),
				"https://example.edu/access/accept?ref=/content/group/821918a5-c69f-48ca-aa82-3c4b40ac5c91/License%20Keys/Kaspersky-US%20Keys.zip&url=/content/group/821918a5-c69f-48ca-aa82-3c4b40ac5c91/License%20Keys/Kaspersky-US%20Keys.zip");

		// copyright alert is set to false
		assertEquals(
				Utils.getCopyrightAcceptUrl(
						"false",
						"https://example.edu/access/content/group/821918a5-c69f-48ca-aa82-3c4b40ac5c91/License%20Keys/Kaspersky-US%20Keys.zip"),
				"https://example.edu/access/accept?ref=/content/group/821918a5-c69f-48ca-aa82-3c4b40ac5c91/License%20Keys/Kaspersky-US%20Keys.zip&url=/content/group/821918a5-c69f-48ca-aa82-3c4b40ac5c91/License%20Keys/Kaspersky-US%20Keys.zip");

		// copyright alert is set to null
		assertEquals(
				Utils.getCopyrightAcceptUrl(
						null,
						"https://example.edu/access/content/group/821918a5-c69f-48ca-aa82-3c4b40ac5c91/License%20Keys/Kaspersky-US%20Keys.zip"),
				"https://example.edu/access/content/group/821918a5-c69f-48ca-aa82-3c4b40ac5c91/License%20Keys/Kaspersky-US%20Keys.zip");
	}

}
