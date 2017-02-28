package junit.edu.umich.its.cpmtest;

import edu.umich.its.cpm.Utils;
import org.junit.*;

import java.util.HashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

//import junit.framework.TestCase;

//public class UtilsTest extends TestCase {
public class UtilsTest {

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

		//escape > <
		assertEquals(Utils.sanitizeName(Utils.COLLECTION_TYPE, "> test < folder"), "_ test _ folder");

		// URL as name
		assertEquals(Utils.sanitizeName(Utils.CTOOLS_RESOURCE_TYPE_CITATION,
				"http://www.npr.org/"), "http___www_npr_org_.html");

		// string without extension
		assertEquals(Utils.sanitizeName(Utils.CTOOLS_RESOURCE_TYPE_CITATION, "test_url"), "test_url.html");
		
		// URL as name, not ending in "/"
		assertEquals(Utils.sanitizeName(Utils.CTOOLS_RESOURCE_TYPE_CITATION, "http://www.npr.org"), "http___www_npr_org.html");
	}
	
	@Test
	public void testGetWebLinkContent() {
		try
		{
			// nice formed url
			assertEquals(Utils.getWebLinkContent("http://www.npr.org", "http://www.npr.org"), "<a href=\"http://www.npr.org\">http://www.npr.org</a>");
			
			//  url string missin http protocol
			assertNotEquals(Utils.getWebLinkContent("www.npr.org", "http://www.npr.org"), "<a href=\"http://www.npr.org\">http://www.npr.org</a>");
				
		}
		catch (java.net.MalformedURLException e)
		{
			// log the error
			System.out.println("java.net.MalforedURLException: testGetWebLinkContent with title www.npr.org " + e.getMessage());
		}
		
		try
		{
			// nice formed url
			assertNotEquals(Utils.getWebLinkContent("npr", "http://www.npr.org"), "<a href=\"http://www.npr.org\">http://www.npr.org</a>");
				
		}
		catch (java.net.MalformedURLException e)
		{
			// log the error
			System.out.println("java.net.MalforedURLException: testGetWebLinkContent with title npr " + e.getMessage());
		}
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
	
	@Test
	public void testupdateFolderNameMap() {
		// start with empty HashMap for folder names
		HashMap<String, String> folderNamesMap = new HashMap<String, String>();
		
		// if the title and current folder name matches, there should not be new entry inside the map
		folderNamesMap = Utils.updateFolderNameMap(folderNamesMap, "unchanged_folder_name", "unchanged_folder_name/");
		assertEquals(folderNamesMap.containsKey("unchanged_folder_name/"), false);
		
		// if the title and current folder name does not matches, 
		// there should not new entry inside the map
		folderNamesMap = Utils.updateFolderNameMap(folderNamesMap, "new_changed_folder_name", "changed_folder_name/");
		assertEquals(folderNamesMap.containsKey("changed_folder_name/"), true);
		assertEquals(folderNamesMap.get("changed_folder_name/"), "new_changed_folder_name/");
		
		// now that we have had an entry inside the map, 
		// we will test the subfolders
		// 1. if there is no title change for all folders in the path
		// there is no entry for this folder in map
		folderNamesMap = Utils.updateFolderNameMap(folderNamesMap, "subfolder_1", "unchanged_folder_name/subfolder_1/");
		assertEquals(folderNamesMap.containsKey("unchanged_folder_name/subfolder_1/"), false);
		
		// 2. if the subfolder DOES NOT have a new title, and its parent folder does not change title
		// there should be an entry inside the map for it
		folderNamesMap = Utils.updateFolderNameMap(folderNamesMap, "subfolder_1", "changed_folder_name/subfolder_1/");
		assertEquals(folderNamesMap.containsKey("changed_folder_name/subfolder_1/"), true);
		assertEquals(folderNamesMap.get("changed_folder_name/subfolder_1/"), "new_changed_folder_name/subfolder_1/");
		
		// 3. if the subfolder DOES have a new title, and its parent folder DOES change title
		// there should not be an entry inside the map for it
		folderNamesMap = Utils.updateFolderNameMap(folderNamesMap, "new_subfolder_1", "changed_folder_name/subfolder_1/");
		assertEquals(folderNamesMap.containsKey("changed_folder_name/subfolder_1/"), true);
		assertEquals(folderNamesMap.get("changed_folder_name/subfolder_1/"), "new_changed_folder_name/new_subfolder_1/");
		
		// 4. if the subfolder DOES have a new title, and its parent folder DOES NOT change title
		// there should be an entry inside the map for it
		folderNamesMap = Utils.updateFolderNameMap(folderNamesMap, "new_subfolder_2", "unchanged_folder_name/subfolder_2/");
		assertEquals(folderNamesMap.containsKey("unchanged_folder_name/subfolder_2/"), true);
		assertEquals(folderNamesMap.get("unchanged_folder_name/subfolder_2/"), "unchanged_folder_name/new_subfolder_2/");
		
		// now test if parent folder and child folder have the same title
		// change child folder name should only put child folder url into the map
		folderNamesMap = Utils.updateFolderNameMap(folderNamesMap, "test", "test/");
		folderNamesMap = Utils.updateFolderNameMap(folderNamesMap, "new_test", "test/test/");
		assertEquals(folderNamesMap.containsKey("test/"), false);
		assertEquals(folderNamesMap.containsKey("test/test/"), true);
		assertEquals(folderNamesMap.get("test/test/"), "test/new_test/");
		
		// now test the file name change
		// 1. there is a folder name change inside the file path
		String fileName = Utils.updateFolderPathForFileName("changed_folder_name/test.pdf", folderNamesMap);
		assertEquals(fileName, "changed_folder_name/test.pdf");

		// 2. there is NO folder name change inside the file path
		fileName = Utils.updateFolderPathForFileName("unchanged_folder_name/subfolder_1//test.pdf", folderNamesMap);
		assertEquals(fileName, "unchanged_folder_name/subfolder_1//test.pdf");
		
		// 3. top level file name should not change
		assertEquals("top.txt",Utils.updateFolderPathForFileName("top.txt", folderNamesMap));
	}

	@Test
	public void testUtilsGetWebLinkContent() {
		
		// url value does not need to be full url
		String fileName = "web link example";
		String url = "google.com";
		try
		{
			assertEquals("<a href=\"google.com\">web link example</a>", Utils.getWebLinkContent(fileName, url));
	
		} catch (java.net.MalformedURLException e)
		{
			System.out.println(this + " java.net.MalformedURLException " + url);
		}
	}

	@Test
	public void testMimeExtensionLogic(){
		assertEquals("A2_International HealthSystem.pdf",Utils.modifyFileNameOnType("application/pdf", "A2.International HealthSystem"));
		assertEquals("T3_0_  Training and faculty development at UM.pdf",Utils.modifyFileNameOnType("application/pdf", "T3_0.  Training and faculty development at UM"));
		assertEquals("Group 1.doc",Utils.modifyFileNameOnType("application/msword", "Group 1.doc"));
		//nonsensical MIME type with correct file extension
		assertEquals("Group 1.doc",Utils.modifyFileNameOnType("application/xword", "Group 1.doc"));
		assertEquals("Copy of F1_0_ Syllabus.doc",Utils.modifyFileNameOnType("application/msword", "Copy of F1_0. Syllabus"));
		assertEquals("A2 health systems.pdf",Utils.modifyFileNameOnType("application/pdf", "A2 health systems"));
		assertEquals("A2 health systems.txt",Utils.modifyFileNameOnType("text/plain", "A2 health systems"));
		assertEquals("A2_0_  Kinsella and Velkoff 2001 Chapter 4.bin",Utils.modifyFileNameOnType("application/octet-stream", "A2.0.  Kinsella and Velkoff 2001 Chapter 4"));
		assertEquals("A2_0_  Kinsella and Velkoff 2001 Chapter 4.html",Utils.modifyFileNameOnType("text/html", "A2.0.  Kinsella and Velkoff 2001 Chapter 4"));
		assertEquals("A2_0_  Kinsella and Velkoff 2001 Chapter 4.html",Utils.modifyFileNameOnType("text/url", "A2.0.  Kinsella and Velkoff 2001 Chapter 4"));
		assertEquals("A2-0-  Kinsella and Velkoff 2001 Chapter 4.html",Utils.modifyFileNameOnType("text/url", "A2-0-  Kinsella and Velkoff 2001 Chapter 4"));
		assertEquals("Using Web Dav on a Mac v1.txt",Utils.modifyFileNameOnType("text/plain; charset=us-ascii", "Using Web Dav on a Mac v1"));
		assertEquals("Using Web Dav on a Mac v1.bin",Utils.modifyFileNameOnType("application/octet-stream", "Using Web Dav on a Mac v1.bin"));
		assertEquals("Something in the fileName.ppt",Utils.guessedFileExtension("Application/x-PowerPoint", "Something in the fileName"));
		assertEquals("RedChicken and his friends.xls",Utils.guessedFileExtension("application/x-msexcel", "RedChicken and his friends"));
		assertEquals("progressive_jpeg.jpeg",Utils.guessedFileExtension("image/pjpeg", "progressive_jpeg"));
		assertEquals("Using Web Dav on a Mac v1.txt",Utils.guessedFileExtension("text/plain; charset=us-ascii", "Using Web Dav on a Mac v1"));

	}



}
