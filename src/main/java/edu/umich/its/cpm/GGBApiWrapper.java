package edu.umich.its.cpm;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

//import javax.servlet.http.HttpServletRequest;
//import org.apache.http.HttpResponse;
//import org.apache.http.client.CookieStore;
//import org.apache.http.client.HttpClient;
//import org.apache.http.client.methods.HttpGet;
//import org.apache.http.client.methods.HttpPost;
//import org.apache.http.client.protocol.HttpClientContext;
//import org.apache.http.impl.client.BasicCookieStore;
//import org.apache.http.impl.client.HttpClientBuilder;
//import org.apache.http.protocol.BasicHttpContext;
//import org.apache.http.protocol.HttpContext;
//import org.apache.http.util.EntityUtils;
//import org.springframework.core.env.Environment;

/*
 * Class to allow configuring calls to a web api that returns JSON.  It is configured with a 
 * baseUrl which is prefixed to each call and a hash of authentication information, that is assumed not 
 * to change for the duration of the class.  Multiple authentication methods will be implemented.
 * This assumes responses will be returned in REST format.  Wrappers can be used to ensure the 
 * value returned is always json.
 */

public class GGBApiWrapper {

	String server;
	String suffix;
	String baseUrl = server + suffix;
	HttpClient httpClient;
	@SuppressWarnings("rawtypes")
	HashMap authInfo;
	CookieStore cookieStore;
	HttpContext httpContext;

	private static final Logger log = LoggerFactory.getLogger(GGBApiWrapper.class);

	// create a class instance to call a micro service. It takes
	// a base url (prepended to all requests) and a hashmap containing
	// any information required for authentication. There may be multiple
	// authentication methods so a hashmap is used to retain flexibility.

	// remember authinfo is JUST for authentication method. For CTools ok to have session id
	// in it

	//first just want enough to run a query with trivial auth

	@SuppressWarnings("rawtypes")
	public GGBApiWrapper(String baseUrl, HashMap authInfo) {
		super();
		this.baseUrl = baseUrl;
		this.authInfo = authInfo;
		this.httpClient = HttpClientBuilder.create().build();
		this.httpContext = new BasicHttpContext();
		this.cookieStore = new BasicCookieStore();
		this.httpContext.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
	}

	// Take an URL and get the data back make calls to do_request to get
	// external data.
	// Deal with any multiple call issues: e.g. authentication, page links,
	// throttling.

	public JSONObject get_request(String url) {
		log.debug("just pass through to do_request for now, handle multiple queries later.");
		return do_one_get_request(create_complete_url(url));
	}

	// take a url, make a single call, return a json result.
	protected JSONObject do_one_get_request(String url) {
		RestTemplate restTemplate = new RestTemplate();
		ResponseEntity<String> value = restTemplate.getForEntity(url, String.class);
		JSONObject valueObject = null;

		if (value.getStatusCode().is2xxSuccessful()) {
			valueObject = new JSONObject(value.getBody());
		}

		return valueObject;
	}

	public String create_complete_url(String url) {
		return baseUrl+url;
	}

	public String post_request(String url, String body){
		HttpResponse response = runPost(create_complete_url(url),body);

		if (response == null) {
			// lower level should report the url and body
			log.warn("post_response (log) got null response");
			System.out.println("post_response got null response");
			return null;
		}

		int status = response.getStatusLine().getStatusCode();
		log.warn("post_request status: "+status);
		System.out.println("post_request status: "+status);
		if (status != HttpStatus.SC_OK) {
			System.out.println("post_request bad status: ");
			return null;
		}

		try {
			return EntityUtils.toString(response.getEntity(),"UTF-8");
		} catch (ParseException | IOException e) {
			log.warn("post_request exception: url: "+url+" body: "+body+" exception: "+e);
			System.out.println("post_request exception: url: "+url+" body: "+body+" exception: "+e);
			return null;
		}
	}

	// run a post command with this url and body
	protected HttpResponse runPost (String url,String body) {
		HttpPost postRequest = new HttpPost(url);
		//postRequest.setHeader("Content-Type", "application/x-www-form-urlencoded");
		postRequest.setHeader("Content-Type", "text/plain");

		postRequest.setEntity((HttpEntity) new StringEntity(body,ContentType.TEXT_PLAIN));

		HttpResponse response = null;

		// if error log it and return null
		System.out.println("runPost: "+formatPostInfo(url, body));
		System.out.println("runPost: entity: "+postRequest.toString());

		try {
			response = httpClient.execute(postRequest);
			System.out.println("post response: "+response);
		} catch (IOException e) {
			System.out.println("Exception for post request :"+formatPostInfo(url,body));
			System.out.println("exception: "+e);
			System.out.println("stacktrace: ");
			e.printStackTrace();
			return null;
		}

		// check the status code
		int status = response.getStatusLine().getStatusCode();
		String reason = response.getStatusLine().getReasonPhrase();
		System.out.println("runPost: reason: "+reason);

		if (status != HttpStatus.SC_CREATED) {
			// if status code is not 201, there is a problem with the
			// request.
			log.warn("Can not run post request for: url: "+url+"body: "+body);
		}

		// have a response so return it for the caller to deal with.
		return response;
	}

