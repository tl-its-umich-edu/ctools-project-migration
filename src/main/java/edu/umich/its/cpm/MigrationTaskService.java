package edu.umich.its.cpm;

import org.apache.commons.io.FilenameUtils;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Queue;
import java.util.LinkedList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxFolder;
import com.box.sdk.Metadata;
import com.box.sdk.ProgressListener;
import com.box.sdk.BoxItem.Info;

@Service
@Component
public
class MigrationTaskService {

	@Autowired
	BoxAuthUserRepository uRepository;

	@Autowired
	MigrationBoxFileRepository fRepository;

	@Autowired
	MigrationEmailMessageRepository mRepository;
	
	@Autowired
	MigrationRepository migrationRepository;

	@Autowired
	private Environment env;

	// String values used in content json feed
	private static final String CTOOLS_ACCESS_STRING = "/access/content";
	private static final String CTOOLS_CITATION_ACCESS_STRING = "/access/citation/content";
	private static final String CTOOLS_CONTENT_STRING = "/content";
	public static final String CONTENT_JSON_ATTR_CONTENT_COLLECTION = "content_collection";
	private static final String CONTENT_JSON_ATTR_CONTAINER = "container";
	private static final String CONTENT_JSON_ATTR_TITLE = "title";
	private static final String CONTENT_JSON_ATTR_TYPE = "type";
	private static final String CONTENT_JSON_ATTR_URL = "url";
	private static final String CONTENT_JSON_ATTR_DESCRIPTION = "description";
	private static final String CONTENT_JSON_ATTR_AUTHOR = "author";
	private static final String CONTENT_JSON_ATTR_COPYRIGHT_ALERT = "copyrightAlert";
	private static final String CONTENT_JSON_ATTR_WEB_LINK_URL = "webLinkUrl";
	private static final String CONTENT_JSON_ATTR_SIZE = "size";

	// true value used in entity feed
	private static final String BOOLEAN_TRUE = "true";

	// integer value of stream operation buffer size
	private static final int STREAM_BUFFER_CHAR_SIZE = 1024;

	// Box has a hard limit of 5GB per any single file
	// use the decimal version of GB here, smaller than the binary version
	private static final long MAX_CONTENT_SIZE_FOR_BOX = 5L * 1024 * 1024 * 1024;
	
	private Queue<String> migrationSiteQueue = new LinkedList<String>();
	
	/**
	 * put siteId into the migration queue
	 * @param siteId
	 */
	public void queueMigrationSiteId(String siteId)
	{
		migrationSiteQueue.add(siteId);		
	}
	
	/**
	 * 
	 * put list of siteId into the migration queue
	 * @param siteIds
	 */
	public void queueMigrationSiteIds(Set<String> siteIds)
	{
		migrationSiteQueue.addAll(siteIds);		
	}
	
	/**
	 * get first siteId in the migration queue
	 * @return
	 */
	public String pollMigrationSiteId()
	{
		return migrationSiteQueue.poll();	
	}
	
	public void handleWithBatchFileMigrationRequest() {
		// for box migration usage
		HashMap<String, String> siteBoxMigrationIdMap = new HashMap<String, String>();
		
		if (!migrationSiteQueue.isEmpty()) {
			// now that we get the site ids for batch upload
			// start the batch process
			String bulkMigrationId = java.util.UUID.randomUUID().toString();

			// duplicate the queue for resource folder process
			Queue<String> dup_migrationSiteQueue = new LinkedList<String>();
			dup_migrationSiteQueue.addAll(migrationSiteQueue);
			
			while (!dup_migrationSiteQueue.isEmpty()) {
				String siteAttributesString = dup_migrationSiteQueue.poll();
				String[] siteAttributes = siteAttributesString.split(Utils.BOX_BULK_UPLOAD_SEPARATOR);
				if (siteAttributes.length != 6)
				{
					// wrong format
					continue;
				}
	
				String siteId = siteAttributes[0];
				String sessionId = siteAttributes[1];
				String userId = siteAttributes[2];
				String ctoolsAdminUserName = siteAttributes[3];
				String bulkMigrationName =  siteAttributes[4];
				String migrationToolId = siteAttributes[5];
				
				// for each site id, start the migration process
				// associate it with the bulk id
				// 1. get site name
				String siteName = getSiteName(siteId, sessionId);
				if (siteName == null)
				{
					// if the site id is invalid, and we cannot find the site
					// generate an empty migration record with error and move on
					String errorMessage = Utils.NO_CTOOLS_SITE + " " + siteId/*site name*/;
					handleBulkResourceBoxRootFolderError(userId, bulkMigrationName,
							bulkMigrationId, siteId, siteName, "default_resource_tool_id",
							Utils.TOOL_NAME_RESOURCES, errorMessage);
					continue;
				}
				
				try
				{
					// add admin user to site with Owner role
					// will remove the user from site later once migration is done.
					addAdminAsSiteOwner(ctoolsAdminUserName, siteId, sessionId);
					
					String toolId = "";
					String toolName = "";
	
					// 2. get tool id, tool name
					HashMap<String, String> pagesMap = get_user_project_site_tools(siteId, sessionId);
					String pagesString = pagesMap.get("pagesString");
					JSONArray pagesJSON = new JSONArray(pagesString);
					for (int pageIndex = 0; pageIndex < pagesJSON.length(); pageIndex++) {
						JSONObject pageJSON = (JSONObject) pagesJSON.get(pageIndex);
						// look the tools attribute and find resource tool
						JSONArray toolsJSON = (JSONArray) pageJSON.get("tools");
						for (int toolIndex = 0; toolIndex < toolsJSON.length(); toolIndex++) {
							JSONObject toolJSON = (JSONObject) toolsJSON
									.get(toolIndex);
							if (migrationToolId.equals(toolJSON.get("toolId"))) {
								// found Resources tool
								toolId = (String) toolJSON.get("id");
								toolName = (String) toolJSON.get("title");
							}
						}
					}
					
					if (migrationToolId.equals(Utils.MIGRATION_TOOL_RESOURCE))
					{
						// check to see whether the site has any resource
						if (checkSiteWithEmptyResourceList(sessionId, userId, bulkMigrationName,
								bulkMigrationId, siteId,
								siteName, toolId, toolName))
						{
							// stop here if site has no resource
							// remove added admin user
							removeAddedAdminOwner(siteId);
							continue;
						}
						
						// bulk migration of resource items into Box
						siteBoxMigrationIdMap = createRootBoxFolderWithMembers(sessionId,
								userId, bulkMigrationName,
								bulkMigrationId, siteId, siteName, toolId, toolName, siteBoxMigrationIdMap);
					}
					else if (migrationToolId.equals(Utils.MIGRATION_TOOL_EMAILARCHIVE))
					{
						// DO NOTHING FOR NOW, since we are not doing email migration
						// bulk migration of email archive messages into Google Groups
						/*handleBulkMessageGoogleMigration(sessionId,
								request, response, userId, bulkMigrationName,
								bulkMigrationId, siteId, siteName, toolId, toolName);
						*/
					}
					else
					{
						// wrong tool
						log.error(" unrecognized migration tool " + migrationToolId);
					}
				}
				catch (Exception e)
				{
					log.info("uploadBatch exception " + e.getMessage() + " for site " + siteId);
					
					// remove added admin user
					removeAddedAdminOwner(siteId);
				}
			}
			
			// now that we finished all box root folder creation
			// we are ready to do file content tasks
			String boxFolderId = null;
			String migrationId = null;
			
			// get the Box Admin Id
			String userId = env.getProperty(Utils.BOX_ADMIN_ACCOUNT_ID);
			while (!migrationSiteQueue.isEmpty())
			{
				String siteAttributesString = migrationSiteQueue.poll();
				String[] siteAttributes = siteAttributesString.split(Utils.BOX_BULK_UPLOAD_SEPARATOR);
				if (siteAttributes.length != 6)
				{
					// wrong format
					continue;
				}
				String siteId = siteAttributes[0];
				
				if (siteBoxMigrationIdMap != null && siteBoxMigrationIdMap.containsKey(siteId + "_boxRootFolderId")) {
					boxFolderId = siteBoxMigrationIdMap.get(siteId + "_boxRootFolderId");
				}
				if (siteBoxMigrationIdMap != null &&siteBoxMigrationIdMap.containsKey(siteId + "_migrationId")) {
					migrationId = siteBoxMigrationIdMap.get(siteId + "_migrationId");
				}
				if (boxFolderId != null && migrationId != null) {
					// delegate the actual content migrations to async calls
					HashMap<String, String> status = createBoxMigrationTask(userId, 
							boxFolderId, siteId, migrationId);
					log.info(this + " batch upload call for site id=" + siteId
							+ " migration id=" + (status.containsKey("migrationId")?status.get("migrationId"):"")
							+ " error message=" + (status.containsKey("errorMessage")?status.get("errorMessage"):""));
				}
			}
		}
	}
	
	/**
	 * use CTools entity feed to get site information based on site id
	 * 
	 */
	private String getSiteName(String siteId, String sessionId) {
		String siteName = null;
		RestTemplate restTemplate = new RestTemplate();
		// the url should be in the format of
		// "https://server/direct/site/<siteId>.json?_sessionId=<sessionId>"
		String requestUrl = Utils.directCallUrl(env, "site/" + siteId + ".json?", sessionId);
		log.info(this + requestUrl);
		try {
			String siteJson = restTemplate.getForObject(requestUrl,
					String.class);
			JSONObject siteJSONObject = new JSONObject(siteJson);
			siteName = (String) siteJSONObject.get("title");
		} catch (RestClientException e) {
			log.error(requestUrl + e.getMessage());
		}
		return siteName;
	}
	
	public void handleBulkResourceBoxRootFolderError(String userId,
			String bulkMigrationName, String bulkMigrationId, String siteId,
			String siteName, String toolId, String toolName, String errorMessage) {
		// error message saved into status
		// the status json object
		JSONObject uploadStatus = new JSONObject();
		// count
		JSONObject count = new JSONObject();
		count.put(Utils.REPORT_ATTR_COUNTS_ERRORS, 1);
		count.put(Utils.REPORT_ATTR_COUNTS_SUCCESSES, 0);
		uploadStatus.put(Utils.REPORT_ATTR_COUNTS,count);
		// details
		JSONObject details = new JSONObject();
		details.put(Utils.REPORT_ATTR_MESSAGE, errorMessage);
		uploadStatus.put(Utils.REPORT_ATTR_DETAILS, details);
		// type
		uploadStatus.put(Utils.REPORT_ATTR_TYPE, Utils.REPORT_ATTR_TYPE_RESOURCE_BOX);
		// status
		uploadStatus.put(Utils.REPORT_ATTR_STATUS, Utils.REPORT_STATUS_ERROR);
		
		Timestamp now = new java.sql.Timestamp(System.currentTimeMillis());
		
		Migration m = new Migration(bulkMigrationId, bulkMigrationName, 
				siteId, siteName, toolId, toolName,
				userId, now, now,
				Utils.MIGRATION_TYPE_BOX, null, uploadStatus.toString());
		try {
			migrationRepository.save(m);
		} catch (Exception e) {
			log.error("Exception saving migraion record " + errorMessage + " with error for " + siteName
					+ e.getMessage());
		}
	}
	
