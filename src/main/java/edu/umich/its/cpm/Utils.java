package edu.umich.its.cpm;

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
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public 
class Utils {
	
	// for local testing
	public static final String TEST_REMOTEUSER = "test.remoteuser";
	
	// migration status string
	public static final String STATUS_SUCCESS = "success";
	public static final String STATUS_FAILURE = "failure";
	public static final String STATUS_ONGING = "onging";

	public static final String CTOOLS_SITE_TYPE_PROJECT = "project";
	public static final String CTOOLS_SITE_TYPE_MYWORKSPACE = "myworkspace";
	public static final String CTOOLS_SITE_TYPE_MYWORKSPACE_PREFIX = "~";
	
	public static final String ROLE_OWNER = "Owner";
	public static final String ROLE_ORGANIZER = "Organizer";
	public static final String ROLE_MEMBER = "Member";
	public static final String ROLE_OBSERVER = "Observer";
	public static final String ROLE_MAINTAINER = "maintain";
	public static final String ROLE_INSTRUCTOR = "Instructor";
	public static final String ROLE_STUDENT = "Student";

	public static final String COLLECTION_TYPE = "collection";

	public static final String MIGRATION_TOOL_RESOURCE = "sakai.resources";
	public static final String MIGRATION_TOOL_EMAILARCHIVE = "sakai.mailbox";
	public static final String MIGRATION_TYPE_BOX = "box";
	public static final String MIGRATION_TYPE_ZIP = "zip";
	public static final String MIGRATION_TYPE_GOOGLE_GROUP = "google";
	public static final String MIGRATION_MAILARCHIVE_TYPE_ZIP = "mailarchive_zip";
       public static final String MIGRATION_MAILARCHIVE_TYPE_MBOX = "mailarchivezip_mbox";
	public static final String MIME_TYPE_ZIP = "application/zip";
	public static final String MAILARCHIVEZIP = "mailarchivezip";
       public static final String MAILARCHIVEMBOX = "mailarchivembox";

	public static final String MIGRATION_FILENAME = "file_name";
	public static final String REPORT_ATTR_STATUS = "status";
	public static final String MIGRATION_DATA = "data";

	// the format of folder path in box
	// e.g. https://umich.app.box.com/files/0/f/<folderId>
	public static final String BOX_FILE_PATH_URL = "https://umich.app.box.com/files/0/f/";
	public static final String BOX_CLIENT_ID = "box_client_id";
	public static final String BOX_CLIENT_SECRET = "box_client_secret";
	public static final String BOX_API_URL = "box_api_url";
	public static final String BOX_TOKEN_URL = "box_token_url";
	public static final String BOX_CLIENT_REDIRECT_URL = "box_client_redirect_uri";
	public static final String BOX_ADMIN_CLIENT_ID = "box_admin_client_id";
	public static final String BOX_ADMIN_CLIENT_SECRET = "box_admin_client_secret";
	public static final String BOX_ADMIN_ACCOUNT_ID = "box_admin_account_id";
	public static final String BOX_ID = "id";
	public static final String BOX_SECRET = "secret";

	public static final String SERVER_URL = "server_url";

	// CTools resource type strings
	public static final String CTOOLS_RESOURCE_TYPE_URL = "text/url";
	public static final String CTOOLS_FILENAME_EXTENSION_DONT_KNOW = "application/octet-stream";
	public static final String CTOOLS_RESOURCE_TYPE_URL_EXTENSION = ".URL";
	public static final String CTOOLS_RESOURCE_TYPE_CITATION = "text/html";
	public static final String CTOOLS_RESOURCE_TYPE_CITATION_URL = "/citation/";
	public static final String HTML_FILE_EXTENSION = ".html";

	// the at sign used in email address
	static final String EMAIL_AT = "@";
	static final String DEFAULT_EMAIL_MEMBER_SUFFIX = "umich.edu";
	// the path separator
	public static final String PATH_SEPARATOR = "/";
	// the extension character
	public static final String EXTENSION_SEPARATOR = ".";

	public static final String LINE_BREAK = "\n";

	private static final Logger log = LoggerFactory.getLogger(Utils.class);
    
