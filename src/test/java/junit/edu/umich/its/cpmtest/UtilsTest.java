package junit.edu.umich.its.cpmtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.HashMap;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

//import junit.framework.TestCase;
import edu.umich.its.cpm.Utils;

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
		assertEquals(fileName, "new_changed_folder_name/test.pdf");

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
	
}