	/**
	 * add user with Owner role into CTools site
	 * @param adminUserId
	 * @param siteId
	 * @param sessionId
	 */
	private void addAdminAsSiteOwner(String adminUserId, String siteId, String sessionId)
	{
		HashMap<String, Object> sessionAttributes = Utils.login_become_admin(env);
	        
		if(sessionAttributes.isEmpty()){
			log.error("Logging into Ctools failed for the admin user.");
		}
		String adminSessionId = (String) sessionAttributes.get(Utils.SESSION_ID);
		// the request string to add user to site with Owner role
		String requestUrl = Utils.directCallUrl(env, "membership/site/" + siteId + "?userSearchValues=" + adminUserId + "&memberRole=" + Utils.ROLE_OWNER + "&", adminSessionId);
     	
		HttpContext httpContext = (HttpContext) sessionAttributes.get("httpContext");
		HttpClient httpClient = HttpClientBuilder.create().build();
		try {
			HttpPost request = new HttpPost(requestUrl);
			request.setHeader("Content-Type", "application/x-www-form-urlencoded");
			HttpResponse response = httpClient.execute(request, httpContext);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != 200) {
				log.error(String.format("Failure to add user \"%1$s\" as Owner to site %2$s ", adminUserId, siteId ));
			}
		} catch (IOException e) {
		    log.error(String.format("Failure to add user \"%1$s\" as Owner to site %2$s ", adminUserId, siteId) + e);
		}
	}
	
	/**
	 * REST API call to get CTools site pages and tools
	 *
	 * @param site_id
	 * @return
	 */
	public HashMap<String, String> get_user_project_site_tools(String site_id, String sessionId) {
		// default the value of checkContentItem to be false
		// when we do not need to check for whether site has any resource items
		return get_user_project_site_tools(site_id, sessionId, false);
	}


	/**
	 * REST API call to get CTools site pages and tools
	 *
	 * @param site_id
	 * @return
	 */
	public HashMap<String, String> get_user_project_site_tools(String site_id, String sessionId, boolean checkContentItem) {
		HashMap<String, String> rv = new HashMap<String, String>();

		String pagesString = "";
		String errorMessage = "";
		String requestUrl = "";

		// get all pages inside site
		// the url should be in the format of
		// "https://server/direct/site/SITE_ID/pages.json"
		RestTemplate restTemplate = new RestTemplate();
		requestUrl = Utils.directCallUrl(env, "site/" + site_id + "/pages.json?", sessionId);
		log.info(requestUrl);
		try {
			pagesString = restTemplate.getForObject(requestUrl,
					String.class);
		} catch (RestClientException e) {
			errorMessage = "Cannot find site pages by siteId: " + site_id
					+ " " + e.getMessage();
			log.error(errorMessage);
		}

		if (pagesString.isEmpty())
		{
			// generate error when there is no JSON feed for site pages
			errorMessage += "Cannot find site pages by siteId: " + site_id + ". Maybe user do not have access to that site?";
		}
		else if (checkContentItem)
		{
			pagesString = siteHasContentItems(site_id, pagesString, sessionId);
		}

		rv.put("pagesString", pagesString);
		rv.put("errorMessage", errorMessage);
		rv.put("requestUrl", requestUrl);
		return rv;
	}
	
	/**
	 * adds an extra "hasContentItem" JSON element to the site tool JSON feed.
	 * True if site has content resources; false if the site content is empty.
	 *
	 * @param site_id
	 * @param pagesString
	 * @param sessionId
	 * @return
	 */
	private String siteHasContentItems(String site_id, String pagesString,
			String sessionId) {
		JSONArray rvSitePagesArray = new JSONArray();

		RestTemplate restTemplate;
		JSONArray pages = new JSONArray(pagesString);
		// iterate through all pages
		for (int iPage = 0; pages != null && iPage < pages.length(); ++iPage) {
			JSONObject page = pages.getJSONObject(iPage);
			if (page.has("tools")) {
				// iterate through all tools within page
				JSONArray tools = (JSONArray) page.get("tools");
				for (int iTool = 0; tools != null && iTool < tools.length(); ++iTool) {
					JSONObject tool = tools.getJSONObject(iTool);
					if (tool != null && tool.has("toolId")
							&& "sakai.resources".equals(tool.get("toolId"))) {
						// found Resource tool
						restTemplate = new RestTemplate();
						String resourceToolRequestUrl = Utils.directCallUrl(env, "content/site/" + site_id + ".json?", sessionId);

						try {
							JSONObject resourceToolResultString = new JSONObject(
									restTemplate.getForObject(
											resourceToolRequestUrl,
											String.class));
							if (resourceToolResultString != null
									&& resourceToolResultString
											.has("content_collection")) {
								// find the resource elements in
								// content_collection
								JSONArray resourceList = (JSONArray) resourceToolResultString
										.get("content_collection");
								// insert the "hasContentItem" attribute to JSON
								// object
								page.put(Utils.HAS_CONTENT_ITEM, resourceList != null
										&& resourceList.length() > 1);
							}
						} catch (RestClientException e) {
							log.error("Cannot find site content by siteId: "
									+ site_id + " " + e.getMessage());
						}
					}

					if (tool != null && tool.has("toolId")
							&& "sakai.mailbox".equals(tool.get("toolId"))) {
						// found email archive tool
						restTemplate = new RestTemplate();
						String emailArchiveRequestUrl = env
								.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
								+ "direct/mailarchive/siteMessages/"
								+ site_id
								+ ".json?_sessionId=" + sessionId;

						try {
							JSONObject emailArchiveToolResultString = new JSONObject(
									restTemplate.getForObject(
											emailArchiveRequestUrl,
											String.class));
							if (emailArchiveToolResultString != null
									&& emailArchiveToolResultString
									.has("mailarchive_collection")) {
								// find the email messages in
								// mailarchive_collection
								JSONArray emailMsgs = (JSONArray) emailArchiveToolResultString
										.get("mailarchive_collection");
								// insert the "hasContentItem" attribute to JSON
								// object
								page.put(Utils.HAS_CONTENT_ITEM, emailMsgs != null && emailMsgs.length() > 0);
							}
						} catch (RestClientException e) {
							log.error("Cannot find site content by siteId: "
									+ site_id + " " + e.getMessage());
						}
					}
				}
			}
			rvSitePagesArray.put(iPage, page);
		}
		return rvSitePagesArray.toString();
	}
	
	/**
	 * returns true if site has no resource item
	 * @param sessionId
	 * @param userId
	 * @param bulkMigrationName
	 * @param bulkMigrationId
	 * @param siteId
	 * @param siteName
	 * @param toolId
	 * @param toolName
	 * @return
	 */
	private boolean checkSiteWithEmptyResourceList(String sessionId,
			String userId, String bulkMigrationName,
			String bulkMigrationId, String siteId,
			String siteName, String toolId, String toolName) {
		// assume site has resource
		boolean hasZeroResource = false;
		
		// check whether the site has content
		RestTemplate template = new RestTemplate();
		String url = Utils.directCallUrl(env, "content/site/" + siteId + ".json?", sessionId);
		String siteResourceJson = null;
		try {
			siteResourceJson = template.getForObject(url,
					String.class);
			JSONObject obj = new JSONObject(siteResourceJson);
			JSONArray array = obj.getJSONArray(MigrationTaskService.CONTENT_JSON_ATTR_CONTENT_COLLECTION);
			if (array.length() == 1)
			{
				// There is only one element about the Site root folder,
				// which means that site does not have any resources.
				// No need to create an empty Box folder for it.
				// Simply save error message in migration record and exit.
				String errorMessage = "Site " + siteName + "(id=" + siteId + ") contains zero resources.";
				log.error(errorMessage);
				handleBulkResourceBoxRootFolderError(userId, bulkMigrationName,
						bulkMigrationId, siteId, siteName, "default_resource_tool_id",
						Utils.TOOL_NAME_RESOURCES, errorMessage);
				hasZeroResource = true;
			}

		} catch (RestClientException e) {
			String errorMessage = "Cannot find site by siteId: " + siteId
					+ " " + e.getMessage();
			log.error(errorMessage);
			handleBulkResourceBoxRootFolderError(userId, bulkMigrationName,
					bulkMigrationId, siteId, siteName, "default_resource_tool_id",
					Utils.TOOL_NAME_RESOURCES, errorMessage);
			hasZeroResource = true;
		} catch (Exception e) {
			String errorMessage = "Migration status for " + siteId + " "
					+ e.getClass().getName();
			log.error("uploadToBox ", e);
			handleBulkResourceBoxRootFolderError(userId, bulkMigrationName,
					bulkMigrationId, siteId, siteName, "default_resource_tool_id",
					Utils.TOOL_NAME_RESOURCES, errorMessage);
			hasZeroResource = true;
		}
		return hasZeroResource;
	}
	
	/**
	 * // cleanup the added owner of admin user from CTools site
	 * @param migrationId
	 */
	public void removeAddedAdminOwner(String siteId) {
		String adminUser = env.getProperty(Utils.ENV_PROPERTY_USERNAME);
		
		HashMap<String, Object> sessionAttributes = Utils.login_become_admin(env);
		
		if(sessionAttributes.isEmpty()){
			log.error("Logging into Ctools failed for the admin user.");
		}
		
		String adminSessionId = (String) sessionAttributes.get(Utils.SESSION_ID);
		// the request string to add user to site with Owner role
		String requestUrl = Utils.directCallUrl(env, "membership/" + adminUser + "::site:" + siteId + "?userIds=" + adminUser + "&", adminSessionId);
		
		HttpContext httpContext = (HttpContext) sessionAttributes.get("httpContext");
		HttpClient httpClient = HttpClientBuilder.create().build();
		try {
		    HttpDelete request = new HttpDelete(requestUrl);
		    request.setHeader("Content-Type", "application/x-www-form-urlencoded");
			HttpResponse response = httpClient.execute(request, httpContext);
			int statusCode = response.getStatusLine().getStatusCode();
			if (statusCode != 204) {
			    log.error(String.format("Failure to remove user \"%1$s\" from site %2$s ", adminUser, siteId ));
			    return;
			}
		} catch (IOException e) {
		    log.error(String.format("IOException Failure to remove user \"%1$s\" from site %2$s ", adminUser, siteId) + e);
		}
	}
	
	/**
	 * create the actual migration tasks
	 *
	 * @param request
	 * @param response
	 * @param target
	 * @param remoteUser
	 * @param rv
	 * @param parameterMap
	 * @param siteId
	 * @param toolId
	 * @param saveMigration
	 * @return
	 */
	private HashMap<String, String> createBoxMigrationTask(String remoteUser, 
			String boxFolderId, String siteId, String migrationId) {
		HashMap<String, String> rv = new HashMap<String, String>();
		
		// exit if there is no new Migration record saved into DB
		if (migrationId == null || migrationId.isEmpty() ) {
			// no new Migration record created
			rv.put("errorMessage", "Cannot create migration records for user "
					+ remoteUser + " and site=" + siteId);
			return rv;
		}
		
		// continue if there is a migrationId param
		HashMap<String, Object> sessionAttributes = Utils
				.login_becomeuser(env, remoteUser);

		StringBuffer boxMigrationErrors = new StringBuffer();
		
		// call asynchronous method for Box file upload
		log.info("start to assign Box migration file tasks asynch for siteId=" + siteId);

		// need to create all folders first
		// get box client id and secret
		String boxClientId = env.getProperty(Utils.BOX_CLIENT_ID);
		String boxClientSecret = env.getProperty(Utils.BOX_CLIENT_SECRET);
		String boxClientRedirectUrl = env.getProperty(Utils.BOX_CLIENT_REDIRECT_URL);
		String boxAPIUrl = env.getProperty(Utils.BOX_API_URL);
		// need to have all Box app configurations
		if (boxClientId == null || boxClientSecret == null) {
			String boxClientIdError = "Missing Box integration parameters (Box client id, client secret)";
			log.error(boxClientIdError);
			boxMigrationErrors.append(boxClientIdError + Utils.LINE_BREAK);
			rv.put("errorMessage", boxMigrationErrors.toString());
			return rv;
		}
		
		String remoteUserEmail = getUserEmailFromUserId(remoteUser);

		if (siteId == null || boxFolderId == null) {
			String boxFolderIdError = "Missing params for CTools site id, or target Box folder id.";
			log.error(boxFolderIdError);
			boxMigrationErrors.append(boxFolderIdError + Utils.LINE_BREAK);
			rv.put("errorMessage", boxMigrationErrors.toString());
			return rv;
		}

		// return if sessionId is missing
		if (!sessionAttributes.containsKey(Utils.SESSION_ID)) {
			 String errorBecomeUser = "Problem become user to " + remoteUser;
			 log.error(errorBecomeUser);
			 boxMigrationErrors.append(errorBecomeUser + Utils.LINE_BREAK);
			 rv.put("errorMessage", boxMigrationErrors.toString());
			 return rv;
		}
		
		String sessionId = (String) sessionAttributes.get(Utils.SESSION_ID);
		HttpContext httpContext = (HttpContext) sessionAttributes
				.get("httpContext");

		// get the site resource list
		RestTemplate restTemplate = new RestTemplate();
		// the url should be in the format of
		// "https://server/direct/site/SITE_ID.json"
		String requestUrl = Utils.directCallUrl(env, "content/site/" + siteId + ".json?", sessionId);
		String siteResourceJson = null;
		try {
			siteResourceJson = restTemplate.getForObject(requestUrl,
					String.class);
			// proceed with uploading resource to Box
			boxUploadSiteContent(migrationId, httpContext, remoteUserEmail,
				sessionId, siteResourceJson, boxFolderId);

		} catch (RestClientException e) {
			String errorMessage = "Cannot find site by siteId: " + siteId
					+ " " + e.getMessage();
			log.error(errorMessage);
			boxMigrationErrors.append(errorMessage + Utils.LINE_BREAK);
		} catch (Exception e) {
			String errorMessage = "Migration status for " + siteId + " "
					+ e.getClass().getName();
			log.error("uploadToBox ", e);
			boxMigrationErrors.append(errorMessage + Utils.LINE_BREAK);
		}

		String uploadFinished = "Finished upload site content for site "
				+ siteId;
		log.info(uploadFinished);
		rv.put("status", uploadFinished + Utils.LINE_BREAK);
		
		rv.put("errorMessage", boxMigrationErrors.toString());
		rv.put("migrationId", migrationId);
		return rv;
	}
	
	public String getUserEmailFromUserId(String userEmail) {
		if (userEmail.indexOf(Utils.EMAIL_AT) == -1) {
			String default_member_email_suffix = Utils.DEFAULT_EMAIL_MEMBER_SUFFIX;
			// if the userEmail value is not of email format
			// then it is the uniqname of umich user
			// we need to attach a suffix to it to make it a full email address
			userEmail = userEmail + Utils.EMAIL_AT + default_member_email_suffix;
		}
		return userEmail;
	}
	

	/**
	 * 
	 * @param sessionId
	 * @param request
	 * @param response
	 * @param userId
	 * @param bulkMigrationName
	 * @param bulkMigrationId
	 * @param siteId
	 * @param siteName
	 * @param toolId
	 * @param toolName
	 * @return
	 */
	private HashMap<String, String> createRootBoxFolderWithMembers(String sessionId,
			String userId, String bulkMigrationName, String bulkMigrationId,
			String siteId, String siteName, String toolId, String toolName, HashMap<String, String> siteBoxMigrationIdMap) {
		// get box folder id
		String remoteUserId = userId;
		String boxAdminClientId = BoxUtils.getBoxClientIdOrSecret(remoteUserId, Utils.BOX_ID);
		String boxAdminClientSecret = BoxUtils.getBoxClientIdOrSecret(remoteUserId, Utils.BOX_SECRET);
		String boxSiteFolderName = "CTools - " + siteName;
		boxSiteFolderName = getMyworkspaceRootFolder(sessionId, siteId,
				siteName, boxSiteFolderName);
		
		Info boxFolder = BoxUtils.createNewFolderAtRootLevel(userId,
				boxAdminClientId, 
				boxAdminClientSecret,
				boxSiteFolderName, siteId, uRepository);
		if (boxFolder == null) {
			// exit with error saved into migration record
			String errorMessage = "Box root folder was not created for site " + siteName + ". "
					+ "Please check whether the Box folder with the name exists; Or Box auth token has expired. ";
			handleBulkResourceBoxRootFolderError(userId, bulkMigrationName,
					bulkMigrationId, siteId, siteName, toolId, toolName, errorMessage);
			return siteBoxMigrationIdMap;
		}
		
		// save the migration record into database
		// the string to hold on all site owner's id, 
		// and the admin id who are doing bulk migration is listed first
		StringBuffer allSiteOwners = new StringBuffer(userId);
		String boxFolderId = boxFolder.getID();
		String boxFolderName = boxFolder.getName();

		// add site members to Box folder as collaborators
		// construct the JSON object for membership import
		JSONObject addMembers = new JSONObject();
		JSONObject addMembers_counts = new JSONObject();
		JSONArray addMembers_items = new JSONArray();
		int count_success = 0;
		int count_error = 0;
		try
		{	
			HashMap<String, String> userRolesMap = get_site_members(siteId, sessionId);
			for (String userEid : userRolesMap.keySet()) {
				if (Utils.PARAM_ERROR_MESSAGE.equals(userEid))
				{
					// encountered error from CTools membership feed
					count_error++;
					// report it into the item status list
					JSONObject userItem = new JSONObject();
					userItem.put(Utils.REPORT_ATTR_ITEM_ID, Utils.PARAM_ERROR_MESSAGE);
					userItem.put(Utils.REPORT_ATTR_MESSAGE, userRolesMap.get(Utils.PARAM_ERROR_MESSAGE));
					addMembers_items.put(userItem);
					continue;
				}
				
				String userRole = userRolesMap.get(userEid);
				//String userEmail = Utils.getUserEmailFromUserId(userEid);
				String userEmail = getUserEmailFromUserId(userEid);

				// exclude the temporary added site admin account
				if (userEmail.startsWith(env.getProperty(Utils.ENV_PROPERTY_USERNAME)))
				{
					continue;
				}
				String addCollaborationStatus = BoxUtils.addCollaboration(
						env.getProperty(Utils.BOX_ADMIN_ACCOUNT_ID),
						userEmail, userRole, boxFolderId,
						boxAdminClientId, boxAdminClientSecret, uRepository);
				
				if (addCollaborationStatus == null)
				{
					// no error message returned - success!
					count_success++;
				}
				else
				{
					// failure
					count_error++;
					
					// update the item list
					JSONObject userItem = new JSONObject();
					userItem.put(Utils.REPORT_ATTR_ITEM_ID, userEmail);
					userItem.put(Utils.REPORT_ATTR_MESSAGE, addCollaborationStatus);
					addMembers_items.put(userItem);
				}
				
				// add user email to the owner list
				if (addUserEmail(siteId, userRole))
				{
					allSiteOwners.append(",").append(userEmail);
				}
			}
		}
		catch (RestClientException e)
		{
			log.error("Problem retrieving site members for site id = " + siteId + " " + e.getStackTrace());;
		}
		catch (JSONException e)
		{
			log.error("Problem parsing site members for site id = " + siteId + " " + e.getStackTrace());
		}
		addMembers.put(Utils.REPORT_ATTR_ITEMS, addMembers_items);
		
		// insert the counts element
		addMembers_counts.put(Utils.REPORT_ATTR_COUNTS_SUCCESSES, count_success);
		addMembers_counts.put(Utils.REPORT_ATTR_COUNTS_ERRORS, count_error);
		addMembers.put(Utils.REPORT_ATTR_COUNTS, addMembers_counts);
		
		// insert the status element
		String membership_status = Utils.REPORT_STATUS_OK;
		if (count_success == 0 && count_error > 0)
		{
			// all failures
			membership_status = Utils.REPORT_STATUS_ERROR;
		} else if (count_success > 0 && count_error > 0)
		{
			// partial failures
			membership_status = Utils.REPORT_ATTR_COUNT_PARTIALS;
		}
		addMembers.put(Utils.REPORT_ATTR_STATUS, membership_status);
		
		// the migration status JSON object shall contain a "details" element
		// which list the details of membership transfers
		JSONObject statusJSON = new JSONObject();
		JSONObject detailsJSON = new JSONObject();
		detailsJSON.put(Utils.REPORT_ATTR_ADD_MEMBERS, addMembers);
		statusJSON.put(Utils.REPORT_ATTR_DETAILS, detailsJSON);
		
		
		// now after all checks passed, we are ready for migration
		// save migration record into database
		HashMap<String, Object> saveBulkMigration = saveBulkBoxMigrationRecord(
				bulkMigrationId, bulkMigrationName, siteId,
				siteName, toolId, toolName, boxFolderId,
				boxFolderName, allSiteOwners.toString());
		
		Migration migration = (Migration) saveBulkMigration.get("migration");
		String migrationId = migration.getMigration_id();

		// save the add member JSON to migration status field
		migrationRepository.setMigrationStatus(statusJSON.toString(), migrationId);
		
		// add the boxFolderId to return map
		siteBoxMigrationIdMap.put(siteId + "_boxRootFolderId", boxFolderId);
		// add the migrationId to return map
		siteBoxMigrationIdMap.put(siteId + "_migrationId", migrationId);

		return siteBoxMigrationIdMap;
	}
	
	/**
	 * Save Bulk Migration Resource to Box record to DB
	 * 
	 * @return HasMap key="status", value=status message; key="migration",
	 *         value=MigrationObject
	 */
	private HashMap<String, Object> saveBulkBoxMigrationRecord(
			String bulkMigrationId, String bulkMigrationName, String siteId,
			String siteName, String toolId, String toolName,
			String boxFolderId, String boxFolderName, String userId) {

		// status message
		StringBuffer status = new StringBuffer();

		String destinationType = Utils.MIGRATION_TYPE_BOX;
		String targetUrl = "";
		
		// the format of folder path in box
		// e.g. https://umich.app.box.com/files/0/f/<folderId>/<folderName>
		log.info("boxFolderName=" + boxFolderName);
		if (boxFolderId != null && !boxFolderId.isEmpty()
				&& boxFolderName != null && !boxFolderName.isEmpty()) {
			targetUrl = Utils.BOX_FILE_PATH_URL + boxFolderId
					+ Utils.PATH_SEPARATOR + boxFolderName;
		}

		// assign null values to batch id and name
		// create new migration record
		return  newMigrationRecord(status, bulkMigrationId, bulkMigrationName,
				siteId, siteName, toolId, toolName, destinationType, userId,
				targetUrl);
	}
	
	/**
	 * create new migration record
	 *
	 * @param status
	 * @param batch_id
	 * @param batch_name
	 * @param siteId
	 * @param siteName
	 * @param toolId
	 * @param toolName
	 * @param destinationType
	 * @param userId
	 * @param targetUrl
	 * @return
	 */
	public HashMap<String, Object> newMigrationRecord(StringBuffer status,
			String batch_id, String batch_name, String siteId, String siteName,
			String toolId, String toolName, String destinationType,
			String userId, String targetUrl) {
		HashMap<String, Object> rv = new HashMap<String, Object>();
		Migration m = new Migration(batch_id, batch_name, siteId, siteName,
				toolId, toolName, userId, new java.sql.Timestamp(
						System.currentTimeMillis()), // start time is now
				null, destinationType, targetUrl, "" /* status */);

		Migration newMigration = null;

		StringBuffer insertMigrationDetails = new StringBuffer();
		insertMigrationDetails.append("Save migration record site_id=")
				.append(siteId).append(" site_name=").append(siteName)
				.append(" tool_id=").append(toolId).append(" tool_name=")
				.append(toolName).append(" migrated_by=").append(userId)
				.append(" destination_type=").append(destinationType)
		.append(" \n ");

		log.info(insertMigrationDetails.toString());
		try {
			newMigration = migrationRepository.save(m);
		} catch (Exception e) {
			log.error("Exception " + insertMigrationDetails.toString()
					+ e.getMessage());
			status.append(e.getMessage());
		}

		// put Migration object into HashMap
		if (newMigration != null) {
			rv.put("migration", newMigration);
		}
		// put status message into HashMap
		if (status.length() == 0) {
			status.append("Database Migration record successfully created.");
		}
		rv.put(Utils.REPORT_ATTR_STATUS, status.toString());

		return rv;
	}

	public HashMap<String, Object> newMigrationRecordForMsgMigration(String status,
																	  String batch_id, String batch_name, String siteId, String siteName,
																	  String toolId, String toolName, String destinationType,
																	  String userId, String targetUrl, Timestamp endtime) {
		HashMap<String, Object> rv = new HashMap<String, Object>();
		Migration m = new Migration(batch_id, batch_name, siteId, siteName,
				toolId, toolName, userId,  new Timestamp(
				System.currentTimeMillis()), // start time is now
				endtime, destinationType, targetUrl, status);

		Migration newMigration = null;

		StringBuffer insertMigrationDetails = new StringBuffer();
		insertMigrationDetails.append("Save migration record site_id=")
				.append(siteId).append(" site_name=").append(siteName)
				.append(" tool_id=").append(toolId).append(" tool_name=")
				.append(toolName).append(" migrated_by=").append(userId)
				.append(" destination_type=").append(destinationType)
				.append(" \n ");

		log.info(insertMigrationDetails.toString());
		try {
			newMigration = migrationRepository.save(m);
		} catch (Exception e) {
			log.error("Exception " + insertMigrationDetails.toString()
					+ e.getMessage());
		}

		// put Migration object into HashMap
		if (newMigration != null) {
			rv.put("migration", newMigration);
		}
		rv.put(Utils.REPORT_ATTR_STATUS, status);

		return rv;
	}
	
	/**
	 * return true if user has Owner role inside the project site
	 * or if the user has maintain role in myworkspace site
	 * @param siteId
	 * @param userRole
	 * @return
	 */
	private boolean addUserEmail(String siteId, String userRole) {
		return Utils.ROLE_OWNER.equals(userRole)
		 || (siteId.startsWith(Utils.CTOOLS_SITE_TYPE_MYWORKSPACE_PREFIX)
			&& Utils.ROLE_MAINTAINER.equals(userRole));
	}
	
	/**
	 * REST API call to get CTools site members
	 *
	 * @param site_id
	 * @param sessionId
	 * @return map of member id and ctools site role.
	 */
	
	// This wrapper ensures existing calls remain working.
	public HashMap<String, String> get_site_members(String site_id, String sessionId) {
		HashMap<String,Object> raw_result = get_site_members(site_id, sessionId, Utils.SHORT_MEMBERSHIP_FORMAT);
		// convert from map of String,Object to the expected String,String
		HashMap<String,String> newMap =new HashMap<String,String>();
		for (Map.Entry<String, Object> entry : raw_result.entrySet()) {
		       if(entry.getValue() instanceof String){
		            newMap.put(entry.getKey(), (String) entry.getValue());
		          }
		 }
		return newMap;
	}


	// Generalized get_site_members method which takes account of the requested format.
	public HashMap<String, Object> get_site_members(String site_id, String sessionId, String format) {

		HashMap<String, Object> rv = new HashMap<String, Object>();

		if (format.length() > 0) {
			log.debug("get_site_members: format: [{}]",format);
		}

		String membersString = "";
		String errorMessage = "";
		String requestUrl = "";

		// get the membership from CTools.
		RestTemplate restTemplate = new RestTemplate();
		requestUrl = Utils.directCallUrl(env, "membership/site/" + site_id + ".json?", sessionId);
		log.debug("get_site_members: url:[{}] ",requestUrl);
		try {
			membersString = restTemplate.getForObject(requestUrl, String.class);
		} catch (RestClientException e) {
			if (site_id.startsWith(Utils.CTOOLS_SITE_TYPE_MYWORKSPACE_PREFIX))
			{
				return getMyWorkspaceSiteMember(sessionId, site_id);
			}
			else
			{
				errorMessage = "The membership list for this site cannot be generated. " +
						"There is at least one site member with an invalid user ID. " +
						"Please contact ITS for help with this error. ";
				log.error("Cannot find site members by siteId: " + site_id
						+ " url=" + requestUrl + " " + e.getMessage());
			}
		}

		if (membersString != null && membersString.length() > 0) {
			try
			{
				JSONObject sJSON = new JSONObject(membersString);
				JSONArray members = (JSONArray) sJSON.get("membership_collection");
				// iterate through all members
				for (int iMember = 0; members != null && iMember < members.length(); ++iMember) {
					processEachMemberInSite(rv, members, iMember, format);
				}
			}
			catch (JSONException e)
			{
				errorMessage = "error parsing member string:" + membersString + " for site " + site_id;
				log.warn(errorMessage);
			}
		}

		// return the errorMessage if there is one
		if(!errorMessage.isEmpty()) {
			rv.put(Utils.PARAM_ERROR_MESSAGE, errorMessage);
		}

		return rv;
	}

	// Choose how to process each member.
	// In java 8 could just assign the method to a variable early on and not need to keep redundent checking.
	private void processEachMemberInSite(HashMap<String, Object> rv, JSONArray members, int iMember, String format) {
		{
			if (Utils.SHORT_MEMBERSHIP_FORMAT.equals(format)) {
				formatShortMemberInfo(rv, members, iMember);
			}
			if (Utils.LONG_MEMBERSHIP_FORMAT.equals(format)) {
				formatLongMemberInfo(rv, members, iMember);
			}
		}
	}

	// Format one member information object into the legacy short format.
	private void formatShortMemberInfo(HashMap<String, Object> rv, JSONArray members, int iMember) {
		JSONObject member = members.getJSONObject(iMember);

		String userEid = member.getString("userEid");
		String userRole = member.getString("memberRole");

		boolean isActive = member.getBoolean("active");
		if (isActive) {
			rv.put(userEid, userRole);
		}
	}

	// Format one member information object into the new long format.
	private void formatLongMemberInfo(HashMap<String, Object> rv, JSONArray members, int iMember) {
		JSONObject member = members.getJSONObject(iMember);
		
		String userEid = member.getString("userEid");
		HashMap<String,String> userInfo = new HashMap<String,String>();
		
		userInfo.put("memberRole",member.getString("memberRole"));
		// the name might be userSortName or userDisplayName
		userInfo.put("userSortName", member.getString("userSortName"));

		boolean isActive = member.getBoolean("active");
		if (isActive) {
			rv.put(userEid, userInfo);
		}
	}

	/**
	 * get MyWorkspace site members
	 * @param sessionId
	 * @param site_id
	 * @return map of user id and role.
	 * @throws RestClientException
	 */
	private HashMap<String, Object> getMyWorkspaceSiteMember(String sessionId, String site_id)
			throws RestClientException {
			
			HashMap<String, Object> rv = new HashMap<String, Object>();
			
			// for MyWorkspace site, there is problem getting membership information
			// we have to decipher the user information from the user id
			String userId = site_id.substring(1);
			
			RestTemplate restTemplate = new RestTemplate();
			String requestUrl = Utils.directCallUrl(env, "user/" + userId + ".json?", sessionId);
			log.info("get user info: ",requestUrl);
			// found user
			String userString = "";
			try {
				userString = restTemplate.getForObject(requestUrl, String.class);
				JSONObject userObject = new JSONObject(userString);
				String userEid = userObject.getString("eid");
				// user have maintainer role inside their MyWorkspace site
				// which is comparable to the Owner role in project site
				String userRole = Utils.ROLE_OWNER;
				rv.put(userEid, userRole);
			} catch (RestClientException ee) {
				String errorMessage = "Cannot find user by user id=: " + userId
						+ " " + ee.getMessage();
				log.error(errorMessage);
				throw ee;
			}
			//log.info("result of getting user string " + userString);
			return rv;
		}

	/**
	 * update Box root folder name for MyWorkspace sites
	 * @param sessionId
	 * @param userId
	 * @param siteName
	 * @param boxSiteRootFolderName
	 * @return
	 */
	private String getMyworkspaceRootFolder(String sessionId, String siteId,
			String siteName, String boxSiteRootFolderName) {
		if (Utils.CTOOLS_MYWORKSPACE_TITLE.equals(siteName))
		{
			// get the user id from site id by removing the ~ char
			String userId = siteId.replaceAll(Utils.CTOOLS_SITE_TYPE_MYWORKSPACE_PREFIX, "");
			
			// get user uniqname
			String requestUrl = Utils.directCallUrl(env, "user/" + userId + ".json?", sessionId);
			RestTemplate restTemplate = new RestTemplate();
			try {
				// get user eid based on user id
				log.info(this + "get user eid"+ requestUrl);
				ResponseEntity<String> userEntity = restTemplate
						.getForEntity(requestUrl, String.class);
				if (userEntity.getStatusCode().is2xxSuccessful()) {
					JSONObject userObject = new JSONObject(
							userEntity.getBody());
					String userEid = (String) userObject.get("eid");
					boxSiteRootFolderName = "CTools - " + Utils.CTOOLS_MYWORKSPACE_TITLE + " - " + userEid;
				}
			} catch (RestClientException e2) {
				log.warn(this + requestUrl + e2.getMessage());
				boxSiteRootFolderName = "CTools - " + Utils.CTOOLS_MYWORKSPACE_TITLE + " - " + userId;
			}
		}
		return boxSiteRootFolderName;
	}

	/**
	 * concatenate all site Owner users, and maintainer users for MyWorkspace sites
	 * @param sessionId
	 * @param siteId
	 * @param userId
	 * @return
	 */
	public String getAllSiteUsersWithOwnerOrMaintainerRole(
			String sessionId, String siteId, String userId) {
		// include the current user as site owner first
		StringBuffer allSiteOwners = new StringBuffer(getUserEmailFromUserId(userId));
		try
		{
			// add site members to migration
			HashMap<String, String> userRoles = get_site_members(siteId, sessionId);
			for (String userEid : userRoles.keySet()) {
				// no need to count the error report line here
				if (Utils.PARAM_ERROR_MESSAGE.equals(userEid))
					continue;
				
				String userRole = userRoles.get(userEid);
				//String userEmail = Utils.getUserEmailFromUserId(userEid);
				String userEmail = getUserEmailFromUserId(userEid);
				
				// add user email to owner list
				if (addUserEmail(siteId, userRole))
				{
					allSiteOwners.append(",").append(userEmail);
				}
			}
		}
		catch (RestClientException e)
		{
			log.error("Problem retrieving site members for site id = " + siteId + " " + e.getStackTrace());
		}
		catch (JSONException e)
		{
			log.error("Problem parsing site members for site id = " + siteId + " " + e.getStackTrace());
		}
		return allSiteOwners.toString();
	}


	// TODO: map the ctools to google roles
	private static final HashMap<String,String> MemberRoleMap = new HashMap<String,String>() {
		private static final long serialVersionUID = -8839396373117387837L;
		{
			put(Utils.ROLE_OWNER,"OWNER");
			put(Utils.ROLE_ORGANIZER,"MANAGER");	
			put(Utils.ROLE_MEMBER,"MEMBER");
			put(Utils.ROLE_OBSERVER,"MEMBER");
			put(Utils.ROLE_MAINTAINER,"OWNER");
			put(Utils.ROLE_INSTRUCTOR,"MANAGER");
			put(Utils.ROLE_STUDENT,"MEMBER");
			// Role that will be used if nothing else matches
			put("DEFAULT","MEMBER");
		}};

		private static final Logger log = LoggerFactory
				.getLogger(MigrationTaskService.class);

		/**
		 * Download CTools site resource in zip file
		 *
		 * @return status of download
		 */

		public void downloadZippedFile(Environment env, HttpServletRequest request,
				HttpServletResponse response, String userId,
				HashMap<String, Object> sessionAttributes, String site_id,
				String migrationId, MigrationRepository repository) {
			// hold download status
			StringBuffer downloadStatus = new StringBuffer();
			List<MigrationFileItem> itemStatus = new ArrayList<MigrationFileItem>();

			// login to CTools and get sessionId
			if (sessionAttributes.containsKey(Utils.SESSION_ID)) {
				String sessionId = (String) sessionAttributes.get(Utils.SESSION_ID);
				HttpContext httpContext = (HttpContext) sessionAttributes
						.get("httpContext");

				// 3. get site information
				RestTemplate restTemplate = new RestTemplate();
				// the url should be in the format of
				// "https://server/direct/site/SITE_ID.json"
				String requestUrl = Utils.directCallUrl(env, "content/site/" + site_id + ".json?", sessionId);
				String siteResourceJson = null;
				try {
					siteResourceJson = restTemplate.getForObject(requestUrl,
							String.class);

					// null zip content
					byte[] zipContent = null;

					log.info(":downloadZippedFile begin: start downloading content zip file for site "
							+ site_id);
					//
					// Sends the response back to the user / browser. The
					// content for zip file type is "application/zip". We
					// also set the content disposition as attachment for
					// the browser to show a dialog that will let user
					// choose what action will he do to the sent content.
					//
					response.setContentType(Utils.MIME_TYPE_ZIP);
					response.setCharacterEncoding("UTF-8");

					String zipFileName = generateResourcesZipFileName(request.getParameterMap().get("site_name")[0]);

					response.setHeader("Content-Disposition",
							"attachment;filename=\"" + zipFileName + "\"");

					ZipOutputStream out = new ZipOutputStream(
							response.getOutputStream());
					String compressionLevel = env.getProperty(Utils.ENV_PROPERTY_ZIP_COMPRESSSION_LEVEL);
					if ((compressionLevel == null) || (compressionLevel.isEmpty())) {
						// set compression level to high by default
						out.setLevel(9);
						log.error("The property \"zip.compression.level\" is not set");
					} else {
						out.setLevel(Integer.parseInt(compressionLevel.trim()));
					}

					// prepare zip entry for site content objects
					itemStatus = zipSiteContent(httpContext, siteResourceJson,
							sessionId, out);

					out.flush();
					out.close();
					log.info("Finished zip file download for site " + site_id);

				} catch (RestClientException e) {
					String errorMessage = "Cannot find site by siteId: " + site_id
							+ " " + e.getMessage();
					Response.status(Response.Status.NOT_FOUND).entity(errorMessage)
					.type(MediaType.TEXT_PLAIN).build();
					log.error(errorMessage);
					downloadStatus.append(errorMessage);
				} catch (IOException e) {
					String errorMessage = "Problem getting content zip file for "
							+ site_id + " " + e.getMessage();
					Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(errorMessage).type(MediaType.TEXT_PLAIN)
					.build();
					log.error(errorMessage);
					downloadStatus.append(errorMessage);

				} catch (Exception e) {
					String errorMessage = "Migration status for " + site_id + " "
							+ e.getClass().getName();
					Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(errorMessage).type(MediaType.TEXT_PLAIN)
					.build();
					log.error(errorMessage);
					downloadStatus.append(errorMessage + Utils.LINE_BREAK);
				}
			} else {
				String userError = "Cannot become user " + userId;
				log.error(userError);
				downloadStatus.append(userError);
			}

			// the HashMap object holds itemized status information
			JSONObject statusJson = new JSONObject();
			statusJson.put(Utils.REPORT_ATTR_TYPE, Utils.REPORT_ATTR_TYPE_RESOURCE_ZIP);
			
			// count JSON
			JSONObject countsJson = new JSONObject();
			int errorCount = 0;
			int successItemCount = 0;
			// items json
			JSONArray itemsArray = new JSONArray();
			for(MigrationFileItem fItem : itemStatus)
			{
				String status = fItem.getStatus();
				if (status.isEmpty() || status.toLowerCase().contains("success"))
				{
					successItemCount++;
				}
				else
				{
					errorCount++;
					
					// report error 
					JSONObject itemJson = new JSONObject();
					itemJson.put(Utils.REPORT_ATTR_ITEM_ID, fItem.getFile_name());
					itemJson.put(Utils.REPORT_ATTR_ITEM_STATUS, fItem.getStatus());
					itemsArray.put(itemJson);
				}
			}
			countsJson.put(Utils.REPORT_ATTR_COUNTS_SUCCESSES, successItemCount);
			countsJson.put(Utils.REPORT_ATTR_COUNTS_ERRORS, errorCount);
			
			// add to top report level
			statusJson.put(Utils.REPORT_ATTR_COUNTS, countsJson);
			statusJson.put(Utils.REPORT_ATTR_ITEMS, itemsArray);
			

			statusJson.put(Utils.REPORT_ATTR_STATUS, errorCount == 0 ? Utils.REPORT_STATUS_OK:Utils.REPORT_STATUS_PARTIAL);

			// update the status and end_time of migration record
			setMigrationEndTimeAndStatus(migrationId, repository, statusJson);
			

			return;

		}

		private String generateResourcesZipFileName(String site_name) {
			site_name = Utils.sanitizeName(site_name);
			String zipFileName = site_name + "_content.zip";
			log.debug("resourceZipFileName: [{}]",zipFileName);
			return zipFileName;
		}

		/**
		 * update the status and end_time of migration record
		 * @param migrationId
		 * @param repository
		 * @param obj
		 */
		public void setMigrationEndTimeAndStatus(String migrationId,
				MigrationRepository repository, JSONObject obj) {
			repository.setMigrationEndTime(
					new java.sql.Timestamp(System.currentTimeMillis()),
					migrationId);
			repository.setMigrationStatus(obj.toString(), migrationId);
		}

		/**
		 * create zip entry for folders and files
		 */
		private List<MigrationFileItem> zipSiteContent(HttpContext httpContext,
				String siteResourceJson, String sessionId, ZipOutputStream out) {
			// the return list of MigrationFileItem objects, with migration status
			// recorded
			List<MigrationFileItem> fileItems = new ArrayList<MigrationFileItem>();

			// site root folder
			String rootFolderPath = null;

			JSONObject obj = new JSONObject(siteResourceJson);

			JSONArray array = obj
					.getJSONArray(CONTENT_JSON_ATTR_CONTENT_COLLECTION);

			// the map stores folder name conversions;
			// folder name can be changed within CTools:
			// it can be named differently, while the old name still uses in the
			// folder/resource ids
			HashMap<String, String> folderNameMap = new HashMap<String, String>();
			for (int i = 0; i < array.length(); i++) {

				// item status information
				StringBuffer itemStatus = new StringBuffer();

				JSONObject contentItem = array.getJSONObject(i);

				String type = Utils.getJSONString(contentItem,
						CONTENT_JSON_ATTR_TYPE);
				String title = Utils.getJSONString(contentItem,
						CONTENT_JSON_ATTR_TITLE);
				String copyrightAlert = Utils.getJSONString(contentItem,
						CONTENT_JSON_ATTR_COPYRIGHT_ALERT);

				String contentAccessUrl = contentItem
						.getString(CONTENT_JSON_ATTR_URL);

				// get only the url after "/access/" string
				String contentUrl = getContentUrl(contentAccessUrl);
				if (contentUrl == null) {
					// log error
					itemStatus.append("Content url " + contentUrl
							+ " does not contain " + CTOOLS_ACCESS_STRING + " nor "
							+ CTOOLS_CITATION_ACCESS_STRING);

					// document the error and break
					MigrationFileItem item = new MigrationFileItem(contentUrl,
							title, itemStatus.toString());
					fileItems.add(item);
					break;
				}

				// modify the contentAccessUrl if needed for copyright alert setting
				// always export the resource content regardless of the copyright
				// settings
				contentAccessUrl = Utils.getCopyrightAcceptUrl(copyrightAlert,
						contentAccessUrl);

				// get container string from content url
				String container = getContainerStringFromContentUrl(contentUrl);

				// come checkpoints before migration
				itemStatus = preMigrationChecks(itemStatus, contentUrl, container,
						title);

				if (itemStatus.length() == 0) {
					// no errors, proceed with migration
					if (Utils.COLLECTION_TYPE.equals(type)) {
						// folders
						if (rootFolderPath == null) {
							rootFolderPath = contentUrl;
						} else {

							// no longer need to create folder separately but 
							// will update folderNameMap
							
							String folderName = contentUrl.replace(rootFolderPath,"");
							
							// update folder name
							folderNameMap = Utils.updateFolderNameMap(
									folderNameMap, title, folderName);
							if (folderNameMap.containsKey(folderName)) {
									folderName = folderNameMap.get(folderName);
							}
						}

					} else {
						// get the zip file name with folder path info
						String zipFileName = container.substring(container
								.indexOf(rootFolderPath) + rootFolderPath.length());
						zipFileName = zipFileName.concat(Utils.sanitizeName(type,
								title));
						log.info("zip download processing file " + zipFileName);

						// value not null for WebLink content item
						String webLinkUrl = Utils.getJSONString(contentItem,
								CONTENT_JSON_ATTR_WEB_LINK_URL);

						// Call the zipFiles method for creating a zip stream.
						String zipFileStatus = zipFiles(type, httpContext,
								zipFileName, title, webLinkUrl, contentAccessUrl,
								sessionId, out, folderNameMap);
						itemStatus.append(zipFileStatus);
					}
				}

				MigrationFileItem fileItem = new MigrationFileItem(contentUrl,
						title, itemStatus.toString());

				fileItems.add(fileItem);
			} // for
			return fileItems;
		}

		/**
		 * based on the content url, get its parent folder - container folder url
		 * ends with "/", need remove the ending "/" first;
		 * <container_path_end_with_slash><content_title>
		 *
		 * @param contentUrl
		 * @return
		 */
		private String getContainerStringFromContentUrl(String contentUrl) {
			String container = contentUrl.endsWith(Utils.PATH_SEPARATOR) ? contentUrl
					.substring(0, contentUrl.length() - 1) : contentUrl;
					container = container.substring(0,
							container.lastIndexOf(Utils.PATH_SEPARATOR) + 1);
					return container;
		}

		/**
		 * create zip entry for files
		 */
		private String zipFiles(String type, HttpContext httpContext,
				String fileName, String title, String webLinkUrl,
				String fileAccessUrl, String sessionId, ZipOutputStream out,
				HashMap<String, String> folderNameUpdates) {
			log.info("*** " + fileAccessUrl);

			// record zip status
			StringBuffer zipFileStatus = new StringBuffer();

			// create httpclient
			HttpClient httpClient = HttpClientBuilder.create().build();
			
			InputStream content = null;
			try {
				// get file content from /access url
				HttpGet getRequest = new HttpGet(fileAccessUrl);
				getRequest.setConfig(getRequestConfigWithTimeouts());
				getRequest.setHeader("Content-Type",
						"application/x-www-form-urlencoded");
				HttpResponse r = httpClient.execute(getRequest, httpContext);
				content = r.getEntity().getContent();
			} catch (Exception e) {
				String errorMessage = "Cannot get content for " + title + " due to " + e.getMessage();
				log.error(errorMessage);
				zipFileStatus.append(errorMessage);
			}

			// exit if content stream is null
			if (content == null)
				return zipFileStatus.toString();

			try {
				int length = 0;
				byte data[] = new byte[STREAM_BUFFER_CHAR_SIZE];
				BufferedInputStream bContent = null;

				try {
					bContent = new BufferedInputStream(content);

					// checks for folder renames
					fileName = Utils.updateFolderPathForFileName(fileName,
							folderNameUpdates);

					log.info("download file " + fileName + " type=" + type);

					ZipEntry fileEntry = Utils.zipEntryWithValidName(fileName);
					if (fileEntry == null)
					{
						// folder name too long, log error
						zipFileStatus.append(Utils.ERROR_MESSAGE_ZIP_ENTRY_TOO_LONG_NAME_FILE + fileName);
					}
					else
					{
						// put file content into ZipEntry
						bContent = zipFileContent(type, fileName, title,
								webLinkUrl, out, zipFileStatus, content,
								length, data, bContent, fileEntry);
					}
				} catch (IllegalArgumentException iException) {
					String IAExceptionString = "zipFiles: problem creating BufferedInputStream with content and length "
							+ data.length + iException;
					log.warn(IAExceptionString);
					zipFileStatus.append(IAExceptionString + Utils.LINE_BREAK);
				} finally {
					if (bContent != null) {
						try {
							bContent.close(); // The BufferedInputStream needs to be
							// closed
						} catch (IOException ioException) {
							String ioExceptionString = "zipFiles: problem closing FileChannel "
									+ ioException;
							log.warn(ioExceptionString);
							zipFileStatus.append(ioExceptionString + Utils.LINE_BREAK);
						}
					}
				}
			} catch (IOException e) {
				String eMessage = e.getMessage();
				
				String ioExceptionString = "Problem creating Zip file entry for fileName="
						+ fileName + ": " + eMessage;
				if (eMessage != null && eMessage.contains("duplicate entry"))
				{
					// specific warning for duplicate entry
					ioExceptionString = "This file could not be downloaded because another file already exists with the same name: " + fileName;
				}
				log.warn(ioExceptionString);
				zipFileStatus.append(ioExceptionString + Utils.LINE_BREAK);
			} finally {
				if (content != null) {
					try {
						content.close(); // The input stream needs to be closed
					} catch (IOException ioException) {
						String ioExceptionString = "zipFiles: problem closing Inputstream content for"
								+ fileName + ioException;
						log.warn(ioExceptionString);
						zipFileStatus.append(ioExceptionString + Utils.LINE_BREAK);
					}
				}
				try {
					out.flush();
				} catch (Exception e) {
					String errorMessage = "problem with zip downloading " + fileName + " " + e.getMessage();
					zipFileStatus.append(errorMessage);
					log.error(errorMessage);
				}
			}

			// return success message
			if (zipFileStatus.length() == 0) {
				zipFileStatus.append(fileName
						+ " was added into zip file successfully.");
			}

			return zipFileStatus.toString();
		}

		/**
		 * stream file content into ZipEntry
		 * @param type
		 * @param fileName
		 * @param title
		 * @param webLinkUrl
		 * @param out
		 * @param zipFileStatus
		 * @param content
		 * @param length
		 * @param data
		 * @param bContent
		 * @param fileEntry
		 * @return
		 * @throws IOException
		 */
		private BufferedInputStream zipFileContent(String type,
				String fileName, String title, String webLinkUrl,
				ZipOutputStream out, StringBuffer zipFileStatus,
				InputStream content, int length, byte[] data,
				BufferedInputStream bContent, ZipEntry fileEntry)
				throws IOException {
			if (Utils.isOfURLMIMEType(type)) {
				if (webLinkUrl == null || webLinkUrl.isEmpty())
				{
					zipFileStatus.append("Link "+ title + " could not be migrated due to empty URL link. ");
				}
				else
				{
					try {
						// get the html file content first
						String webLinkContent = Utils.getWebLinkContent(title,
								webLinkUrl);
						
						out.putNextEntry(fileEntry);
						out.write(webLinkContent.getBytes());
					}  catch (IOException e) {
						// return status with error message
						String errorMessage = e.getMessage() + "Link " + title
								+ " could not be migrated due to exception " + e.getMessage() + ". Please change the link name to be the complete URL and migrate the site again.";
						zipFileStatus.append(errorMessage);
						log.error(errorMessage);
					}
				}
			} else {
				out.putNextEntry(fileEntry);
				int bCount = -1;

				bContent = new BufferedInputStream(content);
				while ((bCount = bContent.read(data)) != -1) {
					out.write(data, 0, bCount);
					length = length + bCount;
				}
			}
			out.flush();

			try {
				out.closeEntry(); // The zip entry need to be closed
			} catch (IOException ioException) {
				String ioExceptionString = "zipFiles: problem closing zip entry "
						+ fileName + " " + ioException;
				log.error(ioExceptionString);
				zipFileStatus.append(ioExceptionString + Utils.LINE_BREAK);
			}
			return bContent;
		}

		/**
		 * return a RequestConfig Object with Timeout params set
		 * @return
		 */
		private RequestConfig getRequestConfigWithTimeouts() {
			// default time out to be 10 seconds
			int timeout_milliseconds = 1000;
			if (env.getProperty(Utils.ENV_PROPERTY_TIMEOUT_MINISECOND)!=null) {
				// override from property setting
				try
				{
					timeout_milliseconds = Integer.valueOf(env.getProperty(Utils.ENV_PROPERTY_TIMEOUT_MINISECOND)).intValue();
				} catch (NumberFormatException e)
				{
					log.error("Environment setting " + Utils.ENV_PROPERTY_TIMEOUT_MINISECOND + " is not an integer value. ");
				}
			}
			RequestConfig requestConfig = RequestConfig.custom()
					  .setSocketTimeout(timeout_milliseconds)
					  .setConnectTimeout(timeout_milliseconds)
					  .setConnectionRequestTimeout(timeout_milliseconds)
					  .build();
			return requestConfig;
		}

		/*************** Box Migration ********************/
		/**
		 * iterating though content json and upload folders and files to Box
		 */
		public List<MigrationFileItem> boxUploadSiteContent(
				String migration_id, HttpContext httpContext, String userId, String sessionId,
				String siteResourceJson, String boxFolderId) {

			List<MigrationFileItem> rv = new ArrayList<MigrationFileItem>();

			// site root folder
			String rootFolderPath = null;

			JSONObject obj = new JSONObject(siteResourceJson);

			JSONArray array = obj
					.getJSONArray(CONTENT_JSON_ATTR_CONTENT_COLLECTION);

			// start a stack object, with element of site folder ids
			// the top of the stack is the current container folder
			// since the CTools site content json feed is depth-first search,
			// we can use the stack to store the current folder id,
			// and do pop() when moving to a different folder
			java.util.Stack<String> containerStack = new java.util.Stack<String>();
			// this is the parallel stack which stored the Box folder of those
			// container collections
			java.util.Stack<String> boxFolderIdStack = new java.util.Stack<String>();
			
			for (int i = 0; i < array.length(); i++) {
				// error flag
				boolean error_flag = false;

				// status for each item
				StringBuffer itemStatus = new StringBuffer();

				JSONObject contentItem = array.getJSONObject(i);

				String type = Utils.getJSONString(contentItem,
						CONTENT_JSON_ATTR_TYPE);
				String title = Utils.getJSONString(contentItem,
						CONTENT_JSON_ATTR_TITLE);
				// trim the trailing space from title string
				title = title.trim();
				
				String description = Utils.getJSONString(contentItem,
						CONTENT_JSON_ATTR_DESCRIPTION);
				// metadata
				String author = Utils.getJSONString(contentItem,
						CONTENT_JSON_ATTR_AUTHOR);
				String copyrightAlert = Utils.getJSONString(contentItem,
						CONTENT_JSON_ATTR_COPYRIGHT_ALERT);

				// get only the url after "/access/content" string
				String contentAccessUrl = contentItem
						.getString(CONTENT_JSON_ATTR_URL);
				String contentUrl = getContentUrl(contentAccessUrl);
				if (contentUrl == null) {
					itemStatus.append("Content url " + contentUrl
							+ " does not contain " + CTOOLS_ACCESS_STRING + " nor "
							+ CTOOLS_CITATION_ACCESS_STRING);

					// document the error and break
					MigrationFileItem item = new MigrationFileItem(contentUrl,
							title, itemStatus.toString());
					rv.add(item);
					break;
				}

				// modify the contentAccessUrl if needed for copyright alert setting
				// always export the resource content regardless of the copyright
				// settings
				contentAccessUrl = Utils.getCopyrightAcceptUrl(copyrightAlert,
						contentAccessUrl);

				// get container string from content url
				String container = getContainerStringFromContentUrl(contentUrl);

				// come checkpoints before migration
				itemStatus = preMigrationChecks(itemStatus, contentUrl, container,
						title);

				log.info("type=" + type + " contentUrl=" + contentUrl + " error="
						+ itemStatus.toString());

				if (itemStatus.length() == 0) {
					// now alerts, do Box uploads next
					if (rootFolderPath == null
							&& Utils.COLLECTION_TYPE.equals(type)) {
						// root folder
						rootFolderPath = contentUrl;

						// insert into stack
						containerStack.push(contentUrl);
						boxFolderIdStack.push(boxFolderId);
					} else {
						// value not null only for Web Link Item
						String webLinkUrl = Utils.getJSONString(contentItem,
								CONTENT_JSON_ATTR_WEB_LINK_URL);
						try {
							// do uploads
							HashMap<String, Object> rvValues = processAddBoxFolders(
									migration_id,
									userId, type, rootFolderPath, contentUrl,
									containerStack, boxFolderIdStack, title,
									container, boxFolderId, itemStatus,
									description, contentItem, httpContext,
									webLinkUrl, contentAccessUrl, author,
									copyrightAlert, sessionId);
							itemStatus = (StringBuffer) rvValues.get(Utils.PARAM_ITEM_STATUS);
							containerStack = (java.util.Stack<String>) rvValues
									.get(Utils.PARAM_CONTAINER_STACK);
							boxFolderIdStack = (java.util.Stack<String>) rvValues
									.get(Utils.PARAM_BOX_FOLDER_ID_STACK);
							log.debug(Utils.PARAM_CONTAINER_STACK + " length="
									+ containerStack.size());
							log.debug(Utils.PARAM_BOX_FOLDER_ID_STACK + " length="
									+ boxFolderIdStack.size());
						} catch (BoxAPIException e) {
							String errorMessage = "There is a problem uploading item \"" + title + "\" to Box: "
									+ e.getMessage();
							try
							{
								JSONObject eJSON = new JSONObject(e.getResponse());
								errorMessage = errorMessage.concat(eJSON.has("context_info")?eJSON.getString("context_info"):"");
							}
							catch (JSONException ee)
							{
								log.error("Cannot parse JSONObject out of " + e.getResponse());
							}
							
							log.error(this + errorMessage);
							
							// the status of file upload to Box
							itemStatus.append(errorMessage);
							MigrationFileItem item = new MigrationFileItem(
									contentUrl, title, itemStatus.toString());
							rv.add(item);

							// finish up and continue to next item
							continue;
						}
					}

				}

				// exclude the root folder level in the status report
				if (i == 0)
					continue;

				// the status of file upload to Box
				MigrationFileItem item = new MigrationFileItem(contentUrl, title,
						itemStatus.toString());
				rv.add(item);
			} // for

			return rv;
		}


		/**
		 * get substring of contentAccessUrl
		 *
		 * @param itemStatus
		 * @param contentAccessUrl
		 * @return
		 */
		private String getContentUrl(String contentAccessUrl) {
			String contentUrl = URLDecoder.decode(contentAccessUrl);
			if (contentUrl.contains(CTOOLS_ACCESS_STRING)) {
				// non-citation resource
				contentUrl = contentUrl.substring(contentUrl
						.indexOf(CTOOLS_ACCESS_STRING)
						+ CTOOLS_ACCESS_STRING.length());
			} else if (contentUrl.contains(CTOOLS_CITATION_ACCESS_STRING)) {
				// citation resource
				contentUrl = contentUrl.substring(contentUrl
						.indexOf(CTOOLS_CITATION_ACCESS_STRING)
						+ CTOOLS_CITATION_ACCESS_STRING.length());
			} else {
				// log error
				contentUrl = null;
			}
			return contentUrl;
		}

		/**
		 * perform couple of checks before migration starts
		 *
		 * @param itemStatus
		 * @param contentUrl
		 * @param container
		 * @param title
		 * @param copyrightAlert
		 */
		private StringBuffer preMigrationChecks(StringBuffer itemStatus,
				String contentUrl, String container, String title) {
			if (contentUrl == null || contentUrl.length() == 0) {
				// log error if the content url is missing
				String urlError = "No url for content " + title;
				log.error(urlError);
				itemStatus.append(urlError + Utils.LINE_BREAK);
			} else if (container == null || container.length() == 0) {
				// log error if the content url is missing
				String containerError = "No container folder url for content "
						+ title;
				log.error(containerError);
				itemStatus.append(containerError + Utils.LINE_BREAK);
			}
			return itemStatus;
		}

		/**
		 * process content JSON file
		 * @param migrationId
		 * @param userId
		 * @param type
		 * @param rootFolderPath
		 * @param contentUrl
		 * @param containerStack
		 * @param boxFolderIdStack
		 * @param title
		 * @param container
		 * @param boxFolderId
		 * @param api
		 * @param itemStatus
		 * @param description
		 * @param contentItem
		 * @param httpContext
		 * @param webLinkUrl
		 * @param contentAccessUrl
		 * @param author
		 * @param copyrightAlert
		 * @param sessionId
		 * @return
		 * @throws BoxAPIException
		 */
		private HashMap<String, Object> processAddBoxFolders(String migrationId, String userId,
				String type, String rootFolderPath, String contentUrl,
				java.util.Stack<String> containerStack,
				java.util.Stack<String> boxFolderIdStack, String title,
				String container, String boxFolderId,
				StringBuffer itemStatus, String description,
				JSONObject contentItem, HttpContext httpContext, String webLinkUrl,
				String contentAccessUrl, String author, String copyrightAlert,
				String sessionId) throws BoxAPIException {

			if (Utils.COLLECTION_TYPE.equals(type)) {
				// folders

				log.info("Begin to create folder " + title);

				// pop the stack till the container equals to stack top
				while (!containerStack.empty()
						&& !container.equals(containerStack.peek())) {
					// sync pops
					containerStack.pop();
					boxFolderIdStack.pop();
				}

				BoxAPIConnection api = BoxUtils.getBoxAPIConnection(userId, uRepository);
				if (api == null) {
					// exit if no Box API connection could be made
					// returning all changed variables
					itemStatus.append("Cannot create Box folder for folder " + title);
					
					return returnMapWithStatus(
							containerStack, boxFolderIdStack, itemStatus);
				}

				if (title == null) {
					// exit if folder title is null
					itemStatus.append("Cannot create Box folder for null folder title");
					return returnMapWithStatus(
							containerStack, boxFolderIdStack, itemStatus);
				}
				
				// create box folder
				if (boxFolderIdStack.isEmpty())
				{
					// the folder should not be empty, log with error and update the item status
					itemStatus.append("Problem for adding \"" + title + "\" due to parent folder not existing.");
					log.error(itemStatus.toString());
					// return with error message
					HashMap<String, Object> rv = returnMapWithStatus(containerStack,
							boxFolderIdStack, itemStatus);
					return rv;
				}
				
				// get the first folder id from stack
				String peekFolderId = boxFolderIdStack.peek();
				
				BoxFolder parentFolder = new BoxFolder(api, peekFolderId);
				String sanitizedTitle = Utils.sanitizeName(type, title);
				try {
					BoxFolder.Info childFolderInfo = parentFolder
							.createFolder(sanitizedTitle);
					itemStatus.append("folder " + sanitizedTitle + " created.");

					// push the current folder id into the stack
					containerStack.push(contentUrl);
					boxFolderIdStack.push(childFolderInfo.getID());
					log.debug("top of stack folder id = " + containerStack.peek()
					+ " " + " container folder id=" + container);

					// get the BoxFolder object, get BoxFolder.Info object,
					// set description, and commit change
					BoxFolder childFolder = childFolderInfo.getResource();
					childFolderInfo.setDescription(description);
					childFolder.updateInfo(childFolderInfo);

				} catch (BoxAPIException e) {
					if (e.getResponseCode() == org.apache.http.HttpStatus.SC_CONFLICT) {
						// 409 means name conflict - item already existed
						itemStatus.append("There is already a folder with name "
								+ title + "- folder was not created in Box");
						// push the current folder id into the stack
						containerStack.push(contentUrl);
						boxFolderIdStack.push("title -- conflict with existing Box folder");
					} else {
						// log the exception message
						String errorMessage = "There is a problem " + e.getResponseCode() + " for adding folder " + title + " to Box: " + e.getMessage();
						log.error(errorMessage);
						itemStatus.append(errorMessage);

						// push the current folder id into the stack
						containerStack.push(contentUrl);
						boxFolderIdStack.push("folder \"" + title + "\" -- not added into Box");
					}
				}
			} else {
				// files
				long size = Utils.getJSONLong(contentItem, CONTENT_JSON_ATTR_SIZE);

				// check whether the file size exceeds Box's limit
				if (size >= MAX_CONTENT_SIZE_FOR_BOX) {
					// stop upload this file
					itemStatus.append(title + " is of size " + size
							+ ", too big to be uploaded to Box" + Utils.LINE_BREAK);
				} else {
					// Call the uploadFile method to upload file to Box.
					//
					log.debug("file stack peek= " + containerStack.peek() + " "
							+ " container=" + container);

					while (!containerStack.empty()
							&& !container.equals(containerStack.peek())) {
						// sync pops
						containerStack.pop();
						boxFolderIdStack.pop();
					}

					if (boxFolderIdStack.empty()) {
						String parentError = "Cannot find parent folder for file "
								+ contentUrl;
						itemStatus.append(parentError);
						log.error(parentError);
					} else {
						// insert records into database
						// ready for multi-thread processing
						log.info(" time to insert file record folder id=" + boxFolderIdStack.peek() );
						MigrationBoxFile mFile = new MigrationBoxFile(migrationId, userId, boxFolderIdStack.peek(),
								type, title, webLinkUrl,
								contentAccessUrl, description, author,
								copyrightAlert, size, null,
								null, null);
						fRepository.save(mFile);
					}
				}
			}

			HashMap<String, Object> rv = returnMapWithStatus(containerStack,
					boxFolderIdStack, itemStatus);
			return rv;
		}

		private HashMap<String, Object> returnMapWithStatus(
				java.util.Stack<String> containerStack,
				java.util.Stack<String> boxFolderIdStack,
				StringBuffer itemStatus) {
			HashMap<String, Object> rv = new HashMap<String, Object>();
			rv.put(Utils.PARAM_ITEM_STATUS, itemStatus);
			rv.put(Utils.PARAM_CONTAINER_STACK, containerStack);
			rv.put(Utils.PARAM_BOX_FOLDER_ID_STACK, boxFolderIdStack);
			return rv;
		}

		/**
		 * upload file to Box
		 * @param bFile
		 * @param httpContext
		 * @return
		 */
		@Async
		protected Future<String> uploadBoxFile(MigrationBoxFile bFile, HttpContext httpContext, String sessionId) {
			// status string
			StringBuffer status = new StringBuffer();
			
			// get all bFile params
			String id = bFile.getId();
			String userId = bFile.getUser_id();
			String type = bFile.getType();
			String boxFolderId = bFile.getBox_folder_id();
			String fileName = bFile.getTitle();
			
			if (fileName == null || fileName.isEmpty())
			{
				status.append(" uploadFile: file name is null or empty. ");
				// update job end time and status
				// return AsyncResult
				return new AsyncResult<String>(setUploadJobEndtimeStatus(id, status));
			}
			// replacing dots with _ don't need for Weblinks https://itsjira.umms.med.umich.edu/browse/TLCPM-653
			if(!type.equals(Utils.CTOOLS_RESOURCE_TYPE_URL)) {
				fileName = replaceDotsInFileNameExceptFileExtention(fileName);
			}
			
			String webLinkUrl = bFile.getWeb_link_url();
			String fileAccessUrl = bFile.getFile_access_url();
			String fileDescription = bFile.getDescription();
			String fileAuthor = bFile.getAuthor();
			String fileCopyrightAlert = bFile.getCopyright_alert();
			final long fileSize = bFile.getFile_size();

			BoxAPIConnection api = BoxUtils.getBoxAPIConnection(userId, uRepository);
			if (api == null)
			{
				status.append(" uploadFile: cannot get Box API Connection for file:" + fileName + ".");
				
				// update job end time and status
				// return AsyncResult
				return new AsyncResult<String>(setUploadJobEndtimeStatus(id, status));
			}

			log.info("begin to upload file " + fileName + " to box folder "
					+ boxFolderId + " " + fileAccessUrl);

			// mark the file as being processed
			fRepository.setMigrationBoxFileStartTime(id, new Timestamp(System.currentTimeMillis()));
			
			// create httpclient
			HttpClient httpClient = HttpClientBuilder.create().build();
			InputStream content = null;

			try {
				// get file content from /access url
				HttpGet getRequest = new HttpGet(fileAccessUrl);
				getRequest.setConfig(getRequestConfigWithTimeouts());
				getRequest.setHeader("Content-Type",
						"application/x-www-form-urlencoded");
				HttpResponse r = httpClient.execute(getRequest, httpContext);

				content = r.getEntity().getContent();

				if (Utils.isOfURLMIMEType(type)) {
					if (webLinkUrl == null || webLinkUrl.isEmpty())
					{
						status.append("Link "+ fileName + " could not be migrated due to empty URL link. ");
						return new AsyncResult<String>(setUploadJobEndtimeStatus(id, status));
					}
					try {
						// special handling of Web Links resources
						content = new ByteArrayInputStream(Utils.getWebLinkContent(
								fileName, webLinkUrl).getBytes());
					} catch (Exception e) {
						// return status with error message
						status.append("Link "
								+ fileName
								+ " could not be migrated. Please change the link name to be the complete URL and migrate the site again.");
						log.error(status.toString());
						return new AsyncResult<String>(setUploadJobEndtimeStatus(id, status));
					}
				}
			} catch (Exception e) {
				status.append("Cannot get content for " + fileName + " "
						+ e.getMessage());
				log.error(status.toString());
				
				// update job end time and status
				// return AsyncResult
				return new AsyncResult<String>(setUploadJobEndtimeStatus(id, status));
			}

			// update file name
			fileName = Utils.modifyFileNameOnType(type, fileName);
			String fileExtension = FilenameUtils.getExtension(fileName);
			if(fileExtension.isEmpty()){
				status.append(String.format("For the File \"%1$s\" with MIME type: \"%2$s\" is missing file extension in Box",fileName,type));
				log.error("For the File: {} with invalid MIME type: {} is missing file extension in Box",fileName,type);
			}

			// exit if content stream is null
			if (content == null)
			{
				status.append(" uploadFile: cannot get content for " + fileName);
				
				// update job end time and status
				// return AsyncResult
				return new AsyncResult<String>(setUploadJobEndtimeStatus(id, status));
			}

			BufferedInputStream bContent = null;
			try {

				bContent = new BufferedInputStream(content);
				BoxFolder folder = new BoxFolder(api, boxFolderId);
				log.info("upload file " + fileName + " size " + fileSize + " to folder " + folder.getID());

				fileName = Utils.sanitizeName(type, fileName);
				BoxFile.Info newFileInfo = folder.uploadFile(bContent,
						fileName, fileSize, new ProgressListener() {
					public void onProgressChanged(long numBytes,
							long totalBytes) {
						log.debug(numBytes + " out of total bytes "
								+ totalBytes);
					}
				});


				BoxFile newFile = newFileInfo.getResource();
				// set file description
				status = setBoxFileDescription(status, boxFolderId, fileName,
						fileDescription, newFileInfo, newFile);
				// set file metadata
				status = setBoxFileMetadata(status, boxFolderId, fileName, fileAuthor,
						fileCopyrightAlert, newFile);

				log.info("upload success for file " + fileName);
			} catch (BoxAPIException e) {
				if (e.getResponseCode() == org.apache.http.HttpStatus.SC_UNAUTHORIZED)
				{
					// 401 means Box access token expired
					// log it, but do not change status, so this file still sits in pool
					String errorString = "Box access token expired for user " + userId;
					log.error(errorString);
					
					// we will clean up start_time, end_time, and status for the file, 
					// so that it would be picked up again for migration
					fRepository.setMigrationBoxFileStartTime(id, null);
					fRepository.setMigrationBoxFileEndTime(id, null);
					fRepository.setMigrationBoxFileStatus(id, null);
				}
				else if (e.getResponseCode() == org.apache.http.HttpStatus.SC_CONFLICT) {
					// 409 means name conflict - item already existed
					String conflictString = "There is already a file with name "
							+ fileName + " - file was not added to Box";
					log.error(conflictString);
					status.append(conflictString + Utils.LINE_BREAK);
				}
				else
				{
					String errorString = "There is a problem uploading file \"" + fileName
							+ "\" to Box folder " + boxFolderId + ": " + e.getMessage();
					log.error(this + errorString);
					status.append(errorString + Utils.LINE_BREAK);
				}
			} catch (IllegalArgumentException iException) {
				String ilExceptionString = "problem creating BufferedInputStream for file "
						+ fileName
						+ " with content and length "
						+ fileSize
						+ iException;
				log.error(ilExceptionString);
				status.append(ilExceptionString + Utils.LINE_BREAK);
			} catch (Exception e) {
				String ilExceptionString = "problem creating BufferedInputStream for file "
						+ fileName + " with content and length " + fileSize + e;
				log.error(ilExceptionString);
				status.append(ilExceptionString + Utils.LINE_BREAK);
			} finally {
				if (bContent != null) {
					try {
						bContent.close(); // The BufferedInputStream needs to be
						// closed
					} catch (IOException ioException) {
						String ioExceptionString = "problem closing FileChannel for file "
								+ fileName + " " + ioException;
						log.error(ioExceptionString);
						status.append(ioExceptionString + Utils.LINE_BREAK);
					}
				}
			}
			if (content != null) {
				try {
					content.close(); // The input stream needs to be closed
				} catch (IOException ioException) {
					String ioExceptionString = "zipFiles: problem closing Inputstream content for file "
							+ fileName + " " + ioException;
					log.error(ioExceptionString);
					status.append(ioExceptionString + Utils.LINE_BREAK);
				}
			}

			// box upload success
			if (status.length() == 0) {
				status.append("Box upload successful for file " + fileName + ".");
			}
			
			// update job end time and status
			// return AsyncResult
			return new AsyncResult<String>(setUploadJobEndtimeStatus(id, status));
		}

		/**
		 * set the metadata (author, copyright alert) of Box file
		 * @param status
		 * @param boxFolderId
		 * @param fileName
		 * @param fileAuthor
		 * @param fileCopyrightAlert
		 * @param newFile
		 * @return
		 */
		private StringBuffer setBoxFileMetadata(StringBuffer status,
				String boxFolderId, String fileName, String fileAuthor,
				String fileCopyrightAlert, BoxFile newFile) {
			try
			{
				// assign meta data
				Metadata metaData = new Metadata();
				metaData.add("/copyrightAlert",
						fileCopyrightAlert == null ? "false" : "true");
				metaData.add("/author", fileAuthor==null ? "":fileAuthor);
				newFile.createMetadata(metaData);
			}
			catch (BoxAPIException e)
			{
				String errorString = "There is a problem add metadata (copyright alert, or file author) to file \"" + fileName
						+ "\" in Box folder " + boxFolderId + ": " + e.getMessage();
				log.error(this + errorString);
				status.append(errorString + Utils.LINE_BREAK);
			}
			
			return status;
		}

		/**
		 * set the description field value of Box file
		 * @param status
		 * @param boxFolderId
		 * @param fileName
		 * @param fileDescription
		 * @param newFileInfo
		 * @param newFile
		 * @return
		 */
		private StringBuffer setBoxFileDescription(StringBuffer status,
				String boxFolderId, String fileName, String fileDescription,
				BoxFile.Info newFileInfo, BoxFile newFile) {
			try
			{
				// get the BoxFile object, get BoxFile.Info object, set description,
				// and commit change
				newFileInfo.setDescription(fileDescription);
				newFile.updateInfo(newFileInfo);
			}
			catch (BoxAPIException e)
			{
				String errorString = "There is a problem add description to file \"" + fileName
						+ "\" in Box folder " + boxFolderId + ": " + e.getMessage();
				log.error(this + errorString);
				status.append(errorString + Utils.LINE_BREAK);
			}
			
			return status;
		}

	//Filenames could contain dot(.)  when files migrated to box this will look like unknow extension
	// and cannot be previewed in box. see MigrationTaskServiceTest.java for Use Cases
	public static String replaceDotsInFileNameExceptFileExtention(String fileName) {
		if (!(StringUtils.countOccurrencesOf(fileName, ".") > 1)) {
			return fileName;
		}
		String fileExtension = FilenameUtils.getExtension(fileName);
		fileName = fileName.replaceFirst("[.][^.]+$", "").replace(".", "_");
		fileName = fileName + Utils.EXTENSION_SEPARATOR + fileExtension;
		return fileName;
	}

	/**
		 * update the status and end time for file item
		 * @param id
		 * @param status
		 */
		private String setUploadJobEndtimeStatus(String id, StringBuffer status) {
			fRepository.setMigrationBoxFileEndTime(id, new java.sql.Timestamp(System.currentTimeMillis()));
			fRepository.setMigrationBoxFileStatus(id, status.toString());
			return status.toString();
		}

		/**
		 * Based on the JSON returned inside BoxAPIException object, find out the id
		 * of conflicting box folder
		 *
		 * @return id
		 */
		private String getExistingBoxFolderIdFromBoxException(BoxAPIException e,
				String folderTitle) {
			String existingFolderId = null;
			// here is the example JSON returned
			// {
			// "type":"error",
			// "status":409,
			// "code":"item_name_in_use",
			// "context_info":{
			// "conflicts":[
			// {
			// "type":"folder",
			// "id":"5443268429",
			// "sequence_id":"0",
			// "etag":"0",
			// "name":"folder1"
			// }
			// ]
			// },
			// "help_url":"http:\/\/developers.box.com\/docs\/#errors",
			// "message":"Item with the same name already exists",
			// "request_id":"153175908556537d483098d"
			// }
			if (e.getResponse() == null)
				return null;
			try
			{
				JSONObject boxException = new JSONObject(e.getResponse());
				if (boxException == null)
					return null;
				JSONObject context_info = boxException.getJSONObject("context_info");
				if (context_info == null)
					return null;
				JSONArray conflicts = context_info.getJSONArray("conflicts");
				if (conflicts == null)
					return null;
	
				for (int index = 0; index < conflicts.length(); index++) {
					JSONObject conflict = conflicts.getJSONObject(index);
					String conflictType = conflict.getString("type");
					if (conflictType != null && conflictType.equals("folder")) {
						String folderId = conflict.getString("id");
						String folderName = conflict.getString("name");
						if (folderName != null && folderName.equals(folderTitle)) {
							// found the existing folder id, break
							existingFolderId = folderId;
							break;
						}
					}
				}
			}
			catch (JSONException jsonException)
			{
				log.error("Problem parsing JSON object based on " + e.getResponse());
			}
			return existingFolderId;
		}


		/**************** MailArchive content ***********/
		/**
		 * Download MailArchive resource in zip file
		 * @param env
		 * @param request
		 * @param response
		 * @param userId
		 * @param sessionAttributes
		 * @param site_id
		 * @param migrationId
		 * @param repository
		 */
		public void downloadMailArchiveZipFile(Environment env, HttpServletRequest request,
				HttpServletResponse response, String userId,
				HashMap<String, Object> sessionAttributes, String site_id,
				String migrationId, MigrationRepository repository) {
			// hold download status

			Map<String, String[]> parameterMap = request.getParameterMap();
			String destination_type = parameterMap.get("destination_type")[0];
			String site_name = parameterMap.get("site_name")[0];
			JSONObject downloadStatus = Utils.migrationStatusObject(destination_type);
			// login to CTools and get sessionId
			if (sessionAttributes.containsKey(Utils.SESSION_ID)) {
				String sessionId = (String) sessionAttributes.get(Utils.SESSION_ID);
				HttpContext httpContext = (HttpContext) sessionAttributes
						.get("httpContext");
				try {
					//
					// Sends the response back to the user / browser. The
					// content for zip file type is "application/zip". We
					// also set the content disposition as attachment for
					// the browser to show a dialog that will let user
					// choose what action will he do to the sent content.
					//
					response.setContentType(Utils.MIME_TYPE_ZIP);
					response.setCharacterEncoding("UTF-8");

					String zipFileName = generateMailArchiveName(site_name, destination_type)+".zip";
					response.setHeader("Content-Disposition",
							"attachment;filename=\"" + zipFileName + "\"");

					ZipOutputStream out = new ZipOutputStream(response.getOutputStream());
					String compressionLevel = env.getProperty(Utils.ENV_PROPERTY_ZIP_COMPRESSSION_LEVEL);
					if ((compressionLevel == null) || (compressionLevel.isEmpty())) {
						// set compression level to high by default
						out.setLevel(9);
						log.error("The property \"zip.compression.level\" is not set");
					} else {
						out.setLevel(Integer.parseInt(compressionLevel.trim()));
					}

					log.info("Starting mail archive download for site " + site_id);
					downloadStatus = getMailArchiveZipContent(env, site_id, downloadStatus,
							sessionId, httpContext, out, request,migrationId);

					out.flush();
					out.close();
					log.info("Finished mail archive download for site " + site_id);


				} catch (RestClientException e) {
					String errorMessage = Utils.STATUS_FAILURE + " Cannot find site by siteId: " + site_id
							+ " " + e.getMessage();
					Response.status(Response.Status.NOT_FOUND).entity(errorMessage)
					.type(MediaType.TEXT_PLAIN).build();
					log.error(errorMessage);
					downloadStatus= errHandlingInDownloadMailArchiveZipFile(site_id, downloadStatus, errorMessage);
				} catch (IOException e) {
					String errorMessage = Utils.STATUS_FAILURE + " problem adding zip folder for siteId: " + site_id
							+ " due to " + e.getMessage();
					Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(errorMessage)
							.type(MediaType.TEXT_PLAIN).build();
					log.error(errorMessage);
					downloadStatus= errHandlingInDownloadMailArchiveZipFile(site_id, downloadStatus, errorMessage);

				} catch (Exception e) {
					String errorMessage = Utils.STATUS_FAILURE + " Migration status for " + site_id + " "
							+ e.getClass().getName();
					Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(errorMessage).type(MediaType.TEXT_PLAIN)
					.build();
					log.error(errorMessage);
					downloadStatus= errHandlingInDownloadMailArchiveZipFile(site_id, downloadStatus, errorMessage);
				}
			} else {
				String userError = "Cannot become user " + userId;
				log.error(userError);
				downloadStatus= errHandlingInDownloadMailArchiveZipFile(site_id, downloadStatus, userError);
			}
			// update the status and end_time of migration record
			setMigrationEndTimeAndStatus(migrationId, repository, downloadStatus);

			return;
		}

		// figure out the name of the mail archive zip file.
		private String generateMailArchiveName(String site_name, String destination_type) {
			String formatText = null;
			
			if (Utils.isItMailArchiveDirectory(destination_type)) {
				formatText = "_directory";
			} else if (Utils.isItMailArchiveMbox(destination_type)) {
				formatText = "_mbox";
			}

			String zipFileName = Utils.sanitizeName(String.format("%s_mailarchive%s",site_name,formatText));

			log.info("*** This is Mail Archive Zip Download name: [{}]",zipFileName);
			return zipFileName;
		}

	private JSONObject errHandlingInDownloadMailArchiveZipFile(String site_id, JSONObject downloadStatus, String errorMessage) {
		JSONArray errArray = new JSONArray();
		downloadStatus.put(Utils.REPORT_ATTR_STATUS, Utils.REPORT_STATUS_ERROR);
		JSONObject count = (JSONObject)downloadStatus.get(Utils.REPORT_ATTR_COUNTS);
		int errorCount = (Integer)count.get(Utils.REPORT_ATTR_COUNTS_ERRORS);
		count.put(Utils.REPORT_ATTR_COUNTS_ERRORS, errorCount+1);
		downloadStatus.put(Utils.REPORT_ATTR_ITEMS, errArray.put(errHandlingForZiparchive(site_id, errorMessage)));
		downloadStatus.put(Utils.REPORT_ATTR_COUNTS,count);
		return downloadStatus;
	}

	/**
		 * get MailArchive message content into ZipOutputStream
		 * @param env
		 * @param site_id
		 * @param downloadStatus
		 * @param sessionId
		 * @param httpContext
		 * @param out
		 * @return
		 * @throws IOException
		 */
		private JSONObject getMailArchiveZipContent(Environment env, String site_id,
				JSONObject downloadStatus, String sessionId,
				HttpContext httpContext, ZipOutputStream out, HttpServletRequest request, String migrationId) throws IOException {


			// get all mail channels inside the site
			RestTemplate restTemplate = new RestTemplate();
			String requestUrl = Utils.directCallUrl(env, "mailarchive/siteChannels/" + site_id + ".json?", sessionId);
			JSONObject channelsJSON = null;
			channelsJSON = new JSONObject(restTemplate.getForObject(requestUrl,
					String.class));

			if (!channelsJSON.has(Utils.JSON_ATTR_MAILARCHIVE_COLLECTION))
			{
				return downloadStatus;
			}

			JSONArray channels = channelsJSON.getJSONArray(Utils.JSON_ATTR_MAILARCHIVE_COLLECTION);

			boolean folderForChannels = false;
			if (channels.length() > 1)
			{
				// if the site have more than one MailArchive channel
				// create zip folder for each channel
				folderForChannels = true;
			}
			JSONArray allChannelMsgItems = new JSONArray();
			Map<String, String[]> parameterMap = request.getParameterMap();
			String destination_type = parameterMap.get("destination_type")[0];

			for (int iChannel = 0; iChannel < channels.length(); iChannel++) {
				JSONObject channel = channels.getJSONObject(iChannel);
				String channelId = channel.getString("data");
				channelId = channelId.substring(("/mailarchive/channel/" + site_id + "/").length());
				String channelName = channel.getString("displayTitle");

				if (folderForChannels)
				{
					ZipEntry folderEntry = new ZipEntry(channelName + "/");
					try {
						out.putNextEntry(folderEntry);
					} catch (IOException e) {
						String ioError = "downloadMailArchiveZipFile: problem adding zip folder for MailArchive channel "
								+ channelName + " " + e;
						log.error(ioError);
					}
				}

				// get all email messages in the channel
				requestUrl = Utils.directCallUrl(env, "mailarchive/channelMessages/" + site_id + "/" + channelId + ".json?", sessionId);
				JSONObject messagesJSON = new JSONObject(restTemplate.getForObject(requestUrl,
						String.class));
				JSONArray messages = messagesJSON.getJSONArray(Utils.JSON_ATTR_MAILARCHIVE_COLLECTION);

				if (Utils.isItMailArchiveDirectory(destination_type)) {
					for (int iMessage = 0; iMessage < messages.length(); iMessage++) {
						JSONObject singleMailZipMsgStatus = new JSONObject();
						StatusReport report = new StatusReport();
						JSONObject message = messages.getJSONObject(iMessage);

						// create file for each message
						String messageFolderName = getMailArchiveMessageFolderName(message, channelName, folderForChannels);
						report.setId(messageFolderName);


						// 1. write the message file
						report = handleMailArchiveMessage(out, message, messageFolderName,
								report);

						// 2. get attachments, if any
						if (report.getStatus() != Utils.REPORT_STATUS_ERROR) {
							report = handleMailArchiveMessageAttachments(
									sessionId, httpContext, out, message, messageFolderName, report);
						}
						singleMailZipMsgStatus.put(Utils.REPORT_ATTR_ITEM_ID, report.getId());
						singleMailZipMsgStatus.put(Utils.REPORT_ATTR_ITEM_STATUS, report.getStatus());
						singleMailZipMsgStatus.put(Utils.REPORT_ATTR_MESSAGE, report.getMsg());

						allChannelMsgItems.put(singleMailZipMsgStatus);
					}

				} else if (Utils.isItMailArchiveMbox(destination_type)) {
					String site_name = request.getParameterMap().get("site_name")[0];
					String messageFolderName = getMailArchiveMboxMessageFolderName(site_name,destination_type);
					ZipEntry fileEntry = new ZipEntry(messageFolderName + Utils.MAIL_MBOX_MESSAGE_FILE_NAME);
					out.putNextEntry(fileEntry);
					for (int iMessage = 0; iMessage < messages.length(); iMessage++) {
						JSONObject message = messages.getJSONObject(iMessage);
						String date = getProperty(message, Utils.JSON_ATTR_MAIL_DATE);
						String subject = getEmailSubject(message);
						String messageId = date + " " + subject;
						String emailMessage = message.toString();
						AttachmentHandler attachmentHandler = new AttachmentHandler(request);
						attachmentHandler.setEnv(env);
						EmailFormatter emailFormatter = null;
						try {
							emailFormatter = new EmailFormatter(emailMessage, attachmentHandler);
							MailResultPair mboxFormatTextPlusStatus = emailFormatter.mboxFormat();
							String mboxMessage = mboxFormatTextPlusStatus.getMessage();
							JSONObject singleMboxMsgStatus = mboxFormatTextPlusStatus.getReport().getJsonReportObject();
							if (mboxMessage != null) {
								allChannelMsgItems.put(singleMboxMsgStatus);
								out.write(mboxMessage.getBytes());
							} else {
								allChannelMsgItems.put(singleMboxMsgStatus);
								log.error(String.format("Mbox Formatting for message with Id (%s) not successful with migrationid " +
										"(%s) for site (%s)", messageId, migrationId, site_id));
							}
						} catch (IOException e) {
							String msg = "Mbox zip file could not be downloaded due to bad json response";
							allChannelMsgItems.put(errHandlingForZiparchive(messageId, msg));
							log.error(msg + "for message: " + messageId + " " + e.getMessage());
							continue;
						}
					}

				}
			}
			downloadStatus = finalReportObjBuilderForMailZipMigration(downloadStatus, allChannelMsgItems);
			return downloadStatus;
		}

	private JSONObject finalReportObjBuilderForMailZipMigration(JSONObject downloadStatus, JSONArray messagesStatus) {
		JSONArray errAndPartialSuccessList = new JSONArray();
		int successes,errors,partials;
		successes = errors=partials=0;
		for (int i = 0; i < messagesStatus.length(); i++) {
			JSONObject perMsg = messagesStatus.getJSONObject(i);
			String msgStatus = (String) perMsg.get(Utils.REPORT_ATTR_ITEM_STATUS);
			if (msgStatus.equals(Utils.REPORT_STATUS_OK)) {
				successes = successes + 1;
			} else if (msgStatus.equals(Utils.REPORT_STATUS_PARTIAL)) {
				partials = partials + 1;
				errAndPartialSuccessList.put(messagesStatus.get(i));
			} else if (msgStatus.equals(Utils.REPORT_STATUS_ERROR)) {
				errors = errors + 1;
				errAndPartialSuccessList.put(messagesStatus.get(i));
			}
		}
		JSONObject counts = new JSONObject();
		counts.put(Utils.REPORT_ATTR_COUNTS_SUCCESSES,successes);
		counts.put(Utils.REPORT_ATTR_COUNTS_ERRORS,errors);
		counts.put(Utils.REPORT_ATTR_COUNT_PARTIALS,partials);
		downloadStatus.put(Utils.REPORT_ATTR_COUNTS,counts);
		downloadStatus.put(Utils.REPORT_ATTR_ITEMS,errAndPartialSuccessList);
		if (errors > 0) {
			downloadStatus.put(Utils.REPORT_ATTR_STATUS, Utils.REPORT_STATUS_ERROR);
		} else if (partials > 0 & successes > 0) {
			downloadStatus.put(Utils.REPORT_ATTR_STATUS, Utils.REPORT_STATUS_PARTIAL);
		} else
			downloadStatus.put(Utils.REPORT_ATTR_STATUS, Utils.REPORT_STATUS_OK);
		return downloadStatus;
	}

	private String getProperty(JSONObject message, String jsonAttribute) {
		JSONArray headers = message.getJSONArray(Utils.JSON_ATTR_MAIL_HEADERS);
		String date = getHeaderAttribute(headers, jsonAttribute);;
		return date;
	}


	/**
		 * output MailArchive message content
		 * @param out
		 * @param message
		 * @param messageFolderName
		 * @param messageStatus
		 * @return
		 * @throws IOException
		 */
		private StatusReport handleMailArchiveMessage(ZipOutputStream out,
				JSONObject message, String messageFolderName,
				StatusReport messageStatus)  {
				String problemMsg="Problem getting message content for Mail Zip Download";
			try {
				// get the html file content first
				String messageContent = message.has(Utils.JSON_ATTR_MAIL_BODY)?message.getString(Utils.JSON_ATTR_MAIL_BODY):"";

				ZipEntry fileEntry = new ZipEntry(messageFolderName + Utils.MAIL_MESSAGE_FILE_NAME);
				out.putNextEntry(fileEntry);

				// output message header info
				JSONArray headers = message.getJSONArray(Utils.JSON_ATTR_MAIL_HEADERS);
				for (int iHeader = 0; iHeader<headers.length(); iHeader++)
				{
					String header = headers.getString(iHeader) + "\r\n";
					out.write(header.getBytes());
				}
				out.write("\r\n".getBytes());
				// output message body
				out.write(messageContent.getBytes());


				messageStatus.setStatus(Utils.REPORT_STATUS_OK);
			} catch (java.net.MalformedURLException e) {
				log.error("Mail Zip download has MalFormedURLException due to "+e.getMessage());
				messageStatus.setStatus(Utils.REPORT_STATUS_ERROR);
				messageStatus.setMsg(problemMsg);
			} catch (IOException e) {
				log.error("Mail Zip download has IOException due to "+e.getMessage());
				messageStatus.setStatus(Utils.REPORT_STATUS_ERROR);
				messageStatus.setMsg(problemMsg);
			} catch (Exception e) {
				log.error("Mail Zip download has Exception due to "+e.getMessage());
				messageStatus.setStatus(Utils.REPORT_STATUS_ERROR);
				messageStatus.setMsg(problemMsg);
			}
			return messageStatus;
		}

		private JSONArray handleMailArchiveMboxMessage(ZipOutputStream out,
				String message, String messageFolderName,
				JSONArray messagesStatus) {

			try {
				// get the html file content first

				ZipEntry fileEntry = new ZipEntry(messageFolderName + Utils.MAIL_MBOX_MESSAGE_FILE_NAME);
				out.putNextEntry(fileEntry);

				// output message body
				out.write(message.getBytes());


			} catch (java.net.MalformedURLException e) {
				String msg = "Mbox zip file could not be downloaded due to MalformedURLException ";
				messagesStatus.put(errHandlingForZiparchive(messageFolderName,  msg));
				log.error(msg+e.getMessage());
			} catch (IOException e) {
				String msg = "Mbox zip file could not be downloaded due to IOException ";
				messagesStatus.put(errHandlingForZiparchive(messageFolderName,msg));
				log.error(msg+e.getMessage());
			}
			return messagesStatus;
		}

	private JSONObject errHandlingForZiparchive(String msgIdentifier, String errMsg) {
		JSONObject errRes=new JSONObject();
		errRes.put(Utils.REPORT_ATTR_ITEM_STATUS, Utils.REPORT_STATUS_ERROR);
		errRes.put(Utils.REPORT_ATTR_MESSAGE, errMsg);
		errRes.put(Utils.REPORT_ATTR_ITEM_ID,msgIdentifier);
		return errRes;
	}

	/**
		 * put mail message attachments into zip
		 * @param sessionId
		 * @param httpContext
		 * @param out
		 * @param message
		 * @param messageFolderName
		 * @return
		 */
		private StatusReport handleMailArchiveMessageAttachments(String sessionId,
				HttpContext httpContext, ZipOutputStream out, JSONObject message,
				String messageFolderName, StatusReport sr) {
			JSONArray attachments = message.getJSONArray(Utils.JSON_ATTR_MAIL_ATTACHMENTS);
			int successes, errors;
			successes = errors = 0;
			for (int iAttachment = 0; iAttachment < attachments.length(); iAttachment++) {
				// get each attachment
				JSONObject attachment = attachments.getJSONObject(iAttachment);
				String type = attachment.has(Utils.JSON_ATTR_MAIL_TYPE) ? attachment.getString(Utils.JSON_ATTR_MAIL_TYPE) : "";
				String name = attachment.has(Utils.JSON_ATTR_MAIL_NAME) ? attachment.getString(Utils.JSON_ATTR_MAIL_NAME) : "";
				String url = attachment.has(Utils.JSON_ATTR_MAIL_URL) ? attachment.getString(Utils.JSON_ATTR_MAIL_URL) : "";
				// Call the zipFiles method for creating a zip stream.
				String fileStatus = zipFiles(type, httpContext,
						messageFolderName + name, name, "", url,
						sessionId, out, new HashMap<String, String>());
				sr.addAllAttachmnts(name);
				if (fileStatus!=null &&fileStatus.contains("successfully")) {
					successes=successes+1;
				} else {
					errors=errors+1;
					sr.addFailedAttachments(name);
				}

			}
			if (errors > 0) {
				String msg = sr.getFailedAttachments().size() + "/" + sr.getAllAttachments().size()
						+ " attachments " + org.apache.commons.lang3.StringUtils.join(sr.getFailedAttachments()
						+" failed to be exported and they are missing from message");
				sr.setStatus(Utils.REPORT_STATUS_PARTIAL);
				sr.setMsg(msg);
			}else{
				sr.setStatus(Utils.REPORT_STATUS_OK);
				sr.setMsg(Utils.REPORT_SUCCESS_MSG);
			}

			return sr;
		}

		/**
		 * construct the zip folder name for a MailArchive message
		 * @param message
		 * @param channelName
		 * @param folderForChannels
		 * @return
		 */
		private String getMailArchiveMessageFolderName(JSONObject message, String channelName, boolean folderForChannels)
		{
			// get message information from header
			JSONArray headers = message.getJSONArray(Utils.JSON_ATTR_MAIL_HEADERS);
			String subject = getEmailSubject(message);
			String sender = getHeaderAttribute(headers, Utils.JSON_ATTR_MAIL_FROM);
            		if (sender.indexOf('<') != -1) {
                		sender = sender.substring(sender.indexOf('<') + 1, sender.indexOf('>'));
            		}
            		String date = getHeaderAttribute(headers, Utils.JSON_ATTR_MAIL_DATE);
			try {
				DateFormat localTimeFormat = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z");
				Date localDateAndTime = localTimeFormat.parse(date);
				long epochTime = localDateAndTime.getTime();
				DateFormat ascTimePattern = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
				ascTimePattern.setTimeZone(TimeZone.getTimeZone("America/New_York"));
				date = ascTimePattern.format(new Date(epochTime));
			} catch (java.text.ParseException e) {
				log.error("Error occurred while parsing the Date: " + date + " due to " + e.getMessage());
			}
			// create file for each message
			String messageFolderName = "";
			if (folderForChannels)
			{
				messageFolderName = channelName + Utils.PATH_SEPARATOR;
			}
			// If there is a channel then put in the separator space.
			if (! messageFolderName.equals("")) {
				messageFolderName += " ";
			}
			messageFolderName += date + " " + sender + " " + subject;
			if(messageFolderName.length()>100){
				messageFolderName=messageFolderName.substring(0,100);
			}
			messageFolderName = Utils.sanitizeName(Utils.COLLECTION_TYPE, messageFolderName) + "/";

			return messageFolderName;
		}

	private String getEmailSubject(JSONObject message) {
		// get the subject from the message that seems to be the place where to get reliable info
		JSONArray headers = message.getJSONArray(Utils.JSON_ATTR_MAIL_HEADERS);
		String subject = message.getString(Utils.JSON_ATTR_MAIL_SUBJECT);
		if (subject != null) {
			return subject;
		}
		// or else get subject from the header
		return getHeaderAttribute(headers, Utils.JSON_ATTR_MAIL_SUBJECT_FROM_HEADER);
	}

	private String getMailArchiveMboxMessageFolderName(String site_name,String destination_type) {
			return generateMailArchiveName(site_name,destination_type)+ "/";
		}

		/**
		 * get mail message info from header
		 * @param headers
		 * @param attribute
		 * @return
		 */
		private String getHeaderAttribute(JSONArray headers, String attribute)
		{
			String rv = "";
			for (int iHeader = 0; iHeader < headers.length(); iHeader++) {
				String header = headers.getString(iHeader);
				if (header.startsWith(attribute))
				{
					rv = header.substring(attribute.length());
					break;
				}
			}

			return rv;
		}

		public HashMap<String, String> processAddEmailMessages(
				HttpServletRequest request, HttpServletResponse response,
				String target, String remoteUser, HashMap<String, String> rv,
				String googleGroupId, String siteId, String toolId,
				HashMap<String, Object> saveMigration) {
			if (!saveMigration.containsKey("migration")) {
				// no new Migration record created
				rv.put("errorMessage", "Cannot create migration records for user "
						+ remoteUser + " and site=" + siteId);
				return rv;
			}

			Migration migration = (Migration) saveMigration.get("migration");
			String migrationId = migration.getMigration_id();

			// get session
			HashMap<String, Object> sessionAttributes = Utils
					.login_becomeuser(env, remoteUser);
			if (sessionAttributes == null || !sessionAttributes.containsKey(Utils.SESSION_ID)) {
				rv.put("errorMessage", "Cannot create become user "
						+ remoteUser + " and site=" + siteId);
				return rv;
			}

			String sessionId = (String) sessionAttributes.get(Utils.SESSION_ID);

			// get all mail channels inside the site
			RestTemplate restTemplate = new RestTemplate();
			String requestUrl = Utils.directCallUrl(env, "mailarchive/siteChannels/" + siteId + ".json?", sessionId);
			JSONObject channelsJSON = null;
			channelsJSON = new JSONObject(restTemplate.getForObject(requestUrl,
					String.class));

			if (!channelsJSON.has(Utils.JSON_ATTR_MAILARCHIVE_COLLECTION))
			{
				rv.put("errorMessage", "Cannot get mail archive information for site=" + siteId);
				return rv;
			}

			JSONArray channels = channelsJSON.getJSONArray(Utils.JSON_ATTR_MAILARCHIVE_COLLECTION);
			for (int iChannel = 0; iChannel < channels.length(); iChannel++) {
				JSONObject channel = channels.getJSONObject(iChannel);
				String channelId = channel.getString("data");
				channelId = channelId.substring(("/mailarchive/channel/" + siteId + "/").length());
				String channelName = channel.getString("displayTitle");

				// get all email messages in the channel
				requestUrl = Utils.directCallUrl(env, "mailarchive/channelMessages/" + siteId + "/" + channelId + ".json?", sessionId);
				JSONObject messagesJSON = new JSONObject(restTemplate.getForObject(requestUrl,
						String.class));
				JSONArray messages = messagesJSON.getJSONArray(Utils.JSON_ATTR_MAILARCHIVE_COLLECTION);
				for (int iMessage = 0; iMessage < messages.length(); iMessage++) {
					JSONObject message = messages.getJSONObject(iMessage);
					String messageId = message.getString("id");
					// construct the MigrationEmailMessage object
					MigrationEmailMessage mMessage = new MigrationEmailMessage(
							messageId, migrationId,
							remoteUser, googleGroupId,
							message.toString(), null,
							null, null);
					try
					{
						mRepository.save(mMessage);
					}
					catch (Exception e)
					{
						log.error("Problem saving the MigrationEmailMessage " + messageId + " with GoogleGroupId " + googleGroupId + " into database ");
					}


				}
			}
			return rv;
		}

		/**
		 * call GG microservice to upload message content
		 * @param googleGroup
		 * @param rcf822Email
		 * @return
		 */

		ApiResultWrapper addEmailToGoogleGroup(String googleGroup, String rcf822Email) {
			log.info("addEmailToGoogleGroup: group: {}",googleGroup);
			log.debug("addEmailToGoogleGroup: email: {}",rcf822Email);

			GGBApiWrapper ggb = establishGGBConnection();

			String archive_url = "/groups/"+googleGroup+"/messages";
			ApiResultWrapper arw= ggb.post_request(archive_url,rcf822Email);
			return arw;

		}

		// Get the json version of the site info.
		protected JSONObject getSiteInfoJson(String sessionId, String siteId) {

			// get the site info from ctools
			JSONObject siteJSONObject = null;

			// get site info for site as json.
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/<siteId>.json?_sessionId=<sessionId>"
			String requestUrl = Utils.directCallUrl(env, "site/" + siteId + ".json?", sessionId);
			log.debug("siteInfo url: " + requestUrl);
			try {
				String siteJson = restTemplate.getForObject(requestUrl,
						String.class);
				siteJSONObject = new JSONObject(siteJson);
			} catch (RestClientException e) {
				log.error(requestUrl + e.getMessage());
			}
			return siteJSONObject;
		}

		/////////////////
		// Get the information required to setup the Google Group from CTools.

		// The group email address might come from various sources.
		public JSONObject getCToolsGroupInfoJson(String sessionId,
				String siteId, String group_email) {
			JSONObject siteJSONObject = getSiteInfoJson(sessionId,siteId);
			if(siteJSONObject==null) {
				return null;
			}
			JSONObject group_info_object = create_group_info_object(siteId, group_email, siteJSONObject);

			return group_info_object;
		}

		public static JSONObject create_group_info_object(String siteId, String group_email, JSONObject siteJSONObject) {
			JSONObject group_info = new JSONObject();

			// include site id for tracking purposes.
			group_info.put("site_id",siteId);
			group_info.put("title", siteJSONObject.optString("title",""));
			group_info.put("group_email",group_email);
			group_info.put("description", siteJSONObject.optString("description",""));

			log.debug(String.format("group_info: [%s]",group_info.toString()));
			return group_info;
		}

		// Get the new email prefix from the old one used in the archive.
		public JSONObject getCToolsGroupInfoJson(String sessionId,String siteId) {
			String archiveEmail = getArchiveEmail(sessionId, siteId);
			if(archiveEmail==null){
				return null;
			}
			return getCToolsGroupInfoJson(sessionId, siteId, archiveEmail);
		}

		//https://ctdevsearch.dsc.umich.edu/direct/mailarchive/siteMessages/22b5d237-0a22-4995-a4b1-d5022dd90a86.json
		// Get the new Google email address based on the email name available in the archive.
		protected String getArchiveEmail(String sessionId, String siteId) {
			RestTemplate restTemplate = new RestTemplate();
			String requestUrl = Utils.directCallUrl(env, "mailarchive/siteMessages/"+siteId+".json?", sessionId);
			String archiveJson = null;
			try {
				archiveJson = restTemplate.getForObject(requestUrl,String.class);
			} catch (RestClientException e) {
				log.error(requestUrl + e.getMessage());
				return null;
			}

			String archiveEmail = extractArchiveEmailName(archiveJson);
			String suffix = env.getProperty(Utils.GGB_GOOGLE_GROUP_DOMAIN);
			if (suffix == null) {
				log.warn("no google suffix provided. Property name is: {}",Utils.GGB_GOOGLE_GROUP_DOMAIN);
				suffix = "";
			}

			String group_name = archiveEmail+"@"+suffix;
			log.info("from extractArchiveEmailName: {} ",archiveEmail);

			return group_name;
		}

		// Breakout the email name used in CTools for this archive.
		static public String extractArchiveEmailName(String emailArchive) {
			String archive_email_name = null;
			JSONObject jo = new JSONObject(emailArchive);
			JSONArray ja = jo.getJSONArray("mailarchive_collection");

			//this loop will make sure that archive_email_name is set to one of
			//a valid group name based on the first To: header it finds or null
			outerloop:
			for(int j=0;j<ja.length();j++) {
				JSONArray allHeaders = (JSONArray) ((JSONObject) ja.get(j)).get("headers");
				int myJsonArraySize = allHeaders.length();
				for (int i = 0; i < myJsonArraySize && archive_email_name == null; i++) {
					String header = (String) allHeaders.get(i);
					// look for the To: header
					if (!header.startsWith("To: ")) {
						continue;
					}
					// Pull out the email name used by the old archive.
					log.debug("aAEN: To header: {}", header);
					archive_email_name = extractEmailFromToHeader(header);
					if (archive_email_name == null) {
						// breakout of inner for loop
						break;
					} else {
						//found qualified google group name
						break outerloop;
					}
				}
			}
			if(archive_email_name == null){
				archive_email_name = (String)((JSONObject)ja.get(0)).get("siteId");
				log.warn("No valid google group name found so substituting with siteId "+archive_email_name);
			}
			log.debug("eAEN: returning: {}",archive_email_name);
			return archive_email_name;
		}

		static public String extractEmailFromToHeader(String header) {
			String archive_email_name = null;
			try {
				header = header.split("To: ")[1].trim();
				String[] multipleEmailId = header.split(",");

				for (String emailId : multipleEmailId) {
					int i = emailId.indexOf('<');
					if (i != -1) {
						// To: <jennlove-test-project@ctqa.dsc.umich.edu>
						// To: "jennlove-test-project@ctqa.dsc.umich.edu" <jennlove-test-project@ctqa.dsc.umich.edu>
						// To: Email Archive Test Site <ctqa-email-archive-1@ctqa.dsc.umich.edu>
						emailId = emailId.substring(i + 1, emailId.indexOf('>'));
					}
					// To: ctqa-mbox@ctqa.dsc.umich.edu
					if (isCToolsDomainValid(emailId)) {
						archive_email_name = emailId.substring(0, emailId.indexOf('@'));
						break;
					}

				}
			} catch (Exception e) {
				log.error("Failure in getting the error google group id from " + header + "failed due to " + e.getMessage());
			}

			return archive_email_name;
		}

      // Eg.,emailId = ctqa-mbox@ctqa.dsc.umich.edu, Unit test are written in MigrationTaskServiceTest class
	public static boolean isCToolsDomainValid(String emailId) {
		String emailSignature = emailId.substring(emailId.indexOf('@')+1);
		String regex = "^ct[A-Za-z.]+.umich.edu$";
		if(emailSignature.matches(regex)){
			return true;
		}

		return false;
	}

	/**
		 * call GG microservice to get Group Groups settings for given site id
		 * @param siteId
		 * @param sessionId
		 * @return
		 */

		public ApiResultWrapper createGoogleGroupForSite(JSONObject googleGroupSettings) {

			GGBApiWrapper ggb = establishGGBConnection();

			String new_group_url = String.format("/groups/%s",googleGroupSettings.getString("email"));
			ApiResultWrapper arw = ggb.put_request(new_group_url,googleGroupSettings.toString());

			return  arw;

		}

		public GGBApiWrapper establishGGBConnection() {
			
			String server = env.getProperty(Utils.GGB_SERVER_NAME);
			String ggb_authinfo_username = env.getProperty(Utils.GGB_AUTHINFO_BASICAUTH_USERNAME);
			String ggb_authinfo_password = env.getProperty(Utils.GGB_AUTHINFO_BASICAUTH_PASSWORD);
			HashMap<String,String> basicAuthInfo = createBasicAuthInfo(ggb_authinfo_username,ggb_authinfo_password);			

			GGBApiWrapper ggb = new GGBApiWrapper(server,basicAuthInfo);
			return ggb;
		}
		
		HashMap<String,String> createBasicAuthInfo(String username,String password) {

			HashMap<String,String >authInfo = new HashMap<String,String>();
			authInfo.put(Utils.ENV_PROPERTY_USERNAME,username);
			authInfo.put(Utils.ENV_PROPERTY_PASSWORD,password);
			return authInfo;
		}

		// Change the ctools site information into Google group information.
		protected JSONObject getGoogleGroupSettings(String sessionId, String siteId) {
			// get group information for this site and update Google
			JSONObject group_info = getCToolsGroupInfoJson(sessionId,siteId);
			if(group_info ==null){
				log.error("Changing ctools site info into Google group has errors for SiteId: "+siteId);
				return null;
			}
			log.debug(String.format("migration: group info: [%s]",group_info.toString()));

			JSONObject googleGroupSettings = createGoogleGroupJson(group_info);
			return googleGroupSettings;
		}

		// create the json Google expects to create a group
		protected JSONObject createGoogleGroupJson(JSONObject group_info) {
			log.warn("createGoogleGroupJson: HACK, GROUP INFO IS NOT YET CORRECT. E.G. DESCRIPTION IS WRONG.");

			log.debug("createGoogleGroupJson: {}",group_info.toString());

			JSONObject googleGroupJson = new JSONObject();
			googleGroupJson.put("email", group_info.get("group_email"));
			googleGroupJson.put("name", group_info.get("title"));
			googleGroupJson.put("description", formatDescription((String)group_info.get("description")));
			log.debug("ggbjson: "+googleGroupJson.toString());

			return googleGroupJson;
		}

		// Make description fit Google standards.
		protected String formatDescription(String description) {
			final int MAX_LENGTH=300;
			String formatted_string = null;
			formatted_string = description.substring(0, Math.min(description.length(), MAX_LENGTH));
			// override description until we know correct format.
			formatted_string = "DUMMY DESCRIPTION FROM formatDescription";
			return formatted_string;
		}


		// map the CTools role to the Google role
		public String findGoogleRole(String role) {
			String goole_role = MemberRoleMap.get(role);
			return (goole_role != null) ? goole_role : MemberRoleMap.get("DEFAULT");
		}

		// change members list to one suitable for inserting into google.
		public List<List<String>> memberPropertiesList(HashMap<String,String> members,String defaultEmailSuffix) {
			log.debug("members: {}",members);

			List<List<String>> edited_members = new ArrayList<List<String>>();
			for (Map.Entry<String, String> entry : members.entrySet()) {
				String user = entry.getKey();
				String role = entry.getValue();
				log.debug("members: member: {} role: {}",user,role);
				List<String> pair = new ArrayList<String>();
				if (user.indexOf("@") == -1) {
					user = user + "@"+defaultEmailSuffix;
				}
				pair.add(user);
				pair.add(findGoogleRole(role));
				edited_members.add(pair);
			}
			log.debug("edited_members: {}",edited_members);
			return edited_members;
		}


		public List<StatusReport> updateGoogleGroupMembershipFromSite(String siteId,HashMap<String, String> members, String groupId) {

			log.debug("process members for site: "+siteId);
			List<List<String>> membersProperties = memberPropertiesList(members,Utils.DEFAULT_EMAIL_MEMBER_SUFFIX);
			log.debug("found members for site: "+siteId+" "+membersProperties);
			List<StatusReport> membershipsStatus = addMembersToGroup(groupId, membersProperties);
			return membershipsStatus;
		}

		List<StatusReport> addMembersToGroup(String group_id,List<List<String>> membersProperties) {
			List<StatusReport> memberships = new ArrayList<StatusReport>();
			for (List<String> user : membersProperties) {
			    StatusReport memberStatus = new StatusReport();
				log.debug("group: {} user: {} role: {}",group_id,user.get(0),user.get(1));
				ApiResultWrapper arw = addMemberToGroup(group_id, user.get(0), user.get(1));
				int statusCode = arw.getStatus();
				memberStatus.setStatus(Utils.REPORT_STATUS_OK);
				if(statusCode/100 !=2 && statusCode != HttpStatus.CONFLICT.value()){
					memberStatus.setStatus(Utils.REPORT_STATUS_ERROR);
				}
				memberStatus.setMsg(arw.getMessage());
				memberStatus.setId(user.get(0)+" "+user.get(1));
				memberships.add(memberStatus);


			}

			return memberships;
		}


		ApiResultWrapper addMemberToGroup(String group_id, String member_email, String member_role) {

			GGBApiWrapper ggb = establishGGBConnection();

			String new_member_url = String.format("/groups/%s/members/%s",group_id,member_email);

			JSONObject jo = new JSONObject();
			jo.put("email",member_email);
			jo.put("role",member_role);
			ApiResultWrapper result = ggb.put_request(new_member_url,jo.toString());
			return result;
		}

		/**
		 * migrate email content to Group Group using microservice
		 * @param message
		 * @return
		 */
		@Async
		protected Future<String> uploadMessageToGoogleGroup(MigrationEmailMessage message) {

			String googleGroupId = message.getGoogle_group_id();

			String messageId = message.getMessage_id();
			mRepository.setMigrationMessageStartTime(message.getMessage_id(), new Timestamp(System.currentTimeMillis()));
			log.info("begin to upload message " + messageId  + " to Google Group id = " + googleGroupId);

			// use EmailFormatter to get RFC822 complaint email content
			String emailContent = message.getJson();
			HttpServletRequest request = null;
			AttachmentHandler attachmentHandler = new AttachmentHandler(request);
			attachmentHandler.setEnv(env);

			String emailText;
			JSONObject statusObj=new JSONObject();
			statusObj.put(Utils.REPORT_ATTR_ITEM_ID, messageId);

			// Try only once and record the resulting status in the database.
			try
			{
				EmailFormatter formatter = new EmailFormatter(emailContent, attachmentHandler);
				formatter.setEnv(env);
				MailResultPair emailTextPlusStatus = formatter.rfc822Format();
				emailText = emailTextPlusStatus.getMessage();
				statusObj = emailTextPlusStatus.getReport().getJsonReportObject();

				if (emailText != null) {
					// mark the file as being processed

					// process the message
					ApiResultWrapper arw = addEmailToGoogleGroup(googleGroupId, emailText);
					int statusCode = arw.getStatus();
					// taking the success msg
					String ggbResult = arw.getResult();
					// Taking the error message
					String ggbMsg = arw.getMessage();
					log.debug("uploadMessageToGoogleGroup: status: {} googleGroupId: {}", statusCode, googleGroupId);

					if (statusCode / 100 != 2 && statusCode != 409)  {
						statusObj= errorHandlingWhenNotSuccess(statusObj, statusCode, ggbMsg,ggbResult);
						log.error(String.format("Failure in migrating message with MessageId: \"%1$s\" to google groups" +
								", status code %2$d and error message %3$s", messageId, statusCode, ggbMsg));
						return new AsyncResult<String>(statusObj.toString());
					}

					// Upload to Google groups went fine
					String messageStatus = (String) statusObj.get(Utils.REPORT_ATTR_ITEM_STATUS);
					String messageStr = (String) statusObj.get(Utils.REPORT_ATTR_MESSAGE);
					//This is the case when in the EmailFormatter attachment might have dropped due to some error or size limit
					if (messageStatus == Utils.REPORT_STATUS_PARTIAL) {
						statusObj.put(Utils.REPORT_ATTR_MESSAGE, "Google Groups message migration successful, but " + messageStr);
					}
					log.info("The response from google groups when 200: "+ggbResult+" for MessageId: "+messageId);
				}

			}catch (IOException exception) {
				String errorString = "IOException from EmailFormatter for message id " + messageId + " " + exception.getMessage();
				log.error(errorString);
				statusObj=errHandlingWhenExceptions(statusObj);
			}catch (ParseException e){
				String errorString = "ParseException while extracting response from GGB with message id " + messageId + " " + e.getMessage();
				log.error(errorString);
				statusObj=errHandlingWhenExceptions(statusObj);
			}catch (Exception e) {
				String msg = String.format("unexpected exception in uploadMessageToGoogleGroup: %s for messageId: %s",
						e.getMessage(),messageId);
				log.error(msg);
				statusObj=errHandlingWhenExceptions(statusObj);
			}

			finally {
				// update the status and end time for file item
				mRepository.setMigrationMessageEndTime(messageId, new java.sql.Timestamp(System.currentTimeMillis()));
				mRepository.setMigrationMessageStatus(messageId, statusObj.toString());
			}
			log.debug("uploadMessageToGoogleGroup: return");
			return new AsyncResult<String>(statusObj.toString());
		}

	private JSONObject errHandlingWhenExceptions(JSONObject statusObj) {
		statusObj.put(Utils.REPORT_ATTR_ITEM_STATUS, Utils.REPORT_STATUS_ERROR);
		statusObj.put(Utils.REPORT_ATTR_MESSAGE, "Failure to migrate message to Google Groups");
		return statusObj;
	}

	private JSONObject errorHandlingWhenNotSuccess(JSONObject statusObj, int statusCode, String errMsg, String googleResults) {
		String msg=googleResults;
		statusObj.put(Utils.REPORT_ATTR_ITEM_STATUS, Utils.REPORT_STATUS_ERROR);
		if(statusCode == ApiResultWrapper.API_EXCEPTION_ERROR || statusCode == ApiResultWrapper.API_UNKNOWN_ERROR) {
			msg=truncateMessage(errMsg);
		}
		String reportMsg= "Failure to migrate message to Google Groups due to " +msg+", failed with status code "+statusCode;
		statusObj.put(Utils.REPORT_ATTR_MESSAGE, reportMsg);
		return statusObj;

	}

	private String truncateMessage(String msg) {
		String msgChunk = "message:";
		int i = msg.indexOf(msgChunk);
		if (i == -1) {
			return "unknown reason";
		}
		return msg.substring(i + 8);
	}

}
