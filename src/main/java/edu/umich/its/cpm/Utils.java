package edu.umich.its.cpm;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.io.FilenameUtils;
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
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;

import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.config.TikaConfig;
import org.apache.commons.io.FilenameUtils;

@Configuration
public class Utils {

	public static final String COLLECTION_TYPE = "collection";
	
	public static final String MIGRATION_TYPE_BOX = "box";
	public static final String MIGRATION_TYPE_ZIP = "zip";
	
	public static final String MIGRATION_STATUS = "status";
	public static final String MIGRATION_DATA = "data";
	
	// the format of folder path in box
	// e.g. https://umich.app.box.com/files/0/f/<folderId>
	public static final String BOX_FILE_PATH_URL = "https://umich.app.box.com/files/0/f/";
	public static final String BOX_CLIENT_ID = "box_client_id";
	public static final String BOX_CLIENT_SECRET = "box_client_secret";
	public static final String BOX_API_URL = "box_api_url";
	public static final String BOX_TOKEN_URL = "box_token_url";
	public static final String BOX_CLIENT_REDIRECT_URL = "box_client_redirect_uri";
	
	public static final String SERVER_URL = "server_url";
	
	// CTools resource type strings
	public static final String CTOOLS_RESOURCE_TYPE_URL = "text/url";
	public static final String CTOOLS_RESOURCE_TYPE_URL_EXTENSION = ".URL";
	public static final String CTOOLS_RESOURCE_TYPE_CITATION = "text/html";
	public static final String CTOOLS_RESOURCE_TYPE_CITATION_URL = "/citation/";
	public static final String HTML_FILE_EXTENSION=".html";
	
	// the at sign used in email address
	private static final String EMAIL_AT = "@";
	private static final String EMAIL_AT_UMICH = "@umich.edu";
	// the path separator
	public static final String PATH_SEPARATOR = "/";
	// the extension character
	public static final String EXTENSION_SEPARATOR = ".";
	
	private static final Logger log = LoggerFactory
			.getLogger(Utils.class);

	private static TikaConfig tikaConfig = TikaConfig.getDefaultConfig();
	