	// config variables
	public static final String ENV_PROPERTY_ZIP_COMPRESSSION_LEVEL = "zip.compression.level";
    public static final String ENV_PROPERTY_ATTACHMENT_LIMIT = "attachment.size.limit" ;
	public static final String ENV_PROPERTY_USERNAME = "username";
	public static final String ENV_PROPERTY_PASSWORD = "password";
	public static final String ENV_PROPERTY_CTOOLS_SERVER_URL = "ctools.server.url";
	public static final String ENV_PROPERTY_TIMEOUT_MINISECOND = "timeout_minisecond";
	public static final String ENV_PROPERTY_ALLOWED_SITE_TYPES = "allowed_site_types";

	// status report attributes
	public static final String REPORT_ATTR_ITEM_STATUS = "item_Status";
	public static final String REPORT_SUCCESS_MSG = "Everything Looks Good!";
	public static final String REPORT_ATTR_TYPE = "type";
	public static final String REPORT_ATTR_TYPE_RESOURCE_ZIP = "resource zip";
	public static final String REPORT_ATTR_TYPE_RESOURCE_BOX = "resource box";
//	public static final String REPORT_ATTR_STATUS = "status";
	public static final String REPORT_ATTR_COUNTS = "counts";
	public static final String REPORT_ATTR_COUNTS_SUCCESSES = "successes";
	public static final String REPORT_ATTR_COUNTS_ERRORS = "errors";
	public static final String REPORT_ATTR_DETAILS = "details";
	public static final String REPORT_ATTR_MESSAGE = "message";
	public static final String REPORT_ATTR_ADD_MEMBERS = "add_members";
	public static final String REPORT_ATTR_ITEMS = "items";
	public static final String REPORT_ATTR_ITEM_ID = "item_id";
	// site-level status summary String
	public static final String REPORT_STATUS_OK = "OK";
	public static final String REPORT_STATUS_PARTIAL = "PARTIAL";
	public static final String REPORT_STATUS_ERROR = "ERROR";
	public static final String REPORT_ATTR_COUNT_PARTIALS = "partial_successes";
	public static final String REPORT_ATTR_ID = "id";
	public static final String REPORT_ATTR_ROLE = "role";
	public static final String HAS_CONTENT_ITEM = "hasContentItem";
	public static final String FILE_EXTENSION_BIN = "bin";
	public static final String JSON_ATTR_MAIL_SUBJECT = "subject";
    public static final String STATUS_DEPENDENCIES_BOX = "status/dependencies/box";
    public static final String STATUS_DEPENDENCIES_CTOOLS = "status/dependencies/ctools";
    public static final String REPORT_STATUS_DOWN = "DOWN";

    private static TikaConfig tikaConfig = TikaConfig.getDefaultConfig();
	//public static String GGB_GOOGLE_DOMAIN = "ggb.google.domain";

	// constant for session id
	public static final String SESSION_ID = "sessionId";
	
	// Strings for CTools MyWorkspace sites title
	public static final String CTOOLS_MYWORKSPACE_TITLE="My Workspace";
	
	// error message of no CTools site with id
	public static final String NO_CTOOLS_SITE="Cannnot find a CTools site with the site id";
	
	// error message of no resource
	public static final String NO_CTOOLS_SITE_RESOURCE="There is no resource within the site id";
	
	// MailArchive message JSON prefixes
	public static final String JSON_ATTR_MAILARCHIVE_COLLECTION = "mailarchive_collection";
	public static final String JSON_ATTR_MAIL_HEADERS = "headers";
	public static final String JSON_ATTR_MAIL_DATE = "Date: ";
	public static final String JSON_ATTR_MAIL_FROM = "From: ";
	public static final String JSON_ATTR_MAIL_SUBJECT_FROM_HEADER = "Subject: ";
	public static final String JSON_ATTR_MAIL_BODY = "body";
	public static final String JSON_ATTR_MAIL_ATTACHMENTS = "attachments";
	public static final String JSON_ATTR_MAIL_TYPE = "type";
	public static final String JSON_ATTR_MAIL_NAME = "name";
	public static final String JSON_ATTR_MAIL_URL = "url";
	public static final String MAIL_MESSAGE_FILE_NAME = "message.txt";
	public static final String MAIL_MBOX_MESSAGE_FILE_NAME = "message.mbox";

	// the max parallel processing thread for migrations
	public static final String MAX_PARALLEL_THREADS_PROP = "max_parallel_threads_prop";
	public static final int MAX_PARALLEL_THREADS_NUM = 20;

	// Google connection property names
	public static final String GGB_SERVER_NAME = "ggb.server";
	public static final String GGB_GOOGLE_GROUP_DOMAIN = "ggb.google.group.domain";
	public static final String GGB_AUTHINFO_BASICAUTH_USERNAME = "ggb.authinfo.basicauth.username";
	public static final String GGB_AUTHINFO_BASICAUTH_PASSWORD = "ggb.authinfo.basicauth.password";
	
