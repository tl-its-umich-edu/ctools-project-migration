package junit.edu.umich.its.cpmtest;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;

import javax.mail.internet.MailDateFormat;

import static org.apache.http.HttpStatus.*;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umich.its.cpm.ApiResultWrapper;
import edu.umich.its.cpm.GGBApiWrapper;

public class GGBApiWrapperTest {

	static final Logger log = LoggerFactory.getLogger(GGBApiWrapperTest.class);

	String TEST_DOMAIN="discussions-dev.its.umich.edu";
	String EMAIL_INSERT_TEST_GROUP="ggb-test-group-insert@discussions-dev.its.umich.edu";
	//String server = "http://localhost:9100";
	//String server = "https://one.durango.ctools.org/service";
	//String server = "https://one.durango.ctools.org";
	//String server = "http://six.durango.ctools.org/service";
	//String server = "https://ggb.openshift.dsc.umich.edu";
	String server = "http://ms-ggb-dev-cpm-dev.openshift.dsc.umich.edu";
	
	HashMap<String,String> badAuth = null;
	HashMap<String,String> goodAuth = null;


	// create an authentication object
	HashMap<String,String> createBasicAuthInfo(String username,String password) {

		HashMap<String,String >authInfo = new HashMap<String,String>();
		authInfo.put("userName",username);
		authInfo.put("password",password);
		return authInfo;
	}

	String create_test_email(String group_id, String from_name, String from_email) {
		Long now = Instant.now().getEpochSecond();
		String message_id = String.format("%d-%s",now,group_id);
		String message_date = new MailDateFormat().format(new Date());

		StringBuilder mail= new StringBuilder();

		mail
		.append("Message-ID: <").append(message_id).append(">\n")
		.append("Date: ").append(message_date).append("\n")
		.append("To: ").append(group_id).append("\n")
		.append("From: ").append(from_name).append(" <").append(from_email).append(">").append("\n")
		.append("Subject: ").append("Groups Migration API Test (java) ").append(message_date).append("\n")
		.append("\n")
		.append("This is a test email (from GGB java wrapper) generated at ").append(message_date).append("\n");

		return mail.toString();
	}

