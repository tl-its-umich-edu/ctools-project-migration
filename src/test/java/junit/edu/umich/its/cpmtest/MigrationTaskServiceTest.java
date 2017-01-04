package junit.edu.umich.its.cpmtest;

import edu.umich.its.cpm.MigrationTaskService;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class MigrationTaskServiceTest {

	JSONObject jo = null;
	String emailArchive = null;
	String emailArchive1 = null;

	@Before
	public void setUp() throws Exception {
		jo = new JSONObject();
		jo.put("title","USE_TITLE");
		Path path = Paths.get(Paths.get(".").toAbsolutePath() + "/src/test/java/junit/edu/umich/its/cpmtest/sample_email_feed.json");
		byte[] jsonData = Files.readAllBytes(path);
		emailArchive = new String(jsonData);

		Path path1 = Paths.get(Paths.get(".").toAbsolutePath() + "/src/test/java/junit/edu/umich/its/cpmtest/sample_email_feed1.json");
		byte[] jsonData1 = Files.readAllBytes(path1);
		emailArchive1 = new String(jsonData1);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test_ExtractEmailSimple() {
		String email_addr = MigrationTaskService.extractEmailFromToHeader("To: HOWDY@ctdev.umich.edu");
		assertEquals("simple email","HOWDY",email_addr);
	}
	@Test
	public void test_ExtractEmailWithMultipleToAddress() {
		String email_addr = MigrationTaskService.extractEmailFromToHeader
				("To: \"Sara M. Mantila\" <msara@umich.edu>, steg_gradstudent <steg_gradstudent@ctools.umich.edu>,ctqa-mbox@ctqa.umich.edu");
		assertEquals("multiple email","steg_gradstudent",email_addr);
	}

	@Test
	public void test_ExtractEmailWithGreaterThan() {
		String email_addr = MigrationTaskService.extractEmailFromToHeader("To: <HOWDY@ctqa.umich.edu>");
		assertEquals("email with < >","HOWDY",email_addr);
	}

	@Test
	public void test_ExtractEmailWithOldTypicalCtoolsDomainName() {
		String email_addr = MigrationTaskService.extractEmailFromToHeader("To: \"HOWDY@HARVARD.edu\" <HOWDY@ctng.ummu.umich.edu>");
		assertEquals("HOWDY",email_addr);
	}

	@Test
	public void test_ExtractEmailForNotValidDomainNamesForEmailArchive() {
		String email_addr = MigrationTaskService.extractEmailFromToHeader("To: HOWDY Test Site <HOWDY@HARVARD.edu>");
		assertNull(email_addr);

		String email_addr1 = MigrationTaskService.extractEmailFromToHeader("To: HOWDY Test Site <HOWDY@umich.edu>");
		assertNull(email_addr1);
	}

	@Test
	public void test_ExtractEmailForBadName() {
		String email_addr = MigrationTaskService.extractEmailFromToHeader("To: undisclosed-recipients:;");
		assertNull(email_addr);
	}

	@Test
	public void test_create_group_info_object_missing_value() {
		JSONObject gio = MigrationTaskService.create_group_info_object("INSITE", "ABBA", jo);
		assertNotNull("empty field ok",gio);
	}

	@Test
    public void testIsCToolsDomainValid(){

		boolean ac2 = MigrationTaskService.isCToolsDomainValid("ctqa-mbox@ctools.umich.edu");
		assertEquals(true,ac2);

		boolean ac3 = MigrationTaskService.isCToolsDomainValid("ctqa-mbox@ctdev.umich.edu");
		assertEquals(true,ac3);

		boolean ac4 = MigrationTaskService.isCToolsDomainValid("ctqa-mbox@ctqa.umich.edu");
		assertEquals(true,ac4);

		boolean ac5 = MigrationTaskService.isCToolsDomainValid("1069863172027-577274@ctng.ummu.umich.edu");
		assertEquals(true,ac5);

		boolean ac9 = MigrationTaskService.isCToolsDomainValid("hai@ct.ummn.dsc.umich.edu");
		assertEquals(true,ac9);

		boolean actual = MigrationTaskService.isCToolsDomainValid("ctqa-mbox@umich.edu");
		assertEquals(false,actual);

		boolean ac6 = MigrationTaskService.isCToolsDomainValid("example@harvard.edu");
		assertEquals(false,ac6);

		boolean ac7 = MigrationTaskService.isCToolsDomainValid("hai@gmail.com");
		assertEquals(false,ac7);

		boolean ac8 = MigrationTaskService.isCToolsDomainValid("hai@ct.ummn.dsc.edu");
		assertEquals(false,ac8);

		boolean ac10 = MigrationTaskService.isCToolsDomainValid("To: undisclosed-recipients:;");
		assertEquals(false,ac10);
	}

	@Test
	public void testExtractEmailNameOutOfMultipleEmailIdInCaseOfBadEmailFeed(){
		String googleGroupName = MigrationTaskService.extractArchiveEmailName(emailArchive);
		assertEquals("cpmdev1",googleGroupName);

	}
	@Test
	public void testGoogleGroupsNamefromSiteIDWhenNoProperEmailIdIsInFeed(){
		String googleGroupName = MigrationTaskService.extractArchiveEmailName(emailArchive1);
		assertEquals("9bfee837-9b2c-4b6e-9e84-d77e9fbe10e9",googleGroupName);

	}


	@Test
	public void testCheckForDotsInFilename(){
		assertEquals("Group 1.doc",MigrationTaskService.replaceDotsInFileNameExceptFileExtention("Group 1.doc"));
		assertEquals("T1_0.  International health systems",MigrationTaskService.replaceDotsInFileNameExceptFileExtention("T1.0.  International health systems"));
		assertEquals("Copy of F1_0. Syllabus",MigrationTaskService.replaceDotsInFileNameExceptFileExtention("Copy of F1.0. Syllabus"));
		assertEquals("simply_long.properties",MigrationTaskService.replaceDotsInFileNameExceptFileExtention("simply.long.properties"));
	}

}