	// the Sakai Evaluation tool id
	public static final String SAKAI_EVALUATION_TOOL_ID = "sakai.rsf.evaluation";
	
	public static final String PARAM_ITEM_STATUS = "itemStatus";
	public static final String PARAM_CONTAINER_STACK = "containerStack";
	public static final String PARAM_BOX_FOLDER_ID_STACK = "boxFolderIdStack";
	
	public static final String PARAM_ERROR_MESSAGE = "errorMessage";
	
	public static final String TOOL_NAME_RESOURCES = "Resources";
	
	public static final String BOX_BULK_UPLOAD_SEPARATOR = ";";
	
	public static final String BOX_REFRESH_TOKEN_VALID_DAYS = "box_refresh_token_valid_days";

	
	/**
	 * login into CTools and become user with sessionId
	 */
	public static HashMap<String, Object> login_becomeuser(Environment env, String remoteUser) {
		// return the session related attributes after successful login call
		HashMap<String, Object> sessionAttributes = new HashMap<String, Object>();

		// session id after login
		String sessionId = "";

		// create httpclient
		HttpClient httpClient = HttpClientBuilder.create().build();

		// store cookies in context, retain the session information
		CookieStore cookieStore = new BasicCookieStore();
		HttpContext httpContext = new BasicHttpContext();
		httpContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

		log.info("remote user is " + remoteUser);

		// here is the CTools integration prior to CoSign integration ( read
		// session user information from configuration file)
		// 1. create a session based on user id and password
		// the url should be in the format of
		// "https://server/direct/session?_username=USERNAME&_password=PASSWORD"
		String requestUrl = Utils.directCallUrl(env, "session?_username=" + env.getProperty(Utils.ENV_PROPERTY_USERNAME)
				+ "&_password=" + env.getProperty("password"), null);
		log.debug("ctools user url: {}",requestUrl);
		try {
			HttpPost postRequest = new HttpPost(requestUrl);
			postRequest.setHeader("Content-Type",
					"application/x-www-form-urlencoded");
			HttpResponse response = httpClient
					.execute(postRequest, httpContext);

			// get the status code
			int status = response.getStatusLine().getStatusCode();

			if (status != 201) {
				// if status code is not 201, there is a problem with the
				// request.
				// return error if a new CTools session could not be created
				// using
				// username and password provided
				log.info("Wrong user id or password. Cannot login to CTools "
						+ env.getProperty(ENV_PROPERTY_CTOOLS_SERVER_URL));
			} else {

				// if status code is 201 login is successful. So reuse the
				// httpContext for next requests.
				// get the session id
				sessionId = EntityUtils.toString(response.getEntity(), "UTF-8");

				if (!env.getProperty(Utils.ENV_PROPERTY_USERNAME).equals(remoteUser))
				{
					// 2. become the user based on REMOTE_USER setting after CoSign
					// integration, only if REMOTE_USER is different than the admin user
					try {
						// the url should be in the format of
						// "https://server/direct/session/SESSION_ID.json"
						requestUrl = Utils.directCallUrl(env, "session/becomeuser/" + remoteUser
								+ ".json?", sessionId);
						log.info("becomeuser url: {}",requestUrl);
	
						HttpGet getRequest = new HttpGet(requestUrl);
						getRequest.setHeader("Content-Type",
								"application/x-www-form-urlencoded");
						HttpResponse r = httpClient
								.execute(getRequest, httpContext);
	
						String resultString = EntityUtils.toString(r.getEntity(),
								"UTF-8");
						log.info("login_becomeuser status=" + resultString);
					} catch (java.io.IOException e) {
						log.error("becomeuser failed: {} e: {}",requestUrl,e.getMessage());
	
						// nullify sessionId if become user call is not successful
						sessionId = null;
					}
				}

				// populate the session related attributes
				sessionAttributes.put(SESSION_ID, sessionId);
				sessionAttributes.put("httpContext", httpContext);
			}
		} catch (java.io.IOException e) {
			log.error(requestUrl + e.getMessage());
		}

		return sessionAttributes;
	}
	