	private String formatPostInfo(String url, String body) {
		return String.format("post data for: url: [%s] body: [%s]",url,body);
	}

	// ///////////////// possible methods
	// process_link_header


	// asks get query
	//	requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
	//			+ "direct/user/" + userEid + ".json?_sessionId="
	//			+ sessionId;
	//	log.info(this + " get_user_myworkspace_site_json "
	//			+ requestUrl);
	//	ResponseEntity<String> userEntity = restTemplate
	//			.getForEntity(requestUrl, String.class);
	//	if (userEntity.getStatusCode().is2xxSuccessful()) {
	//		JSONObject userObject = new JSONObject(
	//				userEntity.getBody());
	//		String userId = (String) userObject.get("id");
	//		// use this userId to form user myworkspace id
	//		requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
	//				+ "direct/site/~" + userId
	//				+ ".json?_sessionId=" + sessionId;
	//		log.info(this + " get_user_myworkspace_site_json "
	//				+ requestUrl);
	//		ResponseEntity<String> siteEntity = restTemplate
	//				.getForEntity(requestUrl, String.class);
	//		if (siteEntity.getStatusCode().is2xxSuccessful()) {
	//			// now we find the user MyWorkspace site
	//			siteJson = siteEntity.getBody();
	//		}
	//	}
	//} catch (RestClientException e2) {
	//	log.error(this + requestUrl + e2.getMessage());
	//}
	//}


	//////// sample code that deals with ctools login.



	// public static HashMap<String, Object> login_becomeuser(Environment env,
	// HttpServletRequest request, String remoteUser) {
	// // return the session related attributes after successful login call
	// HashMap<String, Object> sessionAttributes = new HashMap<String,
	// Object>();
	//
	// // session id after login
	// String sessionId = "";
	//
	// // create httpclient
	// HttpClient httpClient = HttpClientBuilder.create().build();
	//
	// // store cookies in context, retain the session information
	// CookieStore cookieStore = new BasicCookieStore();
	// HttpContext httpContext = new BasicHttpContext();
	// httpContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
	//
	// log.info("remote user is " + remoteUser);
	//
	// // here is the CTools integration prior to CoSign integration ( read
	// // session user information from configuration file)
	// // 1. create a session based on user id and password
	// // the url should be in the format of
	// // "https://server/direct/session?_username=USERNAME&_password=PASSWORD"
	// String requestUrl = env.getProperty(ENV_PROPERTY_CTOOLS_SERVER_URL)
	// + "direct/session?_username=" + env.getProperty("username")
	// + "&_password=" + env.getProperty("password");
	// try {
	// HttpPost postRequest = new HttpPost(requestUrl);
	// postRequest.setHeader("Content-Type",
	// "application/x-www-form-urlencoded");
	// HttpResponse response = httpClient
	// .execute(postRequest, httpContext);
	//
	// // get the status code
	// int status = response.getStatusLine().getStatusCode();
	//
	// if (status != 201) {
	// // if status code is not 201, there is a problem with the
	// // request.
	// // return error if a new CTools session could not be created
	// // using
	// // username and password provided
	// log.info("Wrong user id or password. Cannot login to CTools "
	// + env.getProperty(ENV_PROPERTY_CTOOLS_SERVER_URL));
	// } else {
	//
	// // if status code is 201 login is successful. So reuse the
	// // httpContext for next requests.
	// // get the session id
	// sessionId = EntityUtils.toString(response.getEntity(), "UTF-8");
	// log.info("successfully logged in as user "
	// + env.getProperty("username") + " with sessionId = "
	// + sessionId);
	//
	// // 2. become the user based on REMOTE_USER setting after CoSign
	// // integration
	// try {
	// // the url should be in the format of
	// // "https://server/direct/session/SESSION_ID.json"
	// requestUrl = env.getProperty(ENV_PROPERTY_CTOOLS_SERVER_URL)
	// + "direct/session/becomeuser/" + remoteUser
	// + ".json?_sessionId=" + sessionId;
	// log.info(requestUrl);
	//
	// HttpGet getRequest = new HttpGet(requestUrl);
	// getRequest.setHeader("Content-Type",
	// "application/x-www-form-urlencoded");
	// HttpResponse r = httpClient
	// .execute(getRequest, httpContext);
	//
	// String resultString = EntityUtils.toString(r.getEntity(),
	// "UTF-8");
	// log.info(resultString);
	// } catch (java.io.IOException e) {
	// log.error(requestUrl + e.getMessage());
	//
	// // nullify sessionId if become user call is not successful
	// sessionId = null;
	// }
	//
	// // populate the session related attributes
	// sessionAttributes.put(SESSION_ID, sessionId);
	// sessionAttributes.put("httpContext", httpContext);
	// }
	// } catch (java.io.IOException e) {
	// log.error(requestUrl + e.getMessage());
	// }
	//
	// return sessionAttributes;
	// }

