package edu.umich.its.cpm;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
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
		log.debug("one_get_request: url: {}",url);
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
		log.debug("post_request: {}",formatBodyInfo(url,body));

		if (response == null) {
			// lower level should report the url and body
			log.warn("post_response (log) got null response");
			//System.out.println("post_response got null response");
			return null;
		}

		int status = response.getStatusLine().getStatusCode();
		//log.warn("post_request status: "+status);
		//System.out.println("post_request status: "+status);
		if (status != HttpStatus.SC_OK) {
			log.warn("post_request bad status: "+status);
			//System.out.println("post_request bad status: ");
			return null;
		}

		try {
			return EntityUtils.toString(response.getEntity(),"UTF-8");
		} catch (ParseException e) {
			log.warn("post_request exception: url: "+url+" body: "+body+" exception: "+e);
			//System.out.println("post_request exception: url: "+url+" body: "+body+" exception: "+e);
			return null;
		} catch ( IOException e) {
		log.warn("post_request exception: url: "+url+" body: "+body+" exception: "+e);
		//System.out.println("post_request exception: url: "+url+" body: "+body+" exception: "+e);
		return null;
	}
//} catch (ParseException | IOException e) {
//	log.warn("post_request exception: url: "+url+" body: "+body+" exception: "+e);
//	//System.out.println("post_request exception: url: "+url+" body: "+body+" exception: "+e);
//	return null;
//}
	}


	
	// run a post command with this url and body
	protected HttpResponse runPost (String url,String body) {
		
		log.debug("runPost: {}",String.format("url: [%s] body: [%s]", url,body.toString()));
		HttpPost postRequest = new HttpPost(url);
		
		postRequest.setHeader("Content-Type", "text/plain");
		postRequest.setEntity((HttpEntity) new StringEntity(body,ContentType.TEXT_PLAIN));

		HttpResponse response = null;

		// if error log it and return null
		//System.out.println("runPost: "+formatPostInfo(url, body));
		//System.out.println("runPost: entity: "+postRequest.toString());

		try {
			response = httpClient.execute(postRequest);
			//System.out.println("post response: "+response);
		} catch (IOException e) {
			log.warn("Exception for post request :"+formatBodyInfo(url,body));
			log.warn("exception: "+e);
			log.warn("stacktrace: ");
			e.printStackTrace();
			return null;
		}

		// check the status code
		int status = response.getStatusLine().getStatusCode();
		String reason = response.getStatusLine().getReasonPhrase();
		System.out.println("runPost: reason: "+reason);
		log.debug("runPost: reason: "+reason);

		if (status != HttpStatus.SC_CREATED) {
			// if status code is not 201, there is a problem with the
			// request.
			log.warn("Can not run post request for: url: "+url+"body: "+body);
		}

		// have a response so return it for the caller to deal with.
		return response;
	}

	public HttpResponse put_request(String url, String body) {
		log.debug("put_request: {}",formatBodyInfo(url,body));
		HttpResponse response = runPut(create_complete_url(url),body);
		log.debug("put_request_response: {}",response.toString());
		return response;
	}
	
//	 List<NameValuePair> params = new ArrayList<NameValuePair>();
//	    params.add(new BasicNameValuePair("username", "John"));
//	    params.add(new BasicNameValuePair("password", "pass"));
//	    httpPost.setEntity(new UrlEncodedFormEntity(params));
	
	// run a put command with this url and body
	protected HttpResponse runPut (String url,String body) {
		
		JSONObject jo = new JSONObject(body);
		//System.out.println("runPut: jo: "+jo.toString());
		log.debug("runPut: jo: {}",jo.toString());
		
		//for(int i = 0; i<jobject.names().length(); i++){
		//    Log.v(TAG, "key = " + jobject.names().getString(i) + " value = " + jobject.get(jobject.names().getString(i)));
		//}
		
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		for(int i = 0; i<jo.names().length(); i++){
		    System.out.println("name: "+jo.names().getString(i)+" value="+jo.get(jo.names().getString(i)));
		    params.add(new BasicNameValuePair(jo.names().getString(i),(String) jo.get(jo.names().getString(i))));
		}
		
		// body is parameters
		//List<NameValuePair> params = new ArrayList<NameValuePair>();
		
		
		log.debug("ggb: runPut: {}",formatBodyInfo(url,body));
		System.out.println("runPut: "+formatBodyInfo(url,body));
		HttpPut putRequest = new HttpPut(url);

		//postRequest.setHeader("Content-Type", "text/plain");
		//postRequest.setEntity((HttpEntity) new StringEntity(body,ContentType.TEXT_PLAIN));
		
		//putRequest.setHeader("Content-Type", "text/plain");
		//putRequest.setHeader("Content-Type", "application/json");
		//putRequest.setEntity((HttpEntity) new StringEntity(body,ContentType.TEXT_PLAIN));
		//stringEntity.setContentType("application/json")
		//StringEntity stringEntity= new StringEntity(body,"UTF-8");
		//stringEntity.setContentType("application/json");
		//putRequest.setEntity((HttpEntity) new StringEntity(body,"UTF-8"));
		//putRequest.setEntity((HttpEntity) stringEntity);
		try {
			putRequest.setEntity(new UrlEncodedFormEntity(params));
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		HttpResponse response = null;

		// if error log it and return null
		//System.out.println("runPost: "+formatPostInfo(url, body));
		//System.out.println("runPost: entity: "+postRequest.toString());

		try {
			response = httpClient.execute(putRequest);
			System.out.println("put response: "+response);
			System.out.println("response: "+response.getStatusLine().getReasonPhrase());

		} catch (IOException e) {
			log.warn("Exception for put request :"+formatBodyInfo(url,body));
			log.warn("exception: "+e);
			log.warn("stacktrace: ");
			log.warn(e.getStackTrace().toString());
			e.printStackTrace();
			return null;
		}

		// check the status code
		int status = response.getStatusLine().getStatusCode();
		String reason = response.getStatusLine().getReasonPhrase();
		System.out.println("runPut: reason: "+reason);
		log.debug("runPut: reason: "+reason);

		if (status != HttpStatus.SC_CREATED) {
			// if status code is not 201, there is a problem with the
			// request.
			log.warn("Can not run put request for: url: "+url+"body: "+body);
		}

		// have a response so return it for the caller to deal with.
		return response;
	}
	
	private String formatBodyInfo(String url, String body) {
		return String.format("url/body data for: url: [%s] body: [%s]",url,body);
	}

	// ///////////////// possible methods
	// process_link_header

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

}