	/**
	 * login into CTools and become admin user with sessionId
	 */
	protected static HashMap<String, Object> login_become_admin(Environment env) {
		// return the session related attributes after successful login call
		HashMap<String, Object> sessionAttributes = new HashMap<String, Object>();

		// session id after login
		String sessionId = "";

		// create httpclient
		HttpClient httpClient = HttpClientBuilder.create().build();

		// store cookies in context, retain the session information
		CookieStore cookieStore = new BasicCookieStore();
		HttpContext httpContext = new BasicHttpContext();
		httpContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);

		// here is the CTools integration prior to CoSign integration ( read
		// session user information from configuration file)
		// 1. create a session based on user id and password
		// the url should be in the format of
		// "https://server/direct/session?_username=USERNAME&_password=PASSWORD"
		String requestUrl = Utils.directCallUrl(env, "session?_username=" + env.getProperty(Utils.ENV_PROPERTY_USERNAME)
				+ "&_password=" + env.getProperty("password"), null);
		try {
			HttpPost postRequest = new HttpPost(requestUrl);
			postRequest.setHeader("Content-Type",
					"application/x-www-form-urlencoded");
			HttpResponse response = httpClient
					.execute(postRequest, httpContext);

			// get the status code
			int status = response.getStatusLine().getStatusCode();

			if (status != 201) {
				// if status code is not 201, there is a problem with the
				// request.
				// return error if a new CTools session could not be created
				// using username and password provided
				log.info("Wrong user id or password. Cannot login to CTools "
						+ env.getProperty(ENV_PROPERTY_CTOOLS_SERVER_URL));
			} else {

				// if status code is 201 login is successful. So reuse the
				// httpContext for next requests.
				// get the session id
				sessionId = EntityUtils.toString(response.getEntity(), "UTF-8");
				log.info("successfully logged in as user "
						+ env.getProperty(Utils.ENV_PROPERTY_USERNAME) + " with sessionId = "
						+ sessionId);
			}
		} catch (java.io.IOException e) {
			log.error(requestUrl + e.getMessage());
		}

		sessionAttributes.put("sessionId", sessionId);
		sessionAttributes.put("httpContext", httpContext);
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

	/************* LDAP lookup ****************/
	private static final String OU_GROUPS = "ou=user groups,ou=groups,dc=umich,dc=edu";
	private static final String ALLOW_USER_URLOVERRIDE = "allow.testUser.urlOverride";
	private static final String LDAP_CTX_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";
	private static final String PROPERTY_LDAP_SERVER_URL = "ldap.server.url";
	protected static final String PROPERTY_AUTH_GROUP = "mcomm.group";
	protected static final String PROPERTY_ADMIN_GROUP = "mcomm.admin.group";
	private static final String TEST_USER = "testUser";
	private static final String SESSION_USER = "sesUser";


	public static String getCurrentUserId(HttpServletRequest request,
			Environment env) {
		// get CoSign user first
		String rvUser = getUserLoginId(request, env);

		return rvUser;

	}

	/*
	 * check whether the current user is in cpm admin MCommunity group based on
	 * ldap group membership
	 */
	public static boolean isCurrentUserCPMAdmin(HttpServletRequest request,
			Environment env) {
		// get CoSign user first
		String remoteUser = getUserLoginId(request,env);
		String admin_group_name = env.getProperty(Utils.PROPERTY_ADMIN_GROUP);
		if (admin_group_name != null && remoteUser.equals(admin_group_name)) {
			// if this is the Box admin user login
			return true;
		}

		String propLdapServerUrl = env.getProperty(PROPERTY_LDAP_SERVER_URL);
		String propAdminMCommGroup = env.getProperty(PROPERTY_ADMIN_GROUP);
		return ldapAuthorizationVerification(propLdapServerUrl,
				propAdminMCommGroup, remoteUser);
	}
	
	/*
	 * check whether the current user is in cpm admin MCommunity group based on
	 * ldap group membership
	 */
	public static boolean isCurrentUserCPMAdmin(String userId, Environment env) {
		String propLdapServerUrl = env.getProperty(PROPERTY_LDAP_SERVER_URL);
		String propAdminMCommGroup = env.getProperty(PROPERTY_ADMIN_GROUP);
		return ldapAuthorizationVerification(propLdapServerUrl,
				propAdminMCommGroup, userId);
	}