	@Before
	public void setUp() {
		log.debug("in setup");
		badAuth = createBasicAuthInfo("HOWDY","DUTY");
		goodAuth = createBasicAuthInfo("upstart","ohcrap");
		log.debug("setup: goodAuth: {}",goodAuth);
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void createTrivialInstance() {
		GGBApiWrapper ggb = new GGBApiWrapper(null,null);
		assertNotNull("create a trivial instance of GGB",ggb);
	}

	@Test
	public void testNullUrl() {
		String server = "https://nowhere.nowhow";
		GGBApiWrapper ggb = new GGBApiWrapper(server,null);
		String new_url = ggb.create_complete_url("");
		assertEquals("trivial url",server,new_url);
	}

	@Test
	public void testSmallUrl() {
		String server = "https://nowhere.nowhow";
		GGBApiWrapper ggb = new GGBApiWrapper(server,null);
		String new_url = ggb.create_complete_url("/A");
		assertEquals("small url",server+"/A",new_url);
	}

	@Test
	public void testUrlParameters() {
		String server = "https://nowhere.nowhow";
		GGBApiWrapper ggb = new GGBApiWrapper(server,null);
		String request_url = "/A/hhsd?HOOPER=dolly&bland=hot";
		String new_url = ggb.create_complete_url(request_url);
		assertEquals("trivial_url",server+request_url,new_url);
	}

	@Test
	public void do_one_get_request_status_url() {
		GGBApiWrapper ggb = new GGBApiWrapper(server,null);
		String request_url = "/status";
		String new_url = ggb.create_complete_url(request_url);
		assertEquals("trivial_url",server+request_url,new_url);
	}

	@Test
	public void get_status_ping() {
		GGBApiWrapper ggb = new GGBApiWrapper(server,goodAuth);
		String request_url = "/status";
		ApiResultWrapper arw = ggb.get_request(request_url);
		log.debug("get_status_ping: arw: {}",arw.toString());
		log.debug("get_status_ping: response: {}",arw.getResult().toString());
		JSONObject result = new JSONObject(arw.getResult().toString());
		assertEquals("get_status_ping: get status url status",(Integer)ApiResultWrapper.HTTP_SUCCESS,arw.getStatus());
		String ping_url = (String) result.get("ping");

		assertThat("ping_url is plausible",ping_url,containsString("/status/ping.json"));
	}

	@Test
	public void get_test_unprotected() {
		GGBApiWrapper ggb = new GGBApiWrapper(server,goodAuth);
		String request_url = "/test/unprotected";
		ApiResultWrapper arw = ggb.get_request(request_url);
		log.error("get_test_unprotected: arw: {}",arw.toString());
		JSONObject result = new JSONObject(arw.getResult().toString());
		log.error("get_test_unprotected: result: {}",result.toString());
		String response = (String) result.get("response");

		assertThat("unprotected worked",response,containsString("Welcome, ignoring authentication!"));
	}

	@Test
	public void get_test_protected_good_credentials() {
		log.debug("get_test_protected_good_credentials");
		log.debug("get_test_protected_good_credentials: "+goodAuth);
		GGBApiWrapper ggb = new GGBApiWrapper(server,goodAuth);
		String request_url = "/test/protected";
		ApiResultWrapper arw = ggb.get_request(request_url);
		assertEquals("get_test_protected_good_credentials: successful request",arw.getStatus(),(Integer)200);
		JSONObject result = new JSONObject(arw.getResult().toString());
		log.error("get_test_protected_good_credentials: result: {}",result.toString());
		String response = (String) result.get("response");
		log.debug("get_test_protected_good: url: {} response: {}",request_url,response);

		assertThat("require authentication",response,containsString("Welcome, authenticated!"));
	}

	
	@Test
	public void get_test_bad_credentials() {
		log.debug("get_test_bad_credentials");
		GGBApiWrapper ggb = new GGBApiWrapper(server,badAuth);
		String request_url = "/test/protected";
		ApiResultWrapper arw = ggb.get_request(request_url);
		assertEquals("get_test_protected_bad_credentials: authentication failed",arw.getStatus(),(Integer)401);

		JSONObject result = new JSONObject(arw.getResult().toString());
		log.warn("get_test_bad_credentials: status: {}",arw.getStatus());
		String response = (String) result.get("response");
		log.debug("get_test_bad_credentials: request_url: {} response: {}",request_url,response);
		
		assertThat("protected was authenticated",response,containsString("Not authorized"));
	}


	@Test
	public void post_email() {
		log.debug("post_email: goodAuth: {}",goodAuth);
		GGBApiWrapper ggb = new GGBApiWrapper(server,goodAuth);
		String archive_url = "/groups/"+EMAIL_INSERT_TEST_GROUP+"/messages";
		String test_email = create_test_email(EMAIL_INSERT_TEST_GROUP,"Dave Haines","dlhaines@umich.edu");
		log.debug("post_email: url: {}",archive_url);

		ApiResultWrapper arw = ggb.post_request(archive_url,test_email);

		log.debug("post_email: response_wrapper: {}",arw.toString());
		log.warn("post_email: status: {}",arw.getStatus());
		log.debug("post_email: request_url: {} response: {}",archive_url,arw.toString());
		assertEquals("post_email: successful request",arw.getStatus(),(Integer)200);
		
		String response = arw.getResult();
		log.debug("post_email: response: {}",response);
	}


	@Test 
	public void put_group() {
		GGBApiWrapper ggb = new GGBApiWrapper(server,goodAuth);
		
		String new_group_name = "CPM_CREATE_GROUP_TEST@"+TEST_DOMAIN;
		String new_group_url = "/groups/"+new_group_name;
		log.debug("put_group: url: {}",new_group_url);
		
		JSONObject jo = new JSONObject();
		jo.put("description","TEST DESC");
		jo.put("email",new_group_name);
		jo.put("name","Kitchen Sinker");

		ApiResultWrapper arw = ggb.put_request(new_group_url,jo.toString());
		int status = arw.getStatus();
		// see if the status is acceptable  We accept either creating the 
		// group or failing to create the group because it already exists.
		
		if (status == SC_OK) {
			assertEquals("put_group: successful request",SC_OK,status);
			String response = arw.getResult();
			log.debug("put_group: response: {}",response);
			JSONObject result_jo = new JSONObject(response);
			assertEquals("successful group insert","SUCCESS",result_jo.get("responseCode"));
		}
		else if (status == SC_CONFLICT) {
			assertEquals("put_group: conflict request",SC_CONFLICT,status);
			String response = arw.getResult();
			log.debug("put_group: response: {}",response);
			JSONObject result_jo = new JSONObject(response);
			assertEquals("conflict group insert","duplicate: Entity already exists.",result_jo.get("response"));
		}
		else {
			fail("put_group: unknown status code: "+status);
		}

	}

	@Test 
	public void put_member() {
		GGBApiWrapper ggb = new GGBApiWrapper(server,goodAuth);
		
		String new_group_name = "CPM_CREATE_GROUP_TEST@"+TEST_DOMAIN;
		Long new_id = (System.currentTimeMillis()/1000);
		String email_addr = String.format("GGB-CPM-TEST-MEMBER-%d@nowhere.edu",new_id);
		String new_member_url = String.format("/groups/%s/members/%s",new_group_name,email_addr);
		
		log.debug("put_member url: {}",new_member_url);
		
		JSONObject jo = new JSONObject();
		jo.put("email",email_addr);
		jo.put("role","MEMBER");
		
		ApiResultWrapper arw = ggb.put_request(new_member_url,jo.toString());
		log.debug("put_member: {}",arw.toString());
		assertEquals("put_member: successful request",(Integer)200,arw.getStatus());

		String result = arw.getResult();
		JSONObject result_jo = new JSONObject(result);
		
		String email = result_jo.getString("email");
		assertTrue("email is non-trivial",email.length() > 5);
	}
	
}

