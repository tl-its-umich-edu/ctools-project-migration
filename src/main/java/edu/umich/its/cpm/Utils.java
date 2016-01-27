package edu.umich.its.cpm;

import java.util.HashMap;

import javax.servlet.http.HttpServletRequest;

import org.apache.http.HttpResponse;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;

import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



@PropertySource("file:${catalina.base:/usr/local/ctools/app/ctools/tl}/home/application.properties")
@Configuration
public class Utils {

	public static final String MIGRATION_STATUS = "status";
	public static final String MIGRATION_DATA = "data";
	
	public static final String BOX_CLIENT_ID = "box_client_id";
	public static final String BOX_CLIENT_SECRET = "box_client_secret";
	public static final String BOX_API_URL = "box_api_url";
	public static final String BOX_TOKEN_URL = "box_token_url";
	public static final String BOX_CLIENT_REDIRECT_URL = "box_client_redirect_uri";
	
	// the at sign used in email address
	private static final String EMAIL_AT = "@";
	private static final String EMAIL_AT_UMICH = "@umich.edu";
	
	private static final Logger log = LoggerFactory
			.getLogger(Utils.class);
	
	
	private static Environment env;

    public void setEnvironment(final Environment env) {
        this.env = env;
    }
	
	/**
	 * login into CTools and become user with sessionId
	 */
	public static HashMap<String, Object> login_becomeuser(HttpServletRequest request) {
		// return the session related attributes after successful login call
		HashMap<String, Object> sessionAttributes = new HashMap<String, Object>();
		
		// session id after login
		String sessionId = "";
		
		//create httpclient
		HttpClient httpClient = HttpClientBuilder.create().build();

		//store cookies in context, retain the session information
		CookieStore cookieStore = new BasicCookieStore();
		HttpContext httpContext = new BasicHttpContext();
		httpContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

		String remoteUser = "zqian";
		log.info("remote user is " + remoteUser);
		
		// here is the CTools integration prior to CoSign integration ( read
		// session user information from configuration file)
		// 1. create a session based on user id and password
		// the url should be in the format of
		// "https://server/direct/session?_username=USERNAME&_password=PASSWORD"
		String requestUrl = env.getProperty("ctools.server.url")
				+ "direct/session?_username=" + env.getProperty("username")
				+ "&_password=" + env.getProperty("password");
		try
		{
			HttpPost postRequest = new HttpPost(requestUrl);
			postRequest.setHeader("Content-Type", "application/x-www-form-urlencoded");
			HttpResponse response = httpClient.execute(postRequest, httpContext);
			
			//get the status code
			int status = response.getStatusLine().getStatusCode();
			log.info("---------------session creation call" + status);
			
			if (status != 201) {
				//if status code is not 201, there is a problem with the request.
				// return error if a new CTools session could not be created using
				// username and password provided
				log.info("Wrong user id or password. Cannot login to CTools "
						+ env.getProperty("ctools.server.url"));
			} else {
				
				//if status code is 201 login is successful. So reuse the httpContext for next requests.
				// get the session id
				sessionId = EntityUtils.toString(response.getEntity(), "UTF-8");
				log.info("successfully logged in as user "
						+ env.getProperty("username") + " with sessionId = "
						+ sessionId);
	
				// 2. become the user based on REMOTE_USER setting after CoSign
				// integration
				try {
					// the url should be in the format of
					// "https://server/direct/session/SESSION_ID.json"
					requestUrl = env.getProperty("ctools.server.url")
							+ "direct/session/becomeuser/" + remoteUser
							+ ".json?_sessionId=" + sessionId;
					log.info(requestUrl);
					
					HttpGet getRequest = new HttpGet(requestUrl);
					getRequest.setHeader("Content-Type", "application/x-www-form-urlencoded");
					HttpResponse r = httpClient.execute(getRequest, httpContext);
					
					String resultString = EntityUtils.toString(r.getEntity(), "UTF-8");
					log.info(resultString);
				} catch (java.io.IOException e) {
					log.error(requestUrl + e.getMessage());
	
					// nullify sessionId if become user call is not successful
					sessionId = null;
				}
				
				// populate the session related attributes
				sessionAttributes.put("sessionId", sessionId);
				sessionAttributes.put("httpContext", httpContext);
			}
		}
		catch (java.io.IOException e)
		{
			log.error(requestUrl + e.getMessage());
		}
		
		return sessionAttributes;
	}
	
	/**
	 * Checks first whether the JSONObject contains specified key value; If so,
	 * return the String value associated with the key
	 * 
	 * @param object
	 * @param key
	 * @return
	 */
	public static String getJSONString(JSONObject object, String key) {
		String rv = null;
		if (object.has(key) && object.get(key) != JSONObject.NULL) {
			rv = object.getString(key);
		}
		return rv;
	}
	
	/**
	 * Checks first whether the JSONObject contains specified key value; If so,
	 * return the long value associated with the key
	 * 
	 * @param object
	 * @param key
	 * @return
	 */
	public static long getJSONLong(JSONObject object, String key) {
		long rv = 0L;
		if (object.has(key) && object.get(key) != JSONObject.NULL) {
			rv = object.getLong(key);
		}
		return rv;
	}
	
	/**
	 * construct the user email address
	 */
	public static String getUserEmail(String userId)
	{
		String remoteUserEmail = userId;
		if (remoteUserEmail.indexOf(EMAIL_AT) == -1) {
			// if the remote user value is not of email format
			// then it is the uniqname of umich user
			// we need to attach "@umich.edu" to it to make it a full email
			// address
			remoteUserEmail = remoteUserEmail + EMAIL_AT_UMICH;
		}
		return remoteUserEmail;
	}
}
