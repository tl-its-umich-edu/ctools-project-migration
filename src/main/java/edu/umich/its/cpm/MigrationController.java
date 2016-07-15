package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.http.client.*;
import org.apache.http.protocol.HttpCoreContext;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.NameValuePair;
import org.apache.http.HttpResponse;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.util.EntityUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Callable;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.inject.Inject;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Date;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.concurrent.Future;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.context.annotation.PropertySource;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.util.concurrent.ListenableFutureCallback;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartException;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxItem;
import com.box.sdk.BoxItem.Info;
import com.box.sdk.BoxUser;
import com.box.sdk.Metadata;
import com.box.sdk.ProgressListener;
import com.google.gson.Gson;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@PropertySource("file:${catalina.base:/usr/local/ctools/app/ctools/tl}/home/application.properties")
@RestController
public class MigrationController {
	
	private static final String JSON_ATTR_SITE_COLLECTION = "site_collection";

	private static final String BOX_AUTHORIZED_HTML = "<link rel=\"stylesheet\" href=\"vendors/bootstrap/bootstrap.min.css\"><div class=\"jumbotron\" style=\"background:#fff\"><h1 role=\"alert\">Authorized</h1><p>You can now select a Box folder and migrate project sites to it.</p><p><a onclick=\"window.parent.closeBoxAuthModal()\" href=\"#\">Close</a></p></div>";
	private static final String BOX_UNAUTHORIZED_HTML = "<link rel=\"stylesheet\" href=\"vendors/bootstrap/bootstrap.min.css\"><div class=\"jumbotron\"><h1 role=\"alert\">Unauthorized!</h1><p>You need to authorize Box.<a target=\"_top\" href=\"/\">Go back</a></p></div>";

	private static final Logger log = LoggerFactory
			.getLogger(MigrationController.class);

	@Autowired
	MigrationRepository repository;
	
	@Autowired
	BoxAuthUserRepository uRepository;
	
	@Autowired
	SiteDeleteChoiceRepository cRepository;
	
	@Autowired
	SiteToolExemptRepository tRepository;

	@Autowired
	private Environment env;

	@Autowired
	private MigrationInstanceService migrationInstanceService;

	/**
	 * get all CTools sites where user have site.upd permission
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/projects")
	public void getProjectSites(HttpServletRequest request,
			HttpServletResponse response) {

		// get non-course sites that user have site.upd permission
		HashMap<String, String> projectsMap = getUserAllSitesMap(request);

		// JSON response
		JSON_response(response, projectsMap.get("projectsString"),
				projectsMap.get("errorMessage"), projectsMap.get("requestUrl"));
	}

	/**
	 * return HashMap object with non-course sites that user have site.upd
	 * permission
	 * 
	 * @param request
	 * @return
	 */
	private HashMap<String, String> getUserAllSitesMap(
			HttpServletRequest request) {
		HashMap<String, String> projectsMap = get_user_sites(request);

		// this is a json value contains non-MyWorkspace sites
		String sitesJson = projectsMap.get("projectsString");
		// get the json value for user MyWorkspace site
		String myworkspaceJson = get_user_myworkspace_site_json(request);
		if (myworkspaceJson != null) {
			// insert the MyWorkspace json into the other-sites json
			try {
				JSONObject sitesJSONObject = new JSONObject(sitesJson);
				sitesJSONObject.append(JSON_ATTR_SITE_COLLECTION, new JSONObject(
						myworkspaceJson));
				// get the updated sites json string with MyWorkspace info
				// inserted
				projectsMap.put("projectsString", sitesJSONObject.toString());
			} catch (JSONException e) {
				log.error(this + " error parsing sites JSON value " + sitesJson);
			}
		}
		return projectsMap;
	}

	/**
	 * REST API call to get user MyWorkspace site
	 * 
	 * @param req
	 * @return
	 */
	private String get_user_myworkspace_site_json(HttpServletRequest request) {
		String siteJson = null;

		String requestUrl = "";

		// login to CTools and get sessionId
		String userEid = Utils.getCurrentUserId(request, env);
		HashMap<String, Object> sessionAttributes = Utils.login_becomeuser(env,
				request, userEid);
		if (sessionAttributes.containsKey(Utils.SESSION_ID)) {
			String sessionId = (String) sessionAttributes.get(Utils.SESSION_ID);

			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/<siteId>.json?_sessionId=<sessionId>"
			// Response Code Details: 200 plus data; 404 if not found, 406 if
			// format unavailable
			// user MyWorkspace site ID could be two forms
			// 1. try "~<user_id>" first
			requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL) + "direct/site/~"
					+ userEid + ".json?_sessionId=" + sessionId;
			log.info(this + " get_user_myworkspace_site_json " + requestUrl);
			try {
				ResponseEntity<String> siteEntity = restTemplate.getForEntity(
						requestUrl, String.class);
				if (siteEntity.getStatusCode().is2xxSuccessful()) {
					// find user MyWorkspace site
					siteJson = siteEntity.getBody();
				}
			} catch (RestClientException e) {
				log.error(this + requestUrl + e.getMessage());

				try {
					// 2. site not found, try "~<eid>" next
					// get the user id first
					// "https://server/direct/user/<eid>.json?_sessionId=<sessionId>"
					// Response Code Details: 200 plus data; 404 if not found,
					// 406 if format unavailable
					requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
							+ "direct/user/" + userEid + ".json?_sessionId="
							+ sessionId;
					log.info(this + " get_user_myworkspace_site_json "
							+ requestUrl);
					ResponseEntity<String> userEntity = restTemplate
							.getForEntity(requestUrl, String.class);
					if (userEntity.getStatusCode().is2xxSuccessful()) {
						JSONObject userObject = new JSONObject(
								userEntity.getBody());
						String userId = (String) userObject.get("id");
						// use this userId to form user myworkspace id
						requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
								+ "direct/site/~" + userId
								+ ".json?_sessionId=" + sessionId;
						log.info(this + " get_user_myworkspace_site_json "
								+ requestUrl);
						ResponseEntity<String> siteEntity = restTemplate
								.getForEntity(requestUrl, String.class);
						if (siteEntity.getStatusCode().is2xxSuccessful()) {
							// now we find the user MyWorkspace site
							siteJson = siteEntity.getBody();
						}
					}
				} catch (RestClientException e2) {
					log.error(this + requestUrl + e2.getMessage());
				}
			}

		}

