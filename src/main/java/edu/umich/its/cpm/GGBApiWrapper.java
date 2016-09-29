package edu.umich.its.cpm;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
//import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;
import org.apache.http.util.EntityUtils;
import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Class to allow configuring calls to a web api that returns JSON.  It is configured with a 
 * baseUrl which is prefixed to each call and a hash of authentication information, that is assumed not 
 * to change for the duration of the class.  Multiple authentication methods will be implemented.
 * This assumes responses will be returned in REST format.  Wrappers can be used to ensure the 
 * value returned is always json.
 * 
 * The <request_type>_request methods are the external interface and deal with any requirement to 
 * do multiple requests (such as authentication or paging).
 * run_<request_type> methods implement a single request and handle those errors.
 */

/* Results are returned in a standard format implemented by the ApiResultWrapper class.  It has methods to get:
 * getStatus() - return the underlying HTTP status for the request.  
 * getMessage() - return an associated message for the status.  It may be an empty string.
 * getResult() - return the result, if there is one, in a string containing json. If the request
 * does not natively return json then a trivial json wrapper will be constructed with a 'response' key
 * that holds the actual response.
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

	// Create lousy trust strategy for testing.
	public TrustStrategy trusting() {
		return new TrustStrategy() {
			@Override
			public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
				return true;
			}
		};
	}

	public GGBApiWrapper(String baseUrl, HashMap<String, String> authInfo) {
		super();
		this.baseUrl = baseUrl;

		String default_userName = "admin";
		String default_password = "admin";

		if (authInfo == null) {
			authInfo = new HashMap<String, String>();
			authInfo.put("userName", default_userName);
			authInfo.put("password", default_password);
		}

		this.authInfo = authInfo;

		log.debug("this.authInfo: {}", this.authInfo.toString());

		try {
			log.error("ignoring ssl!!!!");

			CredentialsProvider credentialsProvider = new BasicCredentialsProvider();

			// TODO: don't use AuthScope.ANY
			log.warn("don't use AuthScope.ANY for GGB basicAuth");
			credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(
					(String) this.authInfo.get("userName"), (String) this.authInfo.get("password")));

			this.httpClient = HttpClients.custom().setSSLHostnameVerifier(new NoopHostnameVerifier())
					.setSslcontext(new SSLContextBuilder().loadTrustMaterial(null, trusting()).build())
					.setRedirectStrategy(new LaxRedirectStrategy()).setDefaultCredentialsProvider(credentialsProvider)
					// .setAuthenticationPreemptive(true);
					.build();

		} catch (KeyManagementException e) {
			log.warn(createExceptionResult("GGBApiWrapper: KeyManagementException:", e).toString());
		} catch (NoSuchAlgorithmException e) {
			log.warn(createExceptionResult("GGBApiWrapper: NoSuchAlgorithmException:", e).toString());
		} catch (KeyStoreException e) {
			log.warn(createExceptionResult("GGBApiWrapper: KeyStoreException:", e).toString());
		}

		this.httpContext = new BasicHttpContext();
		this.cookieStore = new BasicCookieStore();
		this.httpContext.setAttribute(HttpClientContext.COOKIE_STORE, this.cookieStore);
	}

	//////////////////////////////////////////////////
	// These <verb>_request methods deal with taking a request and dealing with issues
	// that requires making multiple requests.  E.G. page links, throttling.

	public ApiResultWrapper get_request(String url) {
		log.warn("get_request: Multiple call request handing not yet implemented.");
		return runGet(create_complete_url(url));
	}

	// Do a post request
	public ApiResultWrapper post_request(String url, String body) {
		log.warn("post_result: Multiple call request handing not yet implemented.");
		log.debug("post_request: {}", formatBodyInfo(url, body));
		String full_url = create_complete_url(url);
		return runPost(full_url, body);
	}

	public ApiResultWrapper put_request(String url, String body) {
		log.warn("put_request: Multiple call request handing not yet implemented.");
		log.debug("put_request: {}", formatBodyInfo(url, body));
		ApiResultWrapper response = runPut(create_complete_url(url), body);
		log.debug("put_request_response: {}", response.toString());
		return response;
	}

	////////////////////////////////
	// These run<VERB> methods take a URL, make a single call and return a result. 
	
	protected ApiResultWrapper runGet(String url) {
		log.debug("one_get_request: url: {}", url);

		HttpGet request = new HttpGet(url);
		HttpResponse response = null;

		try {
			response = this.httpClient.execute(request);
		} catch (ClientProtocolException e) {
			return createExceptionResult("runGet: ClientProtocolException:", e);
		} catch (IOException e) {
			return createExceptionResult("runGet: IOException:", e);
		}

		int status = response.getStatusLine().getStatusCode();

		if (status != HttpStatus.SC_OK) {
			String msg = String.format("runGet bad status: %s for url: %s", status, url);
			log.warn(msg);
		}

		ApiResultWrapper valueObject = safeCreateResultFromHttpResponse(url, "", response);

		log.debug("get result as json object: {}", valueObject.toString());

		return valueObject;
	}

	protected ApiResultWrapper runPost(String url, String body) {

		log.debug("runPost: {}", String.format("url: [%s] body: [%s]", url, body.toString()));
		HttpPost postRequest = new HttpPost(url);

		postRequest.setHeader("Content-Type", "text/plain");
		postRequest.setEntity((HttpEntity) new StringEntity(body, ContentType.TEXT_PLAIN));

		HttpResponse response = null;

		try {
			response = httpClient.execute(postRequest);
		} catch (IOException e) {
			String msg = "runPost: IOException for post request :" + formatBodyInfo(url, body);
			return createExceptionResult(msg, e);
		}

		// check the status code
		int status = response.getStatusLine().getStatusCode();
		String reason = response.getStatusLine().getReasonPhrase();
		log.debug("runPost: status reason: {} url: {}" + reason, url);

		if (status != HttpStatus.SC_CREATED && status != HttpStatus.SC_OK) {
			String msg = "runPost: unexpected status for post request :" + formatBodyInfo(url, body);
			log.warn(msg);
		}

		return safeCreateResultFromHttpResponse(url, body, response);
	}

	protected ApiResultWrapper runPut(String url, String body) {

		JSONObject jo = new JSONObject(body);
		log.debug("runPut: jo: {}", jo.toString());

		List<NameValuePair> params = new ArrayList<NameValuePair>();
		for (int i = 0; i < jo.names().length(); i++) {
			log.debug("name: " + jo.names().getString(i) + " value=" + jo.get(jo.names().getString(i)));
			params.add(new BasicNameValuePair(jo.names().getString(i), (String) jo.get(jo.names().getString(i))));
		}

		log.debug("ggb: runPut: {}", formatBodyInfo(url, body));

		HttpPut putRequest = new HttpPut(url);

		try {
			putRequest.setEntity(new UrlEncodedFormEntity(params));
		} catch (UnsupportedEncodingException e1) {
			log.warn("runPut exception: {}", e1.getStackTrace().toString());
		}

		HttpResponse response = null;

		try {
			response = httpClient.execute(putRequest);
		} catch (IOException e) {
			String msg = "runPost: IOException for post request :" + formatBodyInfo(url, body);
			return createExceptionResult(msg, e);
		}

		// check the status code
		int status = response.getStatusLine().getStatusCode();
		String reason = response.getStatusLine().getReasonPhrase();
		log.debug("runPut: status: {} reason: {}", status, reason);

		if (status != HttpStatus.SC_CREATED && status != HttpStatus.SC_OK) {
			String msg = String.format("runPut: unexpected status %s for put request %s:", formatBodyInfo(url, body),
					status);
			log.warn(msg);
		}

		// have a response so return it for the caller to deal with.
		// return response;
		return safeCreateResultFromHttpResponse(url, body, response);
	}

	///////////// utilities.
	// Make a partial url from a request into a full url based on the url prefix
	// provided to the constructor.
	public String create_complete_url(String url) {
		String completeUrl = baseUrl + url;
		log.info("complete_url: {}", completeUrl);
		return completeUrl;
	}

	private String formatBodyInfo(String url, String body) {
		return String.format("url/body data for: url: [%s] body: [%s]", url, body);
	}

	/////////////////////////// 
	// Format a response into a standard wrapper with a known structure.
	public ApiResultWrapper createExceptionResult(String prefix, Exception e) {
		String msg = String.format("%s cause: %s message: %s", prefix, e.getCause(), e.getMessage());
		log.warn(msg);
		return new ApiResultWrapper(ApiResultWrapper.API_EXCEPTION_ERROR, msg, null);
	}

	public ApiResultWrapper createJSONResponseMap(int status, String message, String result) {
		return new ApiResultWrapper(status, message, result);
	}

	// construct a wrapper for unknown_error
	public ApiResultWrapper createErrorResult(String message, String result) {
		return new ApiResultWrapper(ApiResultWrapper.API_UNKNOWN_ERROR, message, result);
	}

	public ApiResultWrapper createResultFromHttpResponse(HttpResponse response)
			throws JSONException, ParseException, IOException {
		String entity = EntityUtils.toString(response.getEntity(), "UTF-8");
		
		log.error("createResultFromHttpResponse: original entity: >>>{}<<<",entity);
		
		// normalize entity so can return in json string
		if (entity.startsWith(" ")) {
			log.error("cRFHR: replace leading whitespace");
			entity = entity.replaceFirst(" ","");
		}
		
		log.error("cRFHR: A: entity >>>{}<<<",entity);
		// normalize: if not already a json string then wrap in object 
		// with 'response' element and make sure the entity starts 
		// with quotes.
		
		if (!entity.startsWith("{")) {
			String formatString = entity.startsWith("\"") ? "{\"response\":%s}":"{\"response\":\"%s\"}";
			log.debug("cRFHR: formatString: {}",formatString);
			entity = String.format(formatString,entity);			
		}
		
		log.error("cRFHR: B: entity >>>{}<<<",entity);
		
		// normalize: replace any explicit line return with a \n
		entity = entity.replace("\n", "\\n");
		
		log.error("cRFHR: C: final entity >>>{}<<<",entity);
		
		entity = new JSONObject(entity).toString();
		
		log.error("createResultFromHttpResponse: final entity: >>>{}<<<",entity);
		return new ApiResultWrapper(response.getStatusLine().getStatusCode(),
				response.getStatusLine().getReasonPhrase(),
				entity);
	}

	// Create a wrapper from http response and handle common error conditions.
	public ApiResultWrapper safeCreateResultFromHttpResponse(String url, String body, HttpResponse response) {
		try {
			return createResultFromHttpResponse(response);
		} catch (ParseException e) {
			String msg = String.format("run_post ParseException: url: " + url + " body: " + body);
			return createExceptionResult(msg, e);
		} catch (IOException e) {
			String msg = String.format("run_post IOException: url: " + url + " body: " + body);
			return createExceptionResult(msg, e);
		}
	}

}