	protected HashMap ggb_auth_setup(HashMap<String, Object> authInfo) {
		// create httpclient

		authInfo.put("httpClient",HttpClientBuilder.create().build());

		// store cookies in authInfo, retain the session information
		CookieStore cookieStore = new BasicCookieStore();
		authInfo.put("cookieStore", new BasicCookieStore());

		HttpContext httpContext = new BasicHttpContext();
		authInfo.put("httpContext", httpContext);
		httpContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

		authInfo.put("sessionId", null);

		return authInfo;
	}




	protected boolean ctools_auth_login(HashMap<String, Object> authInfo) {
		// String requestUrl = env.getProperty(ENV_PROPERTY_CTOOLS_SERVER_URL)
		String authRequestUrl = authInfo.get("baseUrl") + "/session?_username=" + authInfo.get("username")
		+ "&_password=" + authInfo.get("password");
		HttpClient httpClient = (HttpClient) authInfo.get("httpClient");
		HttpContext httpContext = (HttpContext) authInfo.get("httpContext");

		authInfo.put("sessionId", null);

		String requestUrl = null;

		HttpResponse response = null;

		// login as admin
		try {
			HttpPost postRequest = new HttpPost(authRequestUrl);
			postRequest.setHeader("Content-Type", "application/x-www-form-urlencoded");
			response = httpClient.execute(postRequest, httpContext);
			//HttpResponse response = httpClient.execute(postRequest, httpContext);

			// get the status code
			int status = response.getStatusLine().getStatusCode();

			if (status != 201) {
				// if status code is not 201, there is a problem with the
				// request.
				// return error if a new CTools session could not be created
				// using
				// username and password provided
				log.warn("Wrong user id or password. Cannot login to CTools " + "server: " + authInfo.get("baseUrl")
				+ "username: " + authInfo.get("username"));
				return false;
			}
		} catch (java.io.IOException e) {
			log.error(requestUrl + e.getMessage());
			// nullify sessionId if become user call is not successful
			authInfo.put("sessionId", null);
			//} finally {
			//	log.debug("outer finally");
		}

		// if status code is 201 login is successful. So reuse the
		// httpContext for next requests.
		// get the session id
		try {
			authInfo.put("sessionId", EntityUtils.toString(response.getEntity(), "UTF-8"));
		} catch (ParseException e1) {
			log.error("ParseError: ",e1);
			return false;
		} catch (IOException e1) {
			log.error("IOException: ",e1);
			return false;
		}


		log.info("successfully logged in as user " + authInfo.get("username") + " with sessionId = "
				+ authInfo.get("sessionId"));

		// 2. become the user based on REMOTE_USER setting after CoSign
		// integration
		// the url should be in the format of
		// "https://server/direct/session/SESSION_ID.json"
		String sessionRequestUrl = authInfo.get("baseUrl") + "direct/session/becomeuser/"
				+ authInfo.get("remoteUser") + ".json?_sessionId=" + authInfo.get("sessionId");
		log.info(sessionRequestUrl);

		HttpGet getRequest = new HttpGet(requestUrl);
		getRequest.setHeader("Content-Type",
				"application/x-www-form-urlencoded");

		try {
			// the url should be in the format of
			// "https://server/direct/session/SESSION_ID.json"
			// requestUrl = authInfo.get("baseUrl")
			// + "direct/session/becomeuser/" + authInfo.get("remoteUser")
			// + ".json?_sessionId=" + authInfo.get("sessionId");
			// log.info(requestUrl);

			//HttpGet getRequest = new HttpGet(requestUrl);
			//			getRequest.setHeader("Content-Type",
			//					"application/x-www-form-urlencoded");
			HttpResponse r = httpClient
					.execute(getRequest, httpContext);

			String resultString = EntityUtils.toString(r.getEntity(),
					"UTF-8");
			log.info(resultString);
		} catch (java.io.IOException e) {
			log.error(requestUrl + e.getMessage());

			// nullify sessionId if become user call is not successful
			authInfo.put("sessionId", null);
		}
		// finally {
		// log.debug("HOWDY");
		// }

		// }

		// populate the session related attributes
		// sessionAttributes.put(SESSION_ID, sessionId);
		// sessionAttributes.put("httpContext", httpContext);
		// }
		// } catch (java.io.IOException e) {
		// log.error(requestUrl + e.getMessage());
		// }

		// }
		return false;
	}
}
