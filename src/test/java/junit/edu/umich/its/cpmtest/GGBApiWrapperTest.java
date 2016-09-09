package junit.edu.umich.its.cpmtest;

//import static org.junit.matchers.JUnitMatchers.*;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.time.Instant;
import java.util.Date;

import javax.mail.internet.MailDateFormat;

import org.apache.http.HttpResponse;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umich.its.cpm.GGBApiWrapper;

public class GGBApiWrapperTest {

	static final Logger log = LoggerFactory.getLogger(GGBApiWrapperTest.class);

	String TEST_DOMAIN="discussions-dev.its.umich.edu";
	String EMAIL_INSERT_TEST_GROUP="ggb-test-group-insert@discussions-dev.its.umich.edu";
	String server = "http://localhost:9100";

	String create_test_email(String group_id, String from_name, String from_email) {
		Long now = Instant.now().getEpochSecond();
		String message_id = String.format("%d-%s",now,group_id);
		//		//message_date = now.strftime '%a, %d %b %Y %T %z'
		String message_date = new MailDateFormat().format(new Date());
		System.out.println("message_date: "+message_date.toString());

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
		assertEquals("trivial_url",server,new_url);
	}

	@Test
	public void testSmallUrl() {
		String server = "https://nowhere.nowhow";
		GGBApiWrapper ggb = new GGBApiWrapper(server,null);
		String new_url = ggb.create_complete_url("/A");
		assertEquals("trivial_url",server+"/A",new_url);
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
		String server = "https://one.durango.ctools.org";
		GGBApiWrapper ggb = new GGBApiWrapper(server,null);
		String request_url = "/status";
		String new_url = ggb.create_complete_url(request_url);
		assertEquals("trivial_url",server+request_url,new_url);
	}
	
	@Test
	public void do_one_get_status_get() {
		GGBApiWrapper ggb = new GGBApiWrapper(server,null);
		String request_url = "/status";
		JSONObject jo = ggb.get_request(request_url);
		String ping_url = (String) jo.get("ping");

		assertThat("ping_url is plausible",ping_url,containsString("/status/ping.json"));
	}

	@Test
	public void do_one_post_email() {
		//		setupSSL();
		GGBApiWrapper ggb = new GGBApiWrapper(server,null);
		String archive_url = "/groups/"+EMAIL_INSERT_TEST_GROUP+"/messages";
		String test_email = create_test_email(EMAIL_INSERT_TEST_GROUP,"Dave Haines","dlhaines@umich.edu");
		String response = ggb.post_request(archive_url,test_email);
		assertTrue("got a response: ",response.length() > 0);
	}


	@Test 
	public void do_one_put_group() {
		GGBApiWrapper ggb = new GGBApiWrapper(server,null);
		
		String new_group_name = "CPM_CREATE_GROUP_TEST@"+TEST_DOMAIN;
		String new_group_url = "/groups/"+new_group_name;

		JSONObject jo = new JSONObject();
		jo.put("description","TEST DESC");
		jo.put("email",new_group_name);
		jo.put("name","Kitchen Sinker");

		HttpResponse response = ggb.put_request(new_group_url,jo.toString());
		assertTrue("got a response: ",response != null);
	}
	
	@Test 
	public void do_one_put_member() {
		GGBApiWrapper ggb = new GGBApiWrapper(server,null);
		
		String new_group_name = "CPM_CREATE_GROUP_TEST@"+TEST_DOMAIN;
		Long new_id = (System.currentTimeMillis()/1000);
		String email_addr = String.format("GGB-CPM-TEST-MEMBER-%d@nowhere.edu",new_id);
		String new_member_url = String.format("/groups/%s/members/%s",new_group_name,email_addr);
		
		JSONObject jo = new JSONObject();
		jo.put("email",email_addr);
		jo.put("role","MEMBER");
		
		HttpResponse response = ggb.put_request(new_member_url,jo.toString());
		assertTrue("put_member got a response: ",response != null);
	}

	
}