	public static String getUserLoginId(HttpServletRequest request, Environment env) {
		String remoteUser = request.getRemoteUser();
		String testUser = request.getParameter(TEST_USER);
		boolean allowTestUserOverride = ((env.getProperty(ALLOW_USER_URLOVERRIDE) == null ?
				false : Boolean.valueOf(env.getProperty(ALLOW_USER_URLOVERRIDE))));
		log.debug("************** SESSION ID: " + request.getSession().getId());


		if (!allowTestUserOverride) {
			return getOrSetUserInSession(request, remoteUser);
		}

		String userInSession = (String) request.getSession().getAttribute(SESSION_USER);
		if (remoteUser != null) {
			if (!isCurrentUserCPMAdmin(remoteUser, env)) {
				return getOrSetUserInSession(request, remoteUser);
			}

			if (testUser != null) {
				return becomeUser(request, testUser);
			}
			return getOrSetUserInSession(request, remoteUser);
		}

		if (testUser != null) {
			return becomeUser(request, testUser);
		}
		log.debug("****** session User:" + userInSession);
		return userInSession;
	}

	private static String getOrSetUserInSession(HttpServletRequest request, String remoteUser) {
		String userInSession = (String) request.getSession().getAttribute(SESSION_USER);
		if (userInSession != null) {
			log.debug("****** session User: " + userInSession);
			return userInSession;
		}
		//remote user is always accessed from session
		setUserInSession(request, remoteUser);
		log.debug("****** remote User to session:" + remoteUser);
		return remoteUser;
	}

	private static String becomeUser(HttpServletRequest request, String testUser){
		setUserInSession(request, testUser);
		return testUser;

	}

	private static void setUserInSession(HttpServletRequest request, String user) {
		request.getSession().setAttribute(SESSION_USER, user);
		log.debug("****** Became User  " + user);
	}

	public static JSONObject migrationStatusObject(String destination_type) {
		JSONObject downloadStatus = new JSONObject();
		downloadStatus.put(Utils.REPORT_ATTR_TYPE,destination_type);
		downloadStatus.put(Utils.REPORT_ATTR_STATUS, "");
		JSONObject counts = getCountJsonObj();
		downloadStatus.put(Utils.REPORT_ATTR_COUNTS,counts);
		downloadStatus.put(Utils.REPORT_ATTR_ITEMS,new JSONArray());
		return downloadStatus;
	}

	public static JSONObject getCountJsonObj() {
		JSONObject counts = new JSONObject();
		counts.put(Utils.REPORT_ATTR_COUNTS_SUCCESSES,0);
		counts.put(Utils.REPORT_ATTR_COUNTS_ERRORS,0);
		counts.put(Utils.REPORT_ATTR_COUNT_PARTIALS,0);
		return counts;
	}

	/*
	 * The Mcommunity group we have is a members-only group is one that only the
	 * members of the group can send mail to. The group owner can turn this on
	 * or off. More info on Ldap configuration
	 * http://www.itcs.umich.edu/itcsdocs/r1463/attributes-for-ldap.html#group.
	 */
	private static boolean ldapAuthorizationVerification(String ldapUrl,
			String mcommunityGroup, String user) {
		log.info("ldapAuthorizationVerification(): called");
		boolean isAuthorized = false;

		DirContext dirContext = null;
		NamingEnumeration listOfPeopleInAuthGroup = null;
		NamingEnumeration allSearchResultAttributes = null;
		NamingEnumeration simpleListOfPeople = null;
		Hashtable<String, String> env = new Hashtable<String, String>();
		if (hasValue(ldapUrl) && hasValue(mcommunityGroup)) {
			env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CTX_FACTORY);
			env.put(Context.PROVIDER_URL, ldapUrl);
			log.info(ldapUrl + " " + mcommunityGroup + " " + user);
		} else {
			log.error(" [ldap.server.url] or [mcomm.group] properties are not set");
			return isAuthorized;
		}