	/**
	 * login into CTools and become user with sessionId
	 */
	public static HashMap<String, Object> login_becomeuser(Environment env, HttpServletRequest request, String remoteUser) {
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
	 * return long value based on JSON string
	 * 
	 * @param object
	 * @param key
	 * @return
	 */
	public static long getJSONLong(JSONObject object, String key) {
		long rv = 0;
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
	
	/************* LDAP lookup ****************/
	private static final String OU_GROUPS = "ou=groups";
	private static final String ALLOW_TESTUSER_URLOVERRIDE = "allow.testUser.urlOverride";
	private static final String LDAP_CTX_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
	private static final String PROPERTY_LDAP_SERVER_URL = "ldap.server.url";
	protected static final String PROPERTY_AUTH_GROUP = "mcomm.group";
	private static final String TEST_USER = "testUser";
	
	/*
	 * User is authenticated using CoSign and authorized using Ldap. 
	 * For local development, we have use.testUser.url = true to enable "testUser" parameter. 
	 * and only CoSigned user from certain LDAP group can use the "testUser" param in URL. 
	 * For production server, set use.testUser.url=false
	 */
	public static String getCurrentUserId(HttpServletRequest request, Environment env) {
		// get CoSign user first
		String remoteUser = request.getRemoteUser();

		String rvUser = remoteUser;
		
		// get the environment setting, default to "false"
		String allowTestUserUrlOverride = env.getProperty(ALLOW_TESTUSER_URLOVERRIDE, Boolean.FALSE.toString());
		if (hasValue(allowTestUserUrlOverride) && Boolean.valueOf(allowTestUserUrlOverride))
		{
			// non-prod environment
			// check for the "testUser" param in url
			String testUser = request.getParameter(TEST_USER);
			
			String propLdapServerUrl = env.getProperty(PROPERTY_LDAP_SERVER_URL);
			String propMCommGroup = env.getProperty(PROPERTY_AUTH_GROUP);
			if (hasValue(testUser))
			{ 
				if (hasValue(propLdapServerUrl) && hasValue(propMCommGroup))
				{
					// check whether the current user is authorized to set "testUser" param in URL
					if (ldapAuthorizationVerification(propLdapServerUrl, propMCommGroup, remoteUser))
					{
						rvUser = testUser;
					}
				}
			}

			log.debug("CoSign user=" + remoteUser + " test user=" + testUser + " returned user=" + rvUser);
		}
		return rvUser;

	}
	
	/*
	 * The Mcommunity group we have is a members-only group is one that only the members of the group can send mail to. 
	 * The group owner can turn this on or off.
	 * More info on Ldap configuration  http://www.itcs.umich.edu/itcsdocs/r1463/attributes-for-ldap.html#group.
	 */
	private static boolean ldapAuthorizationVerification(String ldapUrl, String mcommunityGroup, String user)  {
		log.info("ldapAuthorizationVerification(): called");
		boolean isAuthorized = false;
		
		DirContext dirContext=null;
		NamingEnumeration listOfPeopleInAuthGroup=null;
		NamingEnumeration allSearchResultAttributes=null;
		NamingEnumeration simpleListOfPeople=null;
		Hashtable<String,String> env = new Hashtable<String, String>();
		if(hasValue(ldapUrl) && hasValue(mcommunityGroup)) {
			env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CTX_FACTORY);
			env.put(Context.PROVIDER_URL, ldapUrl);
			log.info(ldapUrl + " " + mcommunityGroup + " " + user);
		}else {
			log.error(" [ldap.server.url] or [mcomm.group] properties are not set");
			return isAuthorized;
		}
		try {
			dirContext = new InitialDirContext(env);
			String[] attrIDs = {"member"};
			SearchControls searchControls = new SearchControls();
			searchControls.setReturningAttributes(attrIDs);
			searchControls.setReturningObjFlag(true);
			searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
			String searchBase = OU_GROUPS;
			String filter = "(&(cn=" + mcommunityGroup + "))";
			listOfPeopleInAuthGroup = dirContext.search(searchBase, filter, searchControls);
			String positiveMatch = "uid=" + user + ",";
			outerloop:
				while (listOfPeopleInAuthGroup.hasMore()) {
					SearchResult searchResults = (SearchResult)listOfPeopleInAuthGroup.next();
					allSearchResultAttributes = (searchResults.getAttributes()).getAll();
					while (allSearchResultAttributes.hasMoreElements()){
						Attribute attr = (Attribute) allSearchResultAttributes.nextElement();
						simpleListOfPeople = attr.getAll();
						while (simpleListOfPeople.hasMoreElements()){
							String val = (String) simpleListOfPeople.nextElement();
							if(val.indexOf(positiveMatch) != -1){
								isAuthorized = true;
								break outerloop;
							}
						}
					}
				}
			return isAuthorized;
		} catch (NamingException e) {
			log.error("Problem getting attribute:" + e);
			return isAuthorized;
		}
		finally {
			try {
				if(simpleListOfPeople!=null) {
					simpleListOfPeople.close();
				}
			} catch (NamingException e) {
				log.error("Problem occurred while closing the NamingEnumeration list \"simpleListOfPeople\" list ",e);
			}
			try {
				if(allSearchResultAttributes!=null) {
					allSearchResultAttributes.close();
				}
			} catch (NamingException e) {
				log.error("Problem occurred while closing the NamingEnumeration \"allSearchResultAttributes\" list ",e);
			}
			try {
				if(listOfPeopleInAuthGroup!=null) {
					listOfPeopleInAuthGroup.close();
				}
			} catch (NamingException e) {
				log.error("Problem occurred while closing the NamingEnumeration \"listOfPeopleInAuthGroup\" list ",e);
			}
			try {
				if(dirContext!=null) {
					dirContext.close();
				}
			} catch (NamingException e) {
				log.error("Problem occurred while closing the  \"dirContext\"  object",e);
			}
		}

	}
	
	/**
	 * return true if the string value is not null or empty
	 * @param value
	 * @return
	 */
	private static boolean hasValue(String value) {
		boolean rv= value != null && !value.trim().isEmpty();
		return rv;
	}
	
	/**
	 * replace characters match the regular expression to "_"
	 * @param name
	 * @return
	 */
	public static String sanitizeName(String type, String name) {
		// fix file extension
		if (!COLLECTION_TYPE.equals(type))
		{
			name = modifyFileNameOnType(type, name);
		}
		
		// only look for ":" and "/" as of now
		Pattern p = Pattern.compile("[\\\\:\\/]");
		Matcher m = p.matcher(name);
		name = m.replaceAll("_");
		
	    return name;
	}
	
	/**
	 * If file extension is missing, look up the extension by file MIME type and add extension if found;
	 * If file extension is still missing, append ".html" for Web Link and citation type of resources
	 * @param type
	 * @param fileName
	 * @return
	 */
	public static String modifyFileNameOnType(String type, String fileName) {
		if (type != null && FilenameUtils.getExtension(fileName).isEmpty())
		{
			// do extension lookup first
			try {
				MimeType mimeType = tikaConfig.getMimeRepository().forName(type);
				if (mimeType != null) {
					String extension = mimeType.getExtension();
				    //do something with the extension
					fileName = fileName.concat(extension);
				}
			} catch (MimeTypeException e) {
				log.error("Utils.modifyFileNameOnType: Couldn't find file extension for resource: " + fileName + " of MIME type = " + type , e);
			}
		}
		String extension = FilenameUtils.getExtension(fileName);
		if ((extension.isEmpty() || !Utils.HTML_FILE_EXTENSION.equals(EXTENSION_SEPARATOR + extension)) && (Utils.CTOOLS_RESOURCE_TYPE_CITATION.equals(type) || Utils.CTOOLS_RESOURCE_TYPE_URL.equals(type)))
		{
			fileName = fileName + Utils.HTML_FILE_EXTENSION;
		}
		return fileName;
	}
	
	public static String getCopyrightAcceptUrl(String copyrightAlert,
			String contentUrl) {
		if (copyrightAlert != null)
		{
			// for copyright protected resources, we will update the access url,
			// as if user is already accepted the copyright terms
			// the format of url will be:
			// <server_url>/access/accept?ref=<resource_ref>&url=<resource_ref>
			String resource_ref = contentUrl.substring(contentUrl.indexOf("/access/") + 7);
			contentUrl = contentUrl.substring(0, contentUrl.indexOf("/access/"))
					+ "/access/accept?ref=" + resource_ref
					+ "&url=" + resource_ref;
		}
		return contentUrl;
	}

	/**
	 * CTools Web Link content is exported as a html file, with the link inside
	 * @param fileName
	 * @param fileUrl
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public static String getWebLinkContent(String fileName, String fileUrl)
	{
		try
		{
			URL url = new URL(fileName);
			// title is of URL format
			// use the title for link url 
			fileUrl = fileName;
		}
		catch (java.net.MalformedURLException e)
		{
			// title is not of url format
			// use ctools access servlet url for now
			// next CTools release will provide clean url feed
			log.info("Utils:getWebLinkContent: use CTools access url for now as URL content for URL-type resource " + fileUrl );
		}
		
		try
		{
			fileUrl = URLDecoder.decode(fileUrl, "UTF-8");
		}
		catch (UnsupportedEncodingException e)
		{
			log.error("Utils.getWebLinkContent: UnsupportedEncodingException " + e);
		}
		
		StringBuffer b = new StringBuffer();
		b.append("<a href=\"");
		b.append(fileUrl);
		b.append("\">" + fileName + "</a>");
		return b.toString();
	}
}