		return siteJson;
	}

	/**
	 * REST API call to get all sites for user
	 * 
	 * @param req
	 * @return
	 */
	private HashMap<String, String> get_user_sites(HttpServletRequest request) {
		HashMap<String, String> rv = new HashMap<String, String>();

		String projectsString = "";
		String errorMessage = "";
		String requestUrl = "";

		String currentUserId = Utils.getCurrentUserId(request, env);
		
		// login to CTools and get sessionId
		HashMap<String, Object> sessionAttributes = Utils.login_becomeuser(env,
				request, currentUserId);
		if (sessionAttributes.containsKey(Utils.SESSION_ID)) {
			String sessionId = (String) sessionAttributes.get(Utils.SESSION_ID);

			// get all sites that user have permission site.upd
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/withPerm/.json?permission=site.upd"
			requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
					+ "direct/site/withPerm/.json?permission=site.upd&_sessionId="
					+ sessionId;
			log.info(this + " get_user_sites " + requestUrl);
			try {
				projectsString = restTemplate.getForObject(requestUrl,
						String.class);
				
				// update the projectString by filtering based on site Owner role
				projectsString = filterForSitesWithOwnerRole(request,
						projectsString, currentUserId);
			} catch (RestClientException e) {
				errorMessage = e.getMessage();
				log.error(requestUrl + errorMessage);
			}

		}

		rv.put("projectsString", projectsString);
		rv.put("errorMessage", errorMessage);
		rv.put("requestUrl", requestUrl);
		return rv;
	}

	/**
	 * retain only the site record where the current user has Owner role inside
	 * @param request
	 * @param projectsString
	 * @param currentUserId
	 * @return
	 */
	private String filterForSitesWithOwnerRole(HttpServletRequest request,
			String projectsString, String currentUserId) {
		JSONArray ownerSitesJSONArray = new JSONArray();
		try {
			JSONObject sitesJSONObject = new JSONObject(projectsString);
			// get site array
			JSONArray sitesJSONArray = sitesJSONObject.getJSONArray(JSON_ATTR_SITE_COLLECTION);
			
			// filter out those sites that current user has role "Owner" in it
			for (int iSite = 0; sitesJSONArray != null && iSite < sitesJSONArray.length(); ++iSite) {
				JSONObject siteJSON = sitesJSONArray.getJSONObject(iSite);
				String siteId = siteJSON.getString("id");
				try
				{
					// get all site members
					HashMap<String, String> userRoles = get_site_members(
							request, siteId);
					for (String userEid : userRoles.keySet()) {
						String userRole = userRoles.get(userEid);
						if (Utils.ROLE_OWNER.equals(userRole) && userEid.equals(currentUserId))
						{
							// keep the site JSON if current user has Owner role in this site
							ownerSitesJSONArray.put(siteJSON);
							break;
						}
					}
				}
				catch (RestClientException e)
				{
					// sometimes there is problem getting site member roles
					// in this case, we will add the site json
					// and document the exception
					ownerSitesJSONArray.put(siteJSON);
					log.error("Exception getting membership for site " + siteId + " " + e.getMessage());
				}
				catch (JSONException e)
				{
					// sometimes there is problem getting site member roles
					// in this case, we will add the site json
					// and document the exception
					ownerSitesJSONArray.put(siteJSON);
					log.error("Exception getting membership for site " + siteId + " " + e.getMessage());
				}
			}
			sitesJSONObject.put(JSON_ATTR_SITE_COLLECTION, ownerSitesJSONArray);
			projectsString = sitesJSONObject.toString();
		} catch (JSONException e) {
			log.error(this + " error parsing sites JSON value " + projectsString);
		}
		return projectsString;
	}

	/**
	 * get page information
	 * 
	 * @param site_id
	 * @return
	 */
	@GET
	@RequestMapping("/projects/{site_id}")
	public void getProjectSitePages(@PathVariable String site_id,
			HttpServletRequest request, HttpServletResponse response) {
		HashMap<String, String> pagesMap = get_user_project_site_tools(request,
				site_id);
		JSON_response(response, pagesMap.get("pagesString"),
				pagesMap.get("errorMessage"), pagesMap.get("requestUrl"));
	}

	/**
	 * REST API call to get CTools site pages and tools
	 * 
	 * @param site_id
	 * @return
	 */
	private HashMap<String, String> get_user_project_site_tools(
			HttpServletRequest request, String site_id) {
		HashMap<String, String> rv = new HashMap<String, String>();

		String pagesString = "";
		String errorMessage = "";
		String requestUrl = "";

		// login to CTools and get sessionId
		HashMap<String, Object> sessionAttributes = Utils.login_becomeuser(env,
				request, Utils.getCurrentUserId(request, env));
		if (sessionAttributes.containsKey(Utils.SESSION_ID)) {
			String sessionId = (String) sessionAttributes.get(Utils.SESSION_ID);

			// get all pages inside site
			// the url should be in the format of
			// "https://server/direct/site/SITE_ID/pages.json"
			RestTemplate restTemplate = new RestTemplate();
			requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL) + "direct/site/"
					+ site_id + "/pages.json?_sessionId=" + sessionId;

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
			else
			{
				pagesString = siteHasContentItems(site_id, pagesString, sessionId);
			}
		}

		rv.put("pagesString", pagesString);
		rv.put("errorMessage", errorMessage);
		rv.put("requestUrl", requestUrl);
		return rv;
	}

	/**
	 * API call to get CTools site membership
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/siteMembership/{siteId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSiteMembershipAPI(
			@PathVariable("siteId") String siteId,
			HttpServletRequest request) {
		try {

			// return CTools site members
			return Response.status(Response.Status.OK).entity(get_site_members(request, siteId))
					.build();
		} catch (Exception e) {
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot get CTools site members for site "
							+ siteId + ": " + e.getMessage()).build();
		}
	}
	
	/**
	 * REST API call to get CTools site members
	 * 
	 * @param request
	 * @param site_id
	 * @return
	 */
	private HashMap<String, String> get_site_members(
			HttpServletRequest request, String site_id)
			throws RestClientException, JSONException {
		HashMap<String, String> rv = new HashMap<String, String>();

		String membersString = "";
		String errorMessage = "";
		String requestUrl = "";

		// login to CTools and get sessionId
		HashMap<String, Object> sessionAttributes = Utils.login_becomeuser(env,
				request, Utils.getCurrentUserId(request, env));
		if (!sessionAttributes.containsKey(Utils.SESSION_ID)) {
			// exit if login_becomeuser call is not successful
			return rv;
		}

		String sessionId = (String) sessionAttributes.get(Utils.SESSION_ID);

		// get all members inside site
		// the url should be in the format of
		// "https://server/direct/membership/site/SITE_ID.json"
		RestTemplate restTemplate = new RestTemplate();
		requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
				+ "direct/membership/site/" + site_id + ".json?_sessionId="
				+ sessionId;
		try {
			membersString = restTemplate.getForObject(requestUrl, String.class);
		} catch (RestClientException e) {
			errorMessage = "Cannot find site members by siteId: " + site_id
					+ " " + e.getMessage();
			log.error(errorMessage);
			throw e;
		}

		if (membersString != null) {
			try
			{
				JSONObject sJSON = new JSONObject(membersString);
				JSONArray members = (JSONArray) sJSON.get("membership_collection");
				// iterate through all members
				for (int iMember = 0; members != null && iMember < members.length(); ++iMember) {
					JSONObject member = members.getJSONObject(iMember);
					String userEid = member.getString("userEid");
					String userRole = member.getString("memberRole");
					boolean isActive = member.getBoolean("active");
					if (isActive) {
						rv.put(userEid, userRole);
					}
				}
			}
			catch (JSONException e)
			{
				log.warn("error parsing member string:" + membersString + " for site " + site_id);
				throw e;
			}
		}

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
						String resourceToolRequestUrl = env
								.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
								+ "direct/content/site/"
								+ site_id
								+ ".json?_sessionId=" + sessionId;

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
								page.put("hasContentItem", resourceList != null
										&& resourceList.length() > 1);
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
	 * get all migration records
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/migrations")
	@Produces(MediaType.APPLICATION_JSON)
	public Response migrations(HttpServletRequest request) {
		String userId = Utils.getCurrentUserId(request, env);
		try {
			return Response.status(Response.Status.OK)
					.entity(repository.findMigrations(userId)).build();
		} catch (Exception e) {
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot get migration records for user " + userId
							+ ": " + e.getMessage()).build();
		}
	}

	/**
	 * get a specific migration record
	 * 
	 * @param migration_id
	 * @return
	 */
	@GET
	@RequestMapping("/migrations/{migration_id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response migrations(
			@PathVariable("migration_id") String migration_id,
			HttpServletRequest request) {
		Object o = repository.findOne(migration_id);
		String rv = "";
		if (o == null) {
			// no such migration record
			throw new MigrationNotFoundException(
					"no matching record for /migrations/" + migration_id);
		} else {
			String userId = Utils.getCurrentUserId(request, env);
			String migratedBy = ((Migration) o).getMigrated_by();
			if (!migratedBy.equals(userId)) {
				// different user started the migration
				throw new MigrationNotFoundException("record for /migrations/"
						+ migration_id + " was done by user id=" + migratedBy
						+ " , instead of current user " + userId);
			} else {
				// find migration record with id
				return Response.status(Response.Status.OK)
						.entity((Migration) o).build();
			}

		}
	}

	/**
	 * found all migrated records (where the migration record have "end_time"
	 * field value
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/migrated")
	@Produces(MediaType.APPLICATION_JSON)
	public Response migrated(HttpServletRequest request) {
		String userId = Utils.getCurrentUserId(request, env);
		try {
			List<Migration> l = repository.findMigrated(userId);
			return Response.status(Response.Status.OK)
					.entity(repository.findMigrated(userId)).build();
		} catch (Exception e) {
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot get migrated records for user " + userId
							+ ": " + e.getMessage()).build();
		}
	}

	/**
	 * found all migrating records (where the migration record have NO
	 * "end_time" field) value
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/migrating")
	@Produces(MediaType.APPLICATION_JSON)
	public Response migrating(HttpServletRequest request) {
		String userId = Utils.getCurrentUserId(request, env);
		try {
			return Response.status(Response.Status.OK)
					.entity(repository.findMigrating(userId)).build();
		} catch (Exception e) {
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot get migrating records for user " + userId
							+ ": " + e.getMessage()).build();
		}
	}

	/**
	 * insert a new record of Migration
	 * 
	 * @param request
	 */

	@GET
	@Produces("application/zip")
	@RequestMapping(value = "/migrationZip")
	@ResponseBody
	public void migrationZip(HttpServletRequest request,
			HttpServletResponse response) {

		// zip download
		HashMap<String, String> callStatus = migration_call(request, response,
				Utils.MIGRATION_TYPE_ZIP, Utils.getCurrentUserId(request, env));
		if (callStatus.containsKey("errorMessage")) {
			log.info(this + " MigrationZip call error message="
					+ callStatus.get("errorMessage"));
		} else if (callStatus.containsKey("migrationId")) {
			log.info(this + " MigrationZip call migration started id="
					+ callStatus.get("migrationId"));
		}
	}

	/**
	 * handle migration request
	 * 
	 * @param
	 */
	private HashMap<String, String> migration_call(HttpServletRequest request,
			HttpServletResponse response, String target, String remoteUser) {
		HashMap<String, String> rv = new HashMap<String, String>();

		// we need to do series checks to make sure the migration request is
		// valid
		// 1. check if missing site_id or tool_id attribute
		Map<String, String[]> parameterMap = request.getParameterMap();
		String siteId = parameterMap.get("site_id")[0];
		String toolId = parameterMap.get("tool_id")[0];
		if (siteId == null || siteId.isEmpty() || toolId == null
				|| toolId.isEmpty()) {
			rv.put("errorMessage",
					"Migration request missing required parameter: site_id, or tool_id");
			return rv;
		}

		log.info("request migration for site " + siteId + " and tool " + toolId);

		// 2. check to see whether the site_id and tool_id is valid and
		// associated with current user
		HashMap<String, String> projectsMap = getUserAllSitesMap(request);
		String projectsString = projectsMap.get("projectsString");
		if (projectsString.indexOf(siteId) == -1) {
			rv.put("errorMessage", "Invalid site id = " + siteId + " for user "
					+ remoteUser);
			return rv;
		} else {
			HashMap<String, String> pagesMap = get_user_project_site_tools(
					request, siteId);
			String pagesString = pagesMap.get("pagesString");
			if (pagesString.indexOf(toolId) == -1) {
				rv.put("errorMessage", "Invalid tool id = " + toolId
						+ " for site id= " + siteId + " for user " + remoteUser);
				return rv;
			}
		}

		// 3. check if there is an ongoing migration for the same site and tool
		boolean valid_migration_request = true;
		List<Migration> migratingSiteTools = repository
				.findMigrating(remoteUser);
		for (Migration m : migratingSiteTools) {
			log.info("migrating reord " + m.getSite_id() + " tool id=" + toolId
					+ "user id=" + remoteUser);
			if (siteId.equals(m.getSite_id()) && toolId.equals(m.getTool_id())) {
				// still on-going migration
				valid_migration_request = false;
				break;
			}

		}
		// exit if it is duplicate request
		if (!valid_migration_request) {
			rv.put("errorMessage", "Duplicate migration request for site "
					+ siteId + " tool=" + toolId + " user id=" + remoteUser);
			return rv;
		}

		// now after all checks passed, we are ready for migration
		// save migration record into database
		HashMap<String, Object> saveMigration = saveMigrationRecord(request);

		rv = createMigrationTask(
				request,
				response,
				target,
				remoteUser,
				rv,
				parameterMap.get("box_folder_id") != null ? parameterMap
						.get("box_folder_id")[0] : null, siteId, toolId,
				saveMigration);
		return rv;
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
	private HashMap<String, String> createMigrationTask(
			HttpServletRequest request, HttpServletResponse response,
			String target, String remoteUser, HashMap<String, String> rv,
			String boxFolderId, String siteId, String toolId,
			HashMap<String, Object> saveMigration) {
		// exit if there is no new Migration record saved into DB
		if (!saveMigration.containsKey("migration")) {
			// no new Migration record created
			rv.put("errorMessage", "Cannot create migration records for user "
					+ remoteUser + " and site=" + siteId);
			return rv;
		} else {

			Migration migration = (Migration) saveMigration.get("migration");
			String migrationId = migration.getMigration_id();
			try {
				HashMap<String, Object> sessionAttributes = Utils
						.login_becomeuser(env, request, remoteUser);

				// call asynchronous method for zip file download
				String migrationStatus = null;
				if (Utils.MIGRATION_TYPE_ZIP.equals(target)) {
					// call asynchronous method for zip file download
					log.info("start to call zip migration asynch for siteId="
							+ siteId + " tooId=" + toolId);
					migrationInstanceService.createDownloadZipInstance(env,
							request, response, remoteUser, sessionAttributes,
							siteId, migrationId, repository);
				} else if (Utils.MIGRATION_TYPE_BOX.equals(target)) {
					// call asynchronous method for Box file upload
					log.info("start to call Box migration asynch for siteId="
							+ siteId + " tooId=" + toolId);
					migrationInstanceService.createUploadBoxInstance(env,
							request, response, remoteUser, sessionAttributes,
							siteId, boxFolderId, migrationId, repository);
				}
			} catch (java.lang.InterruptedException e) {
				log.error(e.getMessage() + " migration error for user "
						+ remoteUser + " and site=" + siteId + " target="
						+ target);
			}
			rv.put("migrationId", migrationId);
			return rv;
		}
	}

	/**
	 * generate output for JSON_ready input value
	 */
	private void JSON_response(HttpServletResponse response, String jsonValue,
			String errorMessage, String requestUrl) {
		try {
			// output json
			response.setContentType(MediaType.APPLICATION_JSON);
			if (jsonValue == null) {
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				response.getWriter().write(errorMessage);
			} else {
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().write(jsonValue);
			}
			response.getWriter().close();
		} catch (IOException e) {
			log.error(requestUrl + e.getMessage());
		}
	}

	/************* Box integration *****************/
	/**
	 * get json string of box folders
	 * 
	 * @return
	 */
	@RequestMapping("/box/folders")
	public List<HashMap<String, String>> handleGetBoxFolders(
			HttpServletRequest request, HttpServletResponse response) {

		// get the current user id
		String userId = Utils.getCurrentUserId(request, env);

		String boxClientId = BoxUtils.getBoxClientId(request, env);
		String boxClientSecret = BoxUtils.getBoxClientSecret(request, env);
		String remoteUserEmail = Utils.getCurrentUserEmail(request, env);

		String boxAPIUrl = env.getProperty(Utils.BOX_API_URL);
		String boxClientRedirectUrl = BoxUtils.getBoxClientRedirectUrl(request,
				env);

		// need to have all Box app configurations
		if (boxClientId == null || boxClientSecret == null
				|| boxClientRedirectUrl == null) {
			log.error("Missing box integration parameters");
			return null;
		}

		if (uRepository.findBoxAuthUserAccessToken(remoteUserEmail) == null) {
			// go to Box authentication screen
			// get access token and refresh token and store locally
			BoxUtils.authenticate(boxAPIUrl, boxClientId, boxClientRedirectUrl,
					remoteUserEmail, response, uRepository);
		} else {
			// get box folders json
			return BoxUtils.getBoxFolders(userId, boxClientId, boxClientSecret, uRepository);
		}
		return null;
	}

	/**
	 * User authenticates into the Box account
	 * 
	 * @return
	 */
	@RequestMapping("/box/authorize")
	public String boxAuthenticate(HttpServletRequest request,
			HttpServletResponse response) {
		// normal user authorize to Box
		return boxAuthorization(request, response);
	}

	/**
	 * get json string of box folders
	 * 
	 * @return
	 */
	@RequestMapping("/box/unauthorize")
	public Response unauthorizeBox(HttpServletRequest request,
			HttpServletResponse response) {

		// get the current user email
		String userEmail = Utils.getCurrentUserEmail(request, env);

		// the return string
		String rv = "";

		// check whether the user authentication token is store in memory
		if (uRepository.findBoxAuthUserAccessToken(Utils.getCurrentUserEmail(request, env)) == null) {
			rv = "Cannot find user's Box authentication info. ";
		} else {
			uRepository.deleteBoxAuthUserAccessToken(userEmail);
			uRepository.deleteBoxAuthUserRefreshToken(userEmail);
			rv = "User authentication info is removed. ";
		}

		log.info("/box/unauthorize for user " + userEmail + " " + rv);
		try {
			return Response.status(Response.Status.OK).entity(rv).build();
		} catch (Exception e) {
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot remove box authentication info for user "
							+ userEmail + ": " + e.getMessage()).build();
		}
	}

	@RequestMapping("/authorized")
	@Produces(MediaType.APPLICATION_JSON)
	public String getBoxAuthzTokens(HttpServletRequest request) {
		String userEmail = Utils.getCurrentUserEmail(request, env);
		String rv = uRepository.findBoxAuthUserAccessToken(userEmail);

		if (rv == null) {
			// get the authCode,
			// and get access token and refresh token subsequently
			String boxTokenUrl = env.getProperty(Utils.BOX_TOKEN_URL);

			String boxClientId = BoxUtils.getBoxClientId(request, env);
			String boxClientSecret = BoxUtils.getBoxClientSecret(request, env);
			BoxUtils.getAuthCodeFromBoxCallback(request, boxClientId,
					boxClientSecret, boxTokenUrl, uRepository);

			// try to get the access token after parsing the request string
			rv = uRepository.findBoxAuthUserAccessToken(userEmail);
		}

		return rv != null ? BOX_AUTHORIZED_HTML : BOX_UNAUTHORIZED_HTML;
	}

	@RequestMapping("/box/checkAuthorized")
	@Produces(MediaType.APPLICATION_JSON)
	public Boolean boxCheckAuthorized(HttpServletRequest request) {
		return Boolean.valueOf(uRepository.findBoxAuthUserAccessToken(Utils.getCurrentUserEmail(request, env)) != null);
	}

	/**
	 * Save Migration record to DB
	 * 
	 * @return HasMap key="status", value=status message; key="migration",
	 *         value=MigrationObject
	 */
	private HashMap<String, Object> saveMigrationRecord(
			HttpServletRequest request) {
		// the return hashmap provide newly created Migration object, and status
		// message
		HashMap<String, Object> rv = new HashMap<String, Object>();

		// status message
		StringBuffer status = new StringBuffer();

		// get parameters
		Map<String, String[]> parameterMap = request.getParameterMap();
		String siteId = parameterMap.get("site_id")[0];
		String siteName = parameterMap.get("site_name")[0];
		String toolId = parameterMap.get("tool_id")[0];
		String toolName = parameterMap.get("tool_name")[0];
		String destinationType = parameterMap.get("destination_type")[0];
		String userId = Utils.getCurrentUserId(request, env);
		String targetUrl = "";
		if (Utils.MIGRATION_TYPE_BOX.equalsIgnoreCase(destinationType)) {
			// the format of folder path in box
			// e.g. https://umich.app.box.com/files/0/f/<folderId>/<folderName>
			String boxFolderId = parameterMap.get("box_folder_id")[0];
			String boxFolderName = parameterMap.get("box_folder_name")[0];
			log.info("boxFolderName=" + boxFolderName);
			if (boxFolderId != null && !boxFolderId.isEmpty()
					&& boxFolderName != null && !boxFolderName.isEmpty()) {
				targetUrl = Utils.BOX_FILE_PATH_URL + boxFolderId
						+ Utils.PATH_SEPARATOR + boxFolderName;
			}
		}
		
		// include the current user as site owner first
		StringBuffer allSiteOwners = new StringBuffer(Utils.getUserEmailFromUserId(userId));
		try
		{
			// add site members to migration
			HashMap<String, String> userRoles = get_site_members(
					request, siteId);
			for (String userEid : userRoles.keySet()) {
				String userRole = userRoles.get(userEid);
				String userEmail = Utils.getUserEmailFromUserId(userEid);
				
				if (Utils.ROLE_OWNER.equals(userRole))
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
			log.error("Problem retrieving site members for site id = " + siteId + " " + e.getStackTrace());
		}

		// assign null values to batch id and name
		// create new migration record
		rv = newMigrationRecord(status, null, null, siteId, siteName, toolId,
				toolName, destinationType, allSiteOwners.toString(), targetUrl);

		return rv;
	}

	/**
	 * Save Migration record to DB
	 * 
	 * @return HasMap key="status", value=status message; key="migration",
	 *         value=MigrationObject
	 */
	private HashMap<String, Object> saveBulkMigrationRecord(
			String bulkMigrationId, String bulkMigrationName, String siteId,
			String siteName, String toolId, String toolName,
			String boxFolderId, String boxFolderName, String userId) {
		// the return hashmap provide newly created Migration object, and status
		// message
		HashMap<String, Object> rv = new HashMap<String, Object>();

		// status message
		StringBuffer status = new StringBuffer();

		String destinationType = Utils.MIGRATION_TYPE_BOX;
		String targetUrl = "";
		if (Utils.MIGRATION_TYPE_BOX.equalsIgnoreCase(destinationType)) {
			// the format of folder path in box
			// e.g. https://umich.app.box.com/files/0/f/<folderId>/<folderName>
			log.info("boxFolderName=" + boxFolderName);
			if (boxFolderId != null && !boxFolderId.isEmpty()
					&& boxFolderName != null && !boxFolderName.isEmpty()) {
				targetUrl = Utils.BOX_FILE_PATH_URL + boxFolderId
						+ Utils.PATH_SEPARATOR + boxFolderName;
			}
		}

		// assign null values to batch id and name
		// create new migration record
		rv = newMigrationRecord(status, bulkMigrationId, bulkMigrationName,
				siteId, siteName, toolId, toolName, destinationType, userId,
				targetUrl);

		return rv;
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
	private HashMap<String, Object> newMigrationRecord(StringBuffer status,
			String batch_id, String batch_name, String siteId, String siteName,
			String toolId, String toolName, String destinationType,
			String userId, String targetUrl) {
		HashMap<String, Object> rv = new HashMap<String, Object>();
		Migration m = new Migration(batch_id, batch_name, siteId, siteName,
				toolId, toolName, userId, new java.sql.Timestamp(
						System.currentTimeMillis()), // start
														// time is
														// now
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
			newMigration = repository.save(m);
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
		rv.put(Utils.MIGRATION_STATUS, status.toString());

		return rv;
	}

	/**
	 * upload resource files into Box folder
	 * 
	 * @return
	 */
	@POST
	@Produces("application/json")
	@RequestMapping("/migrationBox")
	@ResponseBody
	public ResponseEntity<String> migrationBox(HttpServletRequest request,
			HttpServletResponse response, UriComponentsBuilder ucb) {

		// box upload
		HashMap<String, String> callStatus = migration_call(request, response,
				Utils.MIGRATION_TYPE_BOX, Utils.getCurrentUserId(request, env));
		HttpHeaders headers = new HttpHeaders();
		if (callStatus.containsKey("errorMessage")) {
			log.info(this + " MigrationBox call error message="
					+ callStatus.get("errorMessage"));
			return new ResponseEntity<String>(callStatus.get("errorMessage"),
					headers, HttpStatus.CONFLICT);
		} else {
			log.info(this + " MigrationBox call migration started id="
					+ callStatus.get("migrationId"));
			if (callStatus.containsKey("migrationId")) {
				// http://serverUrl/migration/id
				headers.setLocation(ucb.path("/migration/{id}")
						.buildAndExpand(callStatus.get("migrationId")).toUri());
			}
			return new ResponseEntity<String>("Migration started.", headers,
					HttpStatus.ACCEPTED);
		}

	}

	/******* bulk migration *********/

	/**
	 * check whether the current user is of Box admin
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/isAdmin")
	public void isAdmin(HttpServletRequest request, HttpServletResponse response) {

		JSONObject jObject = new JSONObject();
		jObject.put("isAdmin", Utils.isCurrentUserCPMAdmin(request, env));

		// JSON response
		JSON_response(response, jObject.toString(),
				"Problem with checking admin status for current user.",
				"/isAdmin");
	}

	/**
	 * Authorize app to access Box account based on current user's role
	 * 
	 * @param request
	 * @param response
	 * @return
	 */
	private String boxAuthorization(HttpServletRequest request,
			HttpServletResponse response) {
		String remoteUserEmail = Utils.getCurrentUserEmail(request, env);
		String boxClientId = BoxUtils.getBoxClientId(request, env);
		String boxAPIUrl = env.getProperty(Utils.BOX_API_URL);
		String boxClientRedirectUrl = BoxUtils.getBoxClientRedirectUrl(request,
				env);

		log.debug("in boxAuthorization");

		if (uRepository.findBoxAuthUserAccessToken(remoteUserEmail) == null) {
			log.debug("user " + remoteUserEmail
					+ " has not authorized to use Box. Start auth process.");
			// go to Box authentication screen
			// get access token and refresh token and store locally
			BoxUtils.authenticate(boxAPIUrl, boxClientId,
					boxClientRedirectUrl, remoteUserEmail, response, uRepository);
			return "UnAuthorized";
		} else {
			log.debug("user " + remoteUserEmail + " already authorized");
			return "Authorized";
		}
	}

	/******************* bulk migration **************************/
	/**
	 * get all bulk migration IDs
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/bulkUpload/all")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllBulkUploadIds(HttpServletRequest request) {
		try {
			return Response.status(Response.Status.OK)
					.entity(repository.getAllBulkMigrations()).build();
		} catch (Exception e) {
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot get all bulk upload records: "
							+ e.getMessage()).build();
		}
	}

	/**
	 * get all ongoing bulk migration IDs
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/bulkUpload/ongoing")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getOngoingBulkUploadIds(HttpServletRequest request) {
		try {
			return Response.status(Response.Status.OK)
					.entity(repository.getOngoingBulkMigrations()).build();
		} catch (Exception e) {
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot get ongoing bulk upload records: "
							+ e.getMessage()).build();
		}
	}

	/**
	 * get all concluded bulk migration IDs
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/bulkUpload/concluded")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getConcludedBulkUploadIds(HttpServletRequest request) {
		// calculate those concluded bulk upload by doing diff of all bulk
		// uploads and those ongoing bulk uploads
		List<Object[]> allBulkConcludedUpgrades = repository
				.getAllBulkMigrations();
		List<Object[]> allBulkOngoingUpgrades = repository
				.getOngoingBulkMigrations();
		allBulkConcludedUpgrades.removeAll(allBulkOngoingUpgrades);

		try {
			return Response.status(Response.Status.OK)
					.entity(allBulkConcludedUpgrades).build();
		} catch (Exception e) {
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot get concluded bulk upload records: "
							+ e.getMessage()).build();
		}
	}

	/**
	 * get all migration records within one bulk upload process
	 * according to TLCPM-295, the json should not return the data node of the status node. 
	 * The status node should return a generic status key with a generic message value that also has error count. 
	 * As well as the list of sites with failure/success flags.
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/bulkUpload/{bulk_upload_id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getMigrationInBulkUpload(
			@PathVariable("bulk_upload_id") String bulk_upload_id,
			HttpServletRequest request) {
		try {
			List<Migration> migrations = repository
					.getMigrationsInBulkUpload(bulk_upload_id);
			
			HashMap<String, Object> statusMap = new HashMap<String, Object>();
			List<HashMap<String, String>> sitesList = new ArrayList<HashMap<String, String>>();
			HashMap<String, Object> totalMap = new HashMap<String, Object>();
			int errorSiteCount = 0;
			boolean ongoing = false;
			for (Migration m : migrations )
			{
				// map to hold site information
				HashMap<String, String> siteMap = new HashMap<String, String>();
				
				String siteId = m.getSite_id();
				String siteStatus = Utils.STATUS_SUCCESS;
				String siteStatusString = m.getStatus();
				if (siteStatusString != null)
				{
					JSONObject siteStatusJson = new JSONObject(siteStatusString);
					// look the tools attribute and find resource tool
					JSONArray itemizedJSONArray = (JSONArray) siteStatusJson.get("data");
					for (int iItem = 0; itemizedJSONArray != null && iItem < itemizedJSONArray.length(); ++iItem) {
						JSONObject itemJSON = itemizedJSONArray.getJSONObject(iItem);
						String path = itemJSON.getString("path");
						String status = itemJSON.getString(Utils.MIGRATION_STATUS);
						if (!path.endsWith("/") && status.indexOf("Box upload successful for file") == -1)
						{
							// file path did not end with "/"
							// and if there is error, status message won't have String "Box upload successful for file"
							// set the site migration status to be failure
							siteStatus = Utils.STATUS_FAILURE;
							errorSiteCount++;
							break;
						}
					}
					siteMap.put("id", siteId);
					siteMap.put("name", m.getSite_name());
					siteMap.put("status", siteStatus);
				}
				else
				{
					siteMap.put("id", siteId);
					siteMap.put("name", m.getSite_name());
					siteMap.put("status", Utils.STATUS_ONGING);
					ongoing = true;
				}
				
				// add this site into sites list
				sitesList.add(siteMap);
			}
			
			if (errorSiteCount != 0)
			{
				// error
				statusMap.put(Utils.MIGRATION_STATUS, Utils.STATUS_FAILURE);
				statusMap.put("errors", errorSiteCount);
			}
			else if (ongoing)
			{
				// migration not finished yet
				statusMap.put(Utils.MIGRATION_STATUS, Utils.STATUS_ONGING);
			}
			else
			{
				// all sites are migrated successfully within the bulk migration
				statusMap.put(Utils.MIGRATION_STATUS, Utils.STATUS_SUCCESS);
			}
			
			totalMap.put("status-summary", statusMap);
			totalMap.put("sites", sitesList);
			
			
			return Response.status(Response.Status.OK).entity(totalMap)
					.build();
		} catch (Exception e) {
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot get migrated records for bulk upload id="
							+ bulk_upload_id + ": " + e.getMessage()).build();
		}
	}

	/**
	 * get all migration records within one bulk upload process
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/bulkUpload/{bulk_upload_id}/{site_id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSiteMigrationInBulkUpload(
			@PathVariable("bulk_upload_id") String bulk_upload_id,
			@PathVariable("site_id") String site_id, HttpServletRequest request) {
		try {
			Migration m = repository.getSiteMigrationInBulkUpload(
					bulk_upload_id, site_id);
			return Response.status(Response.Status.OK).entity(m).build();
		} catch (Exception e) {
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot get migrated record for bulk upload id="
							+ bulk_upload_id + " and site id=" + site_id + ": "
							+ e.getMessage()).build();
		}
	}

	/**
	 * upload resource files into Box folder
	 * 
	 * @return
	 */
	@POST
	@Produces("application/json")
	@RequestMapping("/bulkUpload")
	@ResponseBody
	public ResponseEntity<String> uploadBatch(HttpServletRequest request,
			HttpServletResponse response, UriComponentsBuilder ucb) {
		HttpHeaders headers = new HttpHeaders();

		// use the Box admin id
		String userId = env.getProperty(Utils.BOX_ADMIN_ACCOUNT_ID);
		
		// use the CTools server admin credentials
		String ctoolsAdminUserName = env.getProperty("username");
		String ctoolsAdminUserPassword = env.getProperty("password");
		
		// the bulk migration name based on user input
		String bulkMigrationName = "Default Bulk Upload Name";

		// the set of site ids for bulk migration
		Set<String> bulkUploadSiteIds = new HashSet<String>();
		try {
			MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
			
			if (multipartRequest.getParameter("name") != null){
				// get the user input bulk upload name
				bulkMigrationName = (String) multipartRequest.getParameter("name");
			}
			
			Set set = multipartRequest.getFileMap().entrySet();
			Iterator i = set.iterator();
			while (i.hasNext()) {
				Map.Entry mEntry = (Map.Entry) i.next();
				MultipartFile multipartFile = (MultipartFile) mEntry.getValue();
				try {
					InputStream is = new ByteArrayInputStream(
							multipartFile.getBytes());
					BufferedReader br = new BufferedReader(
							new InputStreamReader(is));
					String strLine;

					while ((strLine = br.readLine()) != null) {
						// read site id, and insert into set
						String siteId = strLine.trim();
						bulkUploadSiteIds.add(siteId);
					}
				} catch (IOException ioException) {
					log.error(this
							+ " uploadBatch Cannot read in uploaded file "
							+ ioException.getStackTrace());
				}
			}
		} catch (MultipartException multipartException) {
			log.error(this + " uploadBatch Cannot read in uploaded file "
					+ multipartException.getStackTrace());
		}

		if (bulkUploadSiteIds.size() > 0) {
			// now that we get the site ids for batch upload
			// start the batch process
			String bulkMigrationId = java.util.UUID.randomUUID().toString();

			for (String siteId : bulkUploadSiteIds) {
				// for each site id, start the migration process
				// associate it with the bulk id
				// 1. get site name
				String siteName = getSiteName(request, siteId);
				String toolId = "";
				String toolName = "";

				// 2. get tool id, tool name
				HashMap<String, String> pagesMap = get_user_project_site_tools(
						request, siteId);
				String pagesString = pagesMap.get("pagesString");
				JSONArray pagesJSON = new JSONArray(pagesString);
				for (int pageIndex = 0; pageIndex < pagesJSON.length(); pageIndex++) {
					JSONObject pageJSON = (JSONObject) pagesJSON.get(pageIndex);
					// look the tools attribute and find resource tool
					JSONArray toolsJSON = (JSONArray) pageJSON.get("tools");
					for (int toolIndex = 0; toolIndex < toolsJSON.length(); toolIndex++) {
						JSONObject toolJSON = (JSONObject) toolsJSON
								.get(toolIndex);
						if ("sakai.resources".equals(toolJSON.get("toolId"))) {
							// found Resources tool
							toolId = (String) toolJSON.get("id");
							toolName = (String) toolJSON.get("title");
						}
					}
				}

				// 3. get box folder id
				String boxAdminClientId = BoxUtils.getBoxClientId(request, env);
				String boxAdminClientSecret = BoxUtils.getBoxClientSecret(
						request, env);
				String boxSiteFolderName = "CTools - " + siteName;

				Info boxFolder = null;
				if (uRepository.findBoxAuthUserAccessToken(Utils.getCurrentUserEmail(request, env)) == null) {
					boxAuthenticate(request, response);
				} else {
					boxFolder = BoxUtils.createNewFolderAtRootLevel(userId,
							boxAdminClientId, boxAdminClientSecret,
							boxSiteFolderName, siteId, uRepository);
				}

				// 4. save the migration record into database
				// the string to hold on all site owner's id, 
				// and the admin id who are doing bulk migration is listed first
				StringBuffer allSiteOwners = new StringBuffer(userId);
				if (boxFolder != null) {
					String boxFolderId = boxFolder.getID();
					String boxFolderName = boxFolder.getName();

					try
					{
						// add site members to Box folder as collaborators
						HashMap<String, String> userRoles = get_site_members(
								request, siteId);
						for (String userEid : userRoles.keySet()) {
							String userRole = userRoles.get(userEid);
							String userEmail = Utils.getUserEmailFromUserId(userEid);
							BoxUtils.addCollaboration(
									env.getProperty(Utils.BOX_ADMIN_ACCOUNT_ID),
									userEmail, userRole, boxFolderId,
									boxAdminClientId, boxAdminClientSecret, uRepository);
							if (Utils.ROLE_OWNER.equals(userRole))
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
						log.error("Problem retrieving site members for site id = " + siteId + " " + e.getStackTrace());
					}

					// now after all checks passed, we are ready for migration
					// save migration record into database
					HashMap<String, Object> saveBulkMigration = saveBulkMigrationRecord(
							bulkMigrationId, bulkMigrationName, siteId,
							siteName, toolId, toolName, boxFolderId,
							boxFolderName, allSiteOwners.toString());

					// 5. delegate the actual content migrations to async calls
					// Concatenating all site owners' names together with 
					HashMap<String, String> status = createMigrationTask(
							request, response, Utils.MIGRATION_TYPE_BOX, userId,
							new HashMap<String, String>(), boxFolderId, siteId,
							toolId, saveBulkMigration);
					if (status.containsKey("errorMessage")) {
						log.info(this + " batch upload call for site id="
								+ siteId + " error message="
								+ status.get("errorMessage"));
					} else if (status.containsKey("migrationId")) {
						log.info(this + " batch upload call for site id="
								+ siteId + " migration started id="
								+ status.get("migrationId"));
					}
				}
			}
		}
		return new ResponseEntity<String>("Bulk Migration started.", headers,
				HttpStatus.ACCEPTED);
	}

	/**
	 * use CTools entity feed to get site name based on site id
	 * 
	 * @param request
	 * @return
	 */
	private String getSiteName(HttpServletRequest request, String siteId) {
		String siteName = "";
		// login to CTools and get sessionId
		HashMap<String, Object> sessionAttributes = Utils.login_becomeuser(env,
				request, Utils.getCurrentUserId(request, env));
		if (sessionAttributes.containsKey(Utils.SESSION_ID)) {
			String sessionId = (String) sessionAttributes.get(Utils.SESSION_ID);

			// get all sites that user have permission site.upd
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/<siteId>.json?_sessionId=<sessionId>"
			String requestUrl = env.getProperty(Utils.ENV_PROPERTY_CTOOLS_SERVER_URL)
					+ "direct/site/" + siteId + ".json?_sessionId=" + sessionId;
			log.info(this + requestUrl);
			try {
				String siteJson = restTemplate.getForObject(requestUrl,
						String.class);
				JSONObject siteJSONObject = new JSONObject(siteJson);
				siteName = (String) siteJSONObject.get("title");
			} catch (RestClientException e) {
				log.error(requestUrl + e.getMessage());
			}

		}
		return siteName;
	}
	
	/******************* migtation choices ********************/
	/**
	 * save user input for site delete choices into database
	 * 
	 * @return
	 */
	@POST
	@Produces("application/json")
	@RequestMapping("/deleteSite")
	@ResponseBody
	public ResponseEntity<String> deleteSiteChoice(HttpServletRequest request,
			HttpServletResponse response, UriComponentsBuilder ucb) {
		
		HttpHeaders headers = new HttpHeaders();
		
		String userId = Utils.getCurrentUserId(request, env);
		HashMap<String, String> rv = new HashMap<String, String>();

		// we need to do series checks to make sure the migration request is
		// valid
		// 1. check if missing siteId
		Map<String, String[]> parameterMap = request.getParameterMap();
		String[] siteIds = parameterMap.get("siteId");
		if (siteIds == null || siteIds.length == 0) {
			String errorMessage = "deleteSiteChoice request missing required parameter: siteId";
			return new ResponseEntity<String>(errorMessage, headers, HttpStatus.BAD_REQUEST);
		}
		log.info("delete request for site " + siteIds);
		
		// 2. save the choices into database
		StringBuffer errorMessages = new StringBuffer();
		List<SiteDeleteChoice> cList = new ArrayList<SiteDeleteChoice>();
		for (int i = 0; i < siteIds.length; i++)
		{
			// now for all sites, save the delete site choice into database
			String siteId = siteIds[i];
			SiteDeleteChoice c = new SiteDeleteChoice(siteId, userId, 
							new java.sql.Timestamp(System.currentTimeMillis()));
			try {
				c = cRepository.save(c);
				
				// upon successful save
				// add the record into the return JSON
				cList.add(c);
			} catch (Exception e) {
				errorMessages.append("Exception in saving siteDeleteChoice " + c.toString() + " ");
			}
		}
		
		if (errorMessages.length() > 0)
		{
			// in case of error
			log.error(errorMessages.toString());
			return new ResponseEntity<String>(errorMessages.toString(), headers, HttpStatus.CONFLICT);
		} else {
			log.info(this + " Delete site choice saved ");
			return new ResponseEntity<String>("Delete site choices saved.", headers,
					HttpStatus.ACCEPTED);
		}

	}
	
	/**
	 * GET: check if a site has been marked as "to be deleted": 
	 * /isSiteToBeDeleted?siteId=<site_id> 
	 * returns following JSON or empty set. 
	 * {
	 *  "siteId": <site_id>, 
	 *  "userId": <user id who made the request>"
	 *  "date": <unix timestamp of when it is marked to be deleted>
	 *  }
	 * 
	 * @return
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@RequestMapping("/isSiteToBeDeleted")
	public Response isSiteToBeDeleted(HttpServletRequest request,
			HttpServletResponse response, UriComponentsBuilder ucb) {
		
		// 1. get the siteId request parameter
		Map<String, String[]> parameterMap = request.getParameterMap();
		String[] siteIds = parameterMap.get("siteId");
		if (siteIds == null || siteIds.length != 1) {
			String errorMessage = "isSiteToBeDeleted request missing or multiple required parameter: siteId";
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(errorMessage).build();
		}
		String siteId = siteIds[0];
		
		// 2. get the siteDeleteChoice record from database for this site	
		try {
			return Response.status(Response.Status.OK)
					.entity(cRepository.findSiteDeleteChoiceForSite(siteId)).build();
		} catch (Exception e) {
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Problem getting SiteDeleteChoice for " + siteId
							+ ": " + e.getMessage()).build();
		}

	}
	
	/**
	 * save user input for do-not-migrate
	 * 
	 * @return
	 */
	@POST
	@Produces("application/json")
	@RequestMapping("/doNotMigrateTool")
	@ResponseBody
	public ResponseEntity<String> doNotMigrateToolChoice(HttpServletRequest request,
			HttpServletResponse response, UriComponentsBuilder ucb) {
		
		HttpHeaders headers = new HttpHeaders();
		
		String userId = Utils.getCurrentUserId(request, env);
		HashMap<String, String> rv = new HashMap<String, String>();

		// we need to do series checks to make sure the migration request is
		// valid
		// 1. check if missing or more than one siteId
		Map<String, String[]> parameterMap = request.getParameterMap();
		String[] siteIds = parameterMap.get("siteId");
		if (siteIds == null || siteIds.length != 1) {
			String errorMessage = "doNotMigrateToolChoice request missing or multiple required parameter: siteId";
			return new ResponseEntity<String>(errorMessage, headers, HttpStatus.BAD_REQUEST);
		}
		// check if missing or more than one toolId
		String[] toolIds = parameterMap.get("toolId");
		if (toolIds == null || toolIds.length != 1) {
			String errorMessage = "doNotMigrateToolChoice request missing or multiple required parameter: toolId";
			return new ResponseEntity<String>(errorMessage, headers, HttpStatus.BAD_REQUEST);
		}
		// the target site id and tool id
		String siteId = siteIds[0];
		String toolId = toolIds[0];
		log.info("request migration for site " + siteId + " and toolId " + toolId);
		
		// 2. save the choices into database
		StringBuffer errorMessages = new StringBuffer();
		SiteToolExemptChoice c = new SiteToolExemptChoice(siteId, toolId, userId, 
							new java.sql.Timestamp(System.currentTimeMillis()));
		try {
			c = tRepository.save(c);
		} catch (Exception e) {
			errorMessages.append("Exception in saving siteToolExcemptChoice " + c.toString() + " ");
		}
		
		if (errorMessages.length() > 0)
		{
			// in case of error
			log.error(errorMessages.toString());
			return new ResponseEntity<String>(errorMessages.toString(), headers, HttpStatus.CONFLICT);
		} else {
			log.info(this + " site tool delete exemption choice saved ");
			return new ResponseEntity<String>("site tool delete exempt choice saved.", headers,
					HttpStatus.ACCEPTED);
		}

	}
	
	/*GET
	 * check if a tools within a site has been marked as "not migrate": 
	 * /siteToolNotMigrate?siteId=<site_id>
	 * returns following JSON or empty set: 
	 * {
	 * [ 
	 * "siteId": <site_id>,
	 * "toolId": <tool_id>, 
	 * "user": <user id> 
	 * "date": <unix timestap of when it is marked to be deleted> 
	 * ],
	 * [
	 * "siteId": <site_id>,
	 * "toold": <tool_id>,
	 * "user": <user id>,
	 * "date": <unix timestap of when it is marked to be deleted>
	 * ]
	 * }
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@RequestMapping("/siteToolNotMigrate")
	public Response siteToolNotMigrate(HttpServletRequest request,
			HttpServletResponse response, UriComponentsBuilder ucb) {
		
		// 1. get the siteId request parameter
		Map<String, String[]> parameterMap = request.getParameterMap();
		String[] siteIds = parameterMap.get("siteId");
		if (siteIds == null || siteIds.length != 1) {
			String errorMessage = "siteToolNotMigrate request missing or multiple required parameter: siteId";
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(errorMessage).build();
		}
		String siteId = siteIds[0];
		
		// 2. get the SiteToolExemptChoice record from database for this site	
		try {
			return Response.status(Response.Status.OK)
					.entity(tRepository.findSiteToolExemptChoiceForSite(siteId)).build();
		} catch (Exception e) {
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Problem getting SiteToolExemptionChoice for " + siteId
							+ ": " + e.getMessage()).build();
		}

	}
}