		try {
			DirContext ctx = new InitialDirContext(env);
			SearchControls searchControls = new SearchControls();
			searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
			String searchBase = OU_GROUPS;
			String filter = "(&(cn=" + mcommunityGroup + "))";
			listOfPeopleInAuthGroup = ctx.search(searchBase, filter,
					searchControls);
			String positiveMatch = "uid=" + user + ",";
			outerloop: while (listOfPeopleInAuthGroup.hasMore()) {

				SearchResult searchResults = (SearchResult) listOfPeopleInAuthGroup
						.next();
				allSearchResultAttributes = (searchResults.getAttributes())
						.getAll();
				while (allSearchResultAttributes.hasMoreElements()) {
					Attribute attr = (Attribute) allSearchResultAttributes
							.nextElement();
					simpleListOfPeople = attr.getAll();
					while (simpleListOfPeople.hasMoreElements()) {
						String val = (String) simpleListOfPeople.nextElement();
						if (val.indexOf(positiveMatch) != -1) {
							isAuthorized = true;
							break outerloop;
						}
					}
				}
			}
		} catch (NamingException e) {
			log.error("Problem getting attribute:" + e);
		} finally {
			try {
				if (simpleListOfPeople != null) {
					simpleListOfPeople.close();
				}
			} catch (NamingException e) {
				log.error(
						"Problem occurred while closing the NamingEnumeration list \"simpleListOfPeople\" list ",
						e);
			}
			try {
				if (allSearchResultAttributes != null) {
					allSearchResultAttributes.close();
				}
			} catch (NamingException e) {
				log.error(
						"Problem occurred while closing the NamingEnumeration \"allSearchResultAttributes\" list ",
						e);
			}
			try {
				if (listOfPeopleInAuthGroup != null) {
					listOfPeopleInAuthGroup.close();
				}
			} catch (NamingException e) {
				log.error(
						"Problem occurred while closing the NamingEnumeration \"listOfPeopleInAuthGroup\" list ",
						e);
			}
			try {
				if (dirContext != null) {
					dirContext.close();
				}
			} catch (NamingException e) {
				log.error(
						"Problem occurred while closing the  \"dirContext\"  object",
						e);
			}
		}
		return isAuthorized;
	}

	/**
	 * return true if the string value is not null or empty
	 *
	 * @param value
	 * @return
	 */
	private static boolean hasValue(String value) {
		boolean rv = value != null && !value.trim().isEmpty();
		return rv;
	}

	/**
	 * Replace characters match( /,\,<,>,*, | \tab) the regular expression to "_" as some of these are not allowed
	 * as part of Folder/files names by Operating system and Box. The double back slashes are user for escaping and the
	 * beginning backslash actually escaping the \ and not looking for \\ in a string
	 *
	 * @param name
	 * @return
	 */
	public static String sanitizeName(String type, String name) {
		String oldName = name;
		
		// fix file extension
		if (!COLLECTION_TYPE.equals(type)) {
			name = modifyFileNameOnType(type, name);
		}
		if (name == null)
		{
			log.error(" sanitizeName to be null for name=" + oldName);
			return null;
		}
		Pattern p = Pattern.compile("[\\\\:\\/\\>\\<\\*\\|\\?]");
		Matcher m = p.matcher(name);
		name = m.replaceAll("_");
		name=name.replace("\t", " ");

		return name;
	}
    // Windows Explorer don't like below special characters in Folder names so zip extraction fails. Replacing with _
	public static String sanitizeFolderNames(String zipFileName) {
		// double backslash actually escaping the \ and not looking for \\ in a string. it will replace all the
		// backslash in the string.
		zipFileName=zipFileName.replace('\\','_');
		zipFileName=zipFileName.replace("\t", " ");
		return zipFileName.replaceAll("[?>|*<:]", "_");
	}

	/**
	 * If file extension is missing, look up the extension by file MIME type and
	 * add extension if found; If file extension is still missing, append
	 * ".html" for Web Link and citation type of resources
	 *
	 * @param type
	 * @param fileName
	 * @return
	 */
	public static String modifyFileNameOnType(String type, String fileName) {
		String fileExtension = FilenameUtils.getExtension(fileName);
		if ((fileExtension == null
			|| fileExtension.isEmpty()
			|| !Utils.HTML_FILE_EXTENSION.equals(EXTENSION_SEPARATOR + fileExtension))
			&& 
			(Utils.CTOOLS_RESOURCE_TYPE_CITATION.equals(type)
			|| Utils.isOfURLMIMEType(type))) {
				// handle citation and URL type
				fileName = fileName.replace(".", "_");
				fileName = fileName + Utils.HTML_FILE_EXTENSION;
				return fileName;
		}
		// The .sav have MIME type seems to have application/octetstream which will results .bin extension
		// so adding a check and return if that is the case. https://itsjira.umms.med.umich.edu/browse/TLCPM-641

		if(fileExtension.equals("sav")){
			return fileName;
		}
		
		if (type != null) {
			String mimeExtension = null;
			// do extension lookup first
			// based on MIME type
			try {
				MimeType mimeType = tikaConfig.getMimeRepository()
						.forName(type);
				if (mimeType != null) {
					mimeExtension = mimeType.getExtension();
				}
			} catch (MimeTypeException e) {
				log.error(
						"Utils.modifyFileNameOnType: Couldn't find file extension for resource: "
								+ fileName + " of MIME type = " + type, e);
			}
			// Mime extension will be empty because the Mime type mentioned in the ctools might be invalid/old and the
			// tikka library is not supporting it. so this workflow is supporting invalid/old mime type from ctools.
			// unit test are added to testMimeExtensionLogic()
			if (mimeExtension.isEmpty()) {
				return guessedFileExtension(type, fileName);
			}
			Tika tika = new Tika();

			if (mimeExtension != null && tika.detect(fileName).equals(CTOOLS_FILENAME_EXTENSION_DONT_KNOW)) {
					// if file name extension is missing add the extension to file name
				if (!fileExtension.equals(FILE_EXTENSION_BIN)) {
					fileName = fileName.concat(mimeExtension);
				}
				if (StringUtils.countOccurrencesOf(fileName, ".") > 1) {
					fileName = FilenameUtils.getBaseName(fileName).replace(".", "_");
					fileName = fileName.concat(mimeExtension);
					return fileName;
				}
				}
			}
		return fileName;
	}

	public static String guessedFileExtension(String type, String fileName) {
		MimeTypesChunk mimeTypes = null;
		for (MimeTypesChunk mimeType : MimeTypesChunk.values()) {
			if (type.toLowerCase().contains(mimeType.toString())) {
				mimeTypes = mimeType;
				break;
			}
		}
		if (mimeTypes == null) {
			return fileName;
		}
		String guessedFileExtension = getGuessedFileExtension(mimeTypes);
		if (guessedFileExtension != null) {

			String fileExtension = FilenameUtils.getExtension(fileName);
			if (fileExtension.contains(guessedFileExtension.replace(".", ""))) {
				return fileName;
			}

			fileName = fileName.concat(guessedFileExtension);
			return fileName;
		}
		return fileName;
	}

	// will add more Mime types on case by case basic
	public enum MimeTypesChunk {
		POWERPOINT("powerpoint"), EXCEL("excel"), WORD("word"), JPEG("jpeg"), TEXTPLAIN("text/plain");
		private final String mimeType;

		MimeTypesChunk(String mimeType) {
			this.mimeType = mimeType;
		}

		@Override
		public String toString() {
			return mimeType;
		}
	}

	private static final Map<MimeTypesChunk, String> mimeMap;
	static
	{
		mimeMap = new HashMap<MimeTypesChunk, String>();
		mimeMap.put(MimeTypesChunk.POWERPOINT, ".ppt");
		mimeMap.put(MimeTypesChunk.EXCEL, ".xls");
		mimeMap.put(MimeTypesChunk.WORD, ".doc");
		mimeMap.put(MimeTypesChunk.JPEG, ".jpeg");
		mimeMap.put(MimeTypesChunk.TEXTPLAIN, ".txt");
	}

	public static String getGuessedFileExtension(MimeTypesChunk type) {
		String fileExtension = null;

		for (MimeTypesChunk mimeType : mimeMap.keySet()) {
			if (mimeType.equals(type)) {
				fileExtension = mimeMap.get(mimeType);
				break;
			}
		}
		return fileExtension;
	}


	public static String getCopyrightAcceptUrl(String copyrightAlert,
			String contentUrl) {
		if (copyrightAlert != null) {
			// for copyright protected resources, we will update the access url,
			// as if user is already accepted the copyright terms
			// the format of url will be:
			// <server_url>/access/accept?ref=<resource_ref>&url=<resource_ref>
			String resource_ref = contentUrl.substring(contentUrl
					.indexOf("/access/") + 7);
			contentUrl = contentUrl
					.substring(0, contentUrl.indexOf("/access/"))
					+ "/access/accept?ref="
					+ resource_ref
					+ "&url="
					+ resource_ref;
		}
		return contentUrl;
	}

	/**
	 * change folder path based on updated folder title
	 *
	 * @param folderNameUpdates
	 * @param title
	 * @param folderName
	 * @return
	 */
	public static HashMap<String, String> updateFolderNameMap(
			HashMap<String, String> folderNameUpdates, String title,
			String folderName) {
		// update folder name if there is any parent folder renaming
		// checks for folder name updates in the path
		// replace all old folder title with new title
		if (title.contains("/")) {
			title = title.replace('/', '_');
		}
		String currentFolderName = folderName;
		for (String oldFolderName : folderNameUpdates.keySet()) {
			if (folderName.startsWith(oldFolderName)) {
				folderName = folderName.replace(oldFolderName,
						folderNameUpdates.get(oldFolderName));
			}
		}
		// now checks whether the current folder is renamed
		if (!folderName.endsWith(title + PATH_SEPARATOR)) {
			// save the parent folder path
			// remove the trailing "/" from folder name first
			String parentFolder = folderName.substring(0,
					folderName.length() - 1);
			if (parentFolder.contains(PATH_SEPARATOR)) {
				// get the parent folder
				parentFolder = parentFolder.substring(0,
						parentFolder.lastIndexOf(PATH_SEPARATOR) + 1);
			} else {
				// top level folder
				parentFolder = "";
			}
			// update folder name
			folderName = parentFolder + title + PATH_SEPARATOR;
		}
		if (!currentFolderName.equals(folderName)) {
			// put the old and new folder name into map
			folderNameUpdates.put(currentFolderName, folderName);
		}
		return folderNameUpdates;
	}

	/**
	 * CTools Web Link content is exported as a html file, with the link inside
	 *
	 * @param fileName
	 * @param fileUrl
	 * @return
	 * @throws UnsupportedEncodingException
	 */
	public static String getWebLinkContent(String fileName, String fileUrl)
			throws MalformedURLException {
		try {
			fileUrl = URLDecoder.decode(fileUrl, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			log.error("Utils.getWebLinkContent: UnsupportedEncodingException "
					+ e);
		}

		StringBuffer b = new StringBuffer();
		b.append("<a href=\"");
		b.append(fileUrl);
		b.append("\">" + fileName + "</a>");
		return b.toString();
	}

	/*
	 * update the folder title in file name string
	 *
	 * @param fileName
	 *
	 * @param folderNameMap
	 *
	 * @return
	 */
	public static String updateFolderPathForFileName(String fileName,
			HashMap<String, String> folderNameMap) {
		// navigate the folder container backwards based on the file name
		String parentFolder = fileName.substring(0,
				fileName.lastIndexOf(PATH_SEPARATOR) + 1);
		while (parentFolder != null) {
			// checks for folder name updates in the path
			// replace all old folder title with new title
			if (folderNameMap.containsKey(parentFolder)) {
			        fileName = fileName.replace(parentFolder,
			                folderNameMap.get(parentFolder));
			    break;
			}

			// get the next parent folder
			// remove the trailing "/"
			if (parentFolder.endsWith(PATH_SEPARATOR)) {
				parentFolder = parentFolder.substring(0,
						parentFolder.length() - 1);
			}

			if (parentFolder.contains(PATH_SEPARATOR)) {
				parentFolder = parentFolder.substring(0,
						parentFolder.lastIndexOf(PATH_SEPARATOR) + 1);
			} else {
				parentFolder = null;
			}
		}
		return fileName;
	}

	/**
	 * check whether the given type is of URL MIME type
	 * @param type
	 * @return
	 */
	public static boolean isOfURLMIMEType(String type)
	{
		return CTOOLS_RESOURCE_TYPE_URL.equalsIgnoreCase(type);
	}

    public static boolean isItMailArchiveZip(String destination_type) {
        return destination_type.equals(MAILARCHIVEZIP);
    }

    public static boolean isItMailArchiveMbox(String destination_type) {
        return destination_type.equals(MAILARCHIVEMBOX);
    }

    /**
     * construct the /direct call URL
     * @param env
     * @param urlPart
     * @param sessionId
     * @return
     */
    public static String directCallUrl(Environment env, String urlPart, String sessionId) {
        String rv = env.getProperty(ENV_PROPERTY_CTOOLS_SERVER_URL)
				+ "direct/" + urlPart;
        if (sessionId != null)
        {
        	rv = rv + "_sessionId=" + sessionId;
        }
        return rv;
    }

	public static String dependencyStatus(String url){
        log.debug("*******************" + url);
        String status = REPORT_STATUS_DOWN;
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = null;
        try {
            response = restTemplate.getForEntity(url, String.class);
        } catch (RestClientException e) {
            log.warn("GET call {} failed due to {} ", url, e.getMessage());
        }

        if (response != null && response.getStatusCode().equals(HttpStatus.OK)) {
            status = REPORT_STATUS_OK;
        }
        HashMap<String, Object> statusMap = new HashMap<String, Object>();
        statusMap.put("status", status);
        return (new JSONObject(statusMap)).toString();
    }




}
