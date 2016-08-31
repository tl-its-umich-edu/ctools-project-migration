package junit.edu.umich.its.cpmtest;

import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.log4j.BasicConfigurator;

import static org.junit.Assert.*;
//import static org.junit.matchers.JUnitMatchers.*;
import static org.hamcrest.CoreMatchers.*;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;

import javax.mail.internet.MailDateFormat;
import javax.net.ssl.SSLContext;

import org.apache.http.HttpResponse;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import static org.apache.http.conn.ssl.SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;


import edu.umich.its.cpm.GGBApiWrapper;

//@RunWith(SpringJUnit4ClassRunner.class)
//@SpringBootTest
//@ContextConfiguration
//@RunWith(SpringJUnit4ClassRunner.class)
public class GGBApiWrapperTest {
	
	//static final Logger log = LoggerFactory.getLogger(GGBApiWrapperTest.class);
	static final Logger log = LoggerFactory.getLogger(GGBApiWrapperTest.class);
	

	
	//EMAIL_INSERT_TEST_GROUP="ggb-test-group-insert@discussions-dev.its.umich.edu"
	String EMAIL_INSERT_TEST_GROUP="ggb-test-group-insert@discussions-dev.its.umich.edu";
	//GGBApiWrapper ggb = new GGBApiWrapper(null,null);
	String server = "http://localhost:9100";
	 //url = "/groups/#{group_id}/messages"
	
	String create_test_email(String group_id, String from_name, String from_email) {
		Long now = Instant.now().getEpochSecond();
		String message_id = String.format("%d-%s",now,group_id);
//		//message_date = now.strftime '%a, %d %b %Y %T %z'
		String message_date = new MailDateFormat().format(new Date());
		System.out.println("message_date: "+message_date.toString());

		
		
//		Message-ID: <1472563409.251-ggb-test-group-insert@discussions-dev.its.umich.edu>
//		Date: Tue, 30 Aug 2016 09:23:29 -0400
//		To: ggb-test-group-insert@discussions-dev.its.umich.edu
//		From: "Dave Haines" <dlhaines@umich.edu>
//		Subject: Groups Migration API Test 2016-08-30T09:23:29-04:00
//
//		This is a test email generated at 2016-08-30T09:23:29-04:00
		
		StringBuilder mail= new StringBuilder();
		
//		mail
//		.append("Message-ID: <1472563409.251-ggb-test-group-insert@discussions-dev.its.umich.edu>\n")
//		.append("Date: Tue, 30 Aug 2016 09:23:29 -0400\n")
//		.append("To: ggb-test-group-insert@discussions-dev.its.umich.edu\n")
//		.append("From: \"Dave Haines\" <dlhaines@umich.edu>\n")
//		.append("Subject: Groups Migration API Test 2016-08-30T09:23:29-04:00\n")
//		.append("\n")
//		.append("This is a test email generated at 2016-08-30T09:23:29-04:00");
//		
		
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
	//def create_test_email(group_id, from_name, from_email)
	//# Format an RFC822 message
		
	//now = Time.now
	//message_id = "#{now.to_f}-#{group_id}"
	//message_date = now.strftime '%a, %d %b %Y %T %z'
	//message = <<-EOF
	//Message-ID: <#{message_id}>
	//Date: #{message_date}
	//To: #{group_id}
	//From: "#{from_name}" <#{from_email}>
	//Subject: Groups Migration API Test #{now.iso8601}
	//
	//This is a test email generated at #{now.iso8601}
	//EOF
	//end
	
//	@Before
//	public void setUp() throws Exception {
//	}

	// will print logging to stdout
//	@Before
//	public void setUp() {
//	// set up a basic logging configuration for the test environment
//	BasicConfigurator.resetConfiguration();
//	BasicConfigurator.configure();
//	}
	
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
	
//	void setupSSL() {
//	TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
//
//	SSLContext sslContext = null;
//	try {
//		sslContext = org.apache.http.ssl.SSLContexts.custom()
//		        .loadTrustMaterial(null, acceptingTrustStrategy)
//		        .build();
//	} catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	}
//
//	SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);
//
//	CloseableHttpClient httpClient = HttpClients.custom()
//	        .setSSLSocketFactory(csf)
//	        .build();
//
//	HttpComponentsClientHttpRequestFactory requestFactory =
//	        new HttpComponentsClientHttpRequestFactory();
//
//	requestFactory.setHttpClient(httpClient);
//	}
	
//	void setupIgnorantSSL() {
//	   HttpComponentsClientHttpRequestFactory requestFactory = 
//			      new HttpComponentsClientHttpRequestFactory();
//			    DefaultHttpClient httpClient = (DefaultHttpClient) requestFactory.getHttpClient();
//			    TrustStrategy acceptingTrustStrategy = new TrustStrategy() {
//			    @Override
//			        public boolean isTrusted(X509Certificate[] certificate, String authType) {
//			            return true;
//			        }
//			    };
//			    SSLSocketFactory sf = null;
//				try {
//					sf = new SSLSocketFactory(acceptingTrustStrategy, ALLOW_ALL_HOSTNAME_VERIFIER);
//				} catch (KeyManagementException | UnrecoverableKeyException | NoSuchAlgorithmException
//						| KeyStoreException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//			    httpClient.getConnectionManager().getSchemeRegistry()
//			      .register(new Scheme("https", 8443, sf));
//	} 
//	
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
		System.out.println("test_email:\n"+test_email);
		String response = ggb.post_request(archive_url,test_email);
		System.out.println("email post: response: ["+response+"]");
		assertTrue("got a response: ",response.length() > 0);
	}
	
}

//context "ARCHIVE" do
//
//    should "add email" do
//      group_id = EMAIL_INSERT_TEST_GROUP
//      test_email = create_test_email group_id, "Dave Haines", "dlhaines@umich.edu"
//      #puts "test_email: [#{test_email}]"
//      url = "/groups/#{group_id}/messages"
//      post url, test_email
//      #puts "add email: last_response #{last_response.pretty_inspect}"
//      assert last_response.ok?, 'inserting email'
//      #fail "verify when can see the email archive"
//    end
//
//  end


//def create_test_email(group_id, from_name, from_email)
//# Format an RFC822 message
//now = Time.now
//message_id = "#{now.to_f}-#{group_id}"
//message_date = now.strftime '%a, %d %b %Y %T %z'
//message = <<-EOF
//Message-ID: <#{message_id}>
//Date: #{message_date}
//To: #{group_id}
//From: "#{from_name}" <#{from_email}>
//Subject: Groups Migration API Test #{now.iso8601}
//
//This is a test email generated at #{now.iso8601}
//EOF
//end
