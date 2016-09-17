package junit.edu.umich.its.cpmtest;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.umich.its.cpm.MigrationTaskService;

public class MigrationTaskServiceTest {

	JSONObject jo = null;

	@Before
	public void setUp() throws Exception {
		jo = new JSONObject();
		jo.put("title","USE_TITLE");
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test_ExtractEmailSimple() {
		String email_addr = MigrationTaskService.extractEmailFromToHeader("To: HOWDY@HARVARD.edu");
		assertEquals("simple email","HOWDY",email_addr);
	}

	@Test
	public void test_ExtractEmailWithGreaterThan() {
		String email_addr = MigrationTaskService.extractEmailFromToHeader("To: <HOWDY@HARVARD.edu");
		assertEquals("email with leading <","HOWDY",email_addr);
	}

	@Test
	public void test_create_group_info_object_missing_value() {
		JSONObject gio = MigrationTaskService.create_group_info_object("INSITE", "ABBA", jo);
		assertNotNull("empty field ok",gio);
	}

}
