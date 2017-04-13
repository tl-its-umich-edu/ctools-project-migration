package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.http.client.*;
import org.apache.http.HttpResponse;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.client.methods.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ErrorAttributes;
import org.springframework.boot.autoconfigure.web.ErrorController;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.util.StopWatch;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;
import java.sql.Timestamp;
import java.io.IOException;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;

import com.box.sdk.BoxItem.Info;

import java.io.*;

@EnableAsync
@PropertySource("file:${catalina.base:/usr/local/ctools/app/ctools/tl}/home/application.properties")
@RestController
public class MigrationController implements ErrorController {

	private static final String JSON_ATTR_MEMBERSHIP_COLLECTION = "membership_collection";
	
	private static final String JSON_ATTR_SITE_COLLECTION = "site_collection";

	private static final String BOX_AUTHORIZED_HTML = "<link rel=\"stylesheet\" href=\"vendors/bootstrap/bootstrap.min.css\"><div class=\"jumbotron\" style=\"background:#fff\"><h1 role=\"alert\">Authorized</h1><p>You can now select a Box folder and migrate project sites to it.</p><p><a onclick=\"window.parent.closeBoxAuthModal()\" href=\"#\">Close</a></p></div>";
	private static final String BOX_UNAUTHORIZED_HTML = "<link rel=\"stylesheet\" href=\"vendors/bootstrap/bootstrap.min.css\"><div class=\"jumbotron\"><h1 role=\"alert\">Unauthorized!</h1><p>You need to authorize Box.<a target=\"_top\" href=\"/\">Go back</a></p></div>";

	private static final Logger log = LoggerFactory
			.getLogger(MigrationController.class);

	private static final String PATH = "/error";

	@Autowired
	MigrationRepository repository;

	@Autowired
	BoxAuthUserRepository uRepository;

	@Autowired
	SiteDeleteChoiceRepository cRepository;

	@Autowired
	SiteToolExemptRepository tRepository;

	@Autowired
	MigrationTaskService migrationTaskService;

	@Autowired
	private Environment env;

	@Autowired
	private MigrationInstanceService migrationInstanceService;

	@Autowired
	private ErrorAttributes errorAttributes;

	/**
	 * get all CTools sites where user have Owner role inside
	 *
	 * @return
	 */
	@GET
	@RequestMapping("/projects")
	public void getProjectSites(HttpServletRequest request,
			HttpServletResponse response) {

		// get non-course sites that user has Owner role inside
		HashMap<String, String> projectsMap = getUserAllSitesMap(request);

		// JSON response
		JSON_response(response, projectsMap.get("projectsString"),
				projectsMap.get("errorMessage"), projectsMap.get("requestUrl"));
	}

	/**
	 * return HashMap object with non-course sites that user has Owner role inside
	 * permission
	 *
	 * @param request
	 * @return
	 */
	private HashMap<String, String> getUserAllSitesMap(
			HttpServletRequest request) {
		
		// default site types to be included in CPM tool
		List<String> allowedSiteTypes = Arrays.asList("project", "myworkspace", "specialized_projects");
		if (env.getProperty(Utils.ENV_PROPERTY_ALLOWED_SITE_TYPES) != null)
		{
			//get the property setting for site types to be included in CPM tool
			allowedSiteTypes = Arrays.asList(env.getProperty(Utils.ENV_PROPERTY_ALLOWED_SITE_TYPES).split(","));
		}
		
		String userEid = Utils.getCurrentUserId(request, env);
		// get session id
		String sessionId = getUserSessionId(request);

		HashMap<String, String> projectsMap = get_user_sites(userEid, sessionId, allowedSiteTypes);

		// this is a json value contains non-MyWorkspace sites
		String sitesJson = projectsMap.get("projectsString");
		try {
			JSONObject sitesJSONObject = new JSONObject(sitesJson);
			// get the json value for user MyWorkspace site
			String myworkspaceJson = get_user_myworkspace_site_json(userEid, sessionId);
			if (myworkspaceJson != null) {
				JSONObject mwJSONObject = new JSONObject(myworkspaceJson);
				// check site type
				if (mwJSONObject != null && mwJSONObject.get("type") != null 
						&& allowedSiteTypes.contains(mwJSONObject.get("type")))
				{
					// insert the MyWorkspace json into the other-sites JSON
					sitesJSONObject.append(JSON_ATTR_SITE_COLLECTION, mwJSONObject);
				}
			}
			// get the updated sites json string with MyWorkspace info inserted
			projectsMap.put("projectsString", sitesJSONObject.toString());
		} catch (JSONException e) {
			log.error(this + " error parsing sites JSON value " + sitesJson);
		}
		return projectsMap;
	}

	/**
	 * REST API call to get user MyWorkspace site
	 *
	 * @param req
	 * @return
	 */
	private String get_user_myworkspace_site_json(String userEid, String sessionId) {
		String siteJson = null;

		String requestUrl = "";

		RestTemplate restTemplate = new RestTemplate();
		// the url should be in the format of
		// "https://server/direct/site/<siteId>.json?_sessionId=<sessionId>"
		// Response Code Details: 200 plus data; 404 if not found, 406 if
		// format unavailable
		// user MyWorkspace site ID could be two forms
		// 1. try "~<user_id>" first
		requestUrl = Utils.directCallUrl(env, "site/~" + userEid + ".json?", sessionId);
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
				requestUrl = Utils.directCallUrl(env, "user/" + userEid + ".json?", sessionId);
				log.info(this + " get_user_myworkspace_site_json "
						+ requestUrl);
				ResponseEntity<String> userEntity = restTemplate
						.getForEntity(requestUrl, String.class);
				if (userEntity.getStatusCode().is2xxSuccessful()) {
					JSONObject userObject = new JSONObject(
							userEntity.getBody());
					String userId = (String) userObject.get("id");
					// use this userId to form user myworkspace id
					requestUrl = Utils.directCallUrl(env, "site/~" + userId + ".json?", sessionId);
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

		return siteJson;
	}

	/**
	 * REST API call to get all sites for user
	 *
	 * @param req
	 * @return
	 */
	private HashMap<String, String> get_user_sites(String currentUserId, String sessionId, List<String> allowedSiteTypes) {
		HashMap<String, String> rv = new HashMap<String, String>();

		String projectsString = "";
		String errorMessage = "";
		String requestUrl = "";

		// get all sites that user is of Owner role
		RestTemplate restTemplate = new RestTemplate();
		requestUrl = Utils.directCallUrl(env, "membership.json?role=" + Utils.ROLE_OWNER + "&", sessionId);
		log.info(this + " get_user_sites " + requestUrl);
		try {
			String membershipString = restTemplate.getForObject(requestUrl,
					String.class);

			// update the projectString by filtering based on site Owner role
			projectsString = filterSites(membershipString, currentUserId, sessionId, allowedSiteTypes);
		} catch (RestClientException e) {
			errorMessage = e.getMessage();
			log.error(requestUrl + errorMessage);
		}

		rv.put("projectsString", projectsString);
		rv.put("errorMessage", errorMessage);
		rv.put("requestUrl", requestUrl);
		return rv;
	}

	/**
	 * filter out those sites with only evaluation tool inside
	 * @param request
	 * @param membershipString
	 * @param currentUserId
	 * @return
	 */
	private String filterSites(String membershipString, String currentUserId, String sessionId, List<String> allowedSiteTypes) {
		JSONArray ownerSitesJSONArray = new JSONArray();
		JSONObject membershipsObject = new JSONObject();
		try {
			membershipsObject = new JSONObject(membershipString);
			// the JSON format as follows:
			// "entityPrefix": "membership"
			// "site_collection": <the myworkspace site>
			// "membership_collection": <the sites that user is member of>
		} catch (JSONException e) {
			log.error(this + " error parsing sites JSON value " + membershipString);
			return "";
		}
			
		// get site array
		JSONArray membershipsJSONArray = membershipsObject.getJSONArray(JSON_ATTR_MEMBERSHIP_COLLECTION);

		for (int iMembership = 0; membershipsJSONArray != null && iMembership < membershipsJSONArray.length(); iMembership++) {
			JSONObject membershipJSON = membershipsJSONArray.getJSONObject(iMembership);
			
			// entityId: 9124db8a-171a-46a9-bcf3-5b126842c54f::site:1065049660132-200130
			// check for site id
			if (membershipJSON.isNull("entityId"))
			{
				log.error(this + " no entityId field in " + membershipJSON);
				continue;
			}
			
			String entityId = membershipJSON.getString("entityId");
			String[] siteIdParts = entityId.split("::site:");
			// entityId: 9124db8a-171a-46a9-bcf3-5b126842c54f::site:1065049660132-200130
			if (siteIdParts.length != 2)
			{
				log.error(this + " entityId value not in right format user_id::site:site_id in " + membershipJSON);
				continue; 
			}
			
			String siteId = siteIdParts[1];
			
			// now we need to get site information
			// get all sites that user is of Owner role
			RestTemplate restTemplate = new RestTemplate();
			String requestUrl = Utils.directCallUrl(env, "site/" + siteId + ".json?", sessionId);
			log.info(this + " get site " + requestUrl);
			JSONObject siteJSON = new JSONObject();
			try {
				siteJSON = new JSONObject(restTemplate.getForObject(requestUrl,
						String.class));

			} catch (RestClientException e) {
				log.error(this + requestUrl + e.getMessage());
			}
			
			String siteType = (siteJSON.has("type") && !siteJSON.isNull("type")) ? siteJSON.getString("type"):null;
			if (!allowedSiteTypes.contains(siteType))
				// if not the wanted type, bypass the site
				continue;
			
			// keep the site JSON if current user has Owner role in this site
			ownerSitesJSONArray.put(siteJSON);
		}
		
		JSONObject sitesJSONObject = new JSONObject();
		sitesJSONObject.put(JSON_ATTR_SITE_COLLECTION, ownerSitesJSONArray);
		return sitesJSONObject.toString();
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
		String sessionId = getUserSessionId(request);
		HashMap<String, String> pagesMap = migrationTaskService.get_user_project_site_tools(site_id, sessionId, true);
		JSON_response(response, pagesMap.get("pagesString"),
				pagesMap.get("errorMessage"), pagesMap.get("requestUrl"));
	}

	/**
	 * API call to get CTools site membership
	 * The default format is an object where uniqname is the key and the value is
	 * the user's sakai role.  This is the same as setting the optional query parameter "format"
	 * to be "short". Setting the "format" query parameter to "long" will return a value with
	 * the same key but the value to be an object with attributes of memberRole and userSortName.
	 *
	 * @return
	 */
	@GET
	@RequestMapping("/siteMembership/{siteId}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getSiteMembershipAPI(
			@PathVariable("siteId") String siteId,
			@RequestParam(value="format",defaultValue=Utils.SHORT_MEMBERSHIP_FORMAT, required=false) String format,
			HttpServletRequest request) {
		try {

			// validate the input
			format=format.toLowerCase();
			if (!(Utils.SHORT_MEMBERSHIP_FORMAT.equals(format) || Utils.LONG_MEMBERSHIP_FORMAT.equals(format))) {
				String msg = "Invalid format for getSiteMembershipSPI: ["+format+"]";
				log.error(msg);
				return Response.status(Response.Status.BAD_REQUEST).entity(msg).build();
			}
			
			String sessionId = getUserSessionId(request);

			// return CTools site members

			HashMap<String, Object> site_members = migrationTaskService.get_site_members(siteId, sessionId,format);
			for (String userEid : site_members.keySet()) {
				if (userEid.equals("errorMessage")) {
					return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(site_members).build();
				}
			}

			return Response.status(Response.Status.OK).entity(site_members).build();
		} catch (Exception e) {
			String msg = "The membership list for this site cannot be generated. " +
					"There is at least one site member with an invalid user ID. " +
					"Please contact ITS for help with this error.";
			log.error("Cannot get CTools site members for site " + siteId + ":" + e.getMessage());
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(msg).build();
		}
	}

	private String getUserSessionId(HttpServletRequest request) {
		String userEid = Utils.getCurrentUserId(request, env);
		log.debug("gUSI: userEid: {}",userEid);
		HashMap<String, Object> sessionAttributes = Utils.login_becomeuser(env, userEid);
		if (!sessionAttributes.containsKey(Utils.SESSION_ID)) {
			// exit if the session attributes does not contain session_id
			log.error("CPM tool cannot login as " + userEid);
			return null;
		}
		return (String) sessionAttributes.get(Utils.SESSION_ID);
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
			List<Migration> migrated = repository.findMigrated(userId);
			return Response.status(Response.Status.OK)
					.entity(migrated).build();
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

		String sessionId = getUserSessionId(request);

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
		HashMap<String, String> pagesMap = migrationTaskService.get_user_project_site_tools(siteId, sessionId);
		String pagesString = pagesMap.containsKey("pagesString") ? pagesMap.get("pagesString") : null;
		if (pagesString == null || 
			(pagesString != null && pagesString.indexOf(toolId) == -1)) {
			rv.put("errorMessage", "Invalid tool id = " + toolId
					+ " for site id= " + siteId + " for user " + remoteUser);
			return rv;
		}

		// 3. check if there is an ongoing migration for the same site and tool
		boolean valid_migration_request = true;
		List<Migration> migratingSiteTools = repository
				.findMigrating(remoteUser);
		for (Migration m : migratingSiteTools) {
			log.info("migrating record " + m.getSite_id() + " tool id=" + toolId
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
		HashMap<String, Object> saveMigration = saveMigrationRecord(request, sessionId);

		rv = createMigrationTask(
				request,
				response,
				target,
				remoteUser,
				rv, siteId, toolId,
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
			String siteId, String toolId,
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
			
			HashMap<String, Object> sessionAttributes = Utils
					.login_becomeuser(env, remoteUser);

			if (Utils.MIGRATION_TYPE_ZIP.equals(target)) {
				// call zip file download for site resource
				log.info("start to call zip migration for siteId="
						+ siteId + " tooId=" + toolId);
				StopWatch stopWatch = new StopWatch();
				stopWatch.start();
				log.info("Migration task started: siteId=" + siteId + " migation id=" + migrationId + " target=zip");

				migrationTaskService.downloadZippedFile(env, request, response, remoteUser, sessionAttributes, siteId, migrationId, repository);
				stopWatch.stop();
				log.info("Migration task started: siteId=" + siteId + " migation id=" + migrationId + " target=zip " + stopWatch.prettyPrint());
			} else if (Utils.MIGRATION_MAILARCHIVE_TYPE_ZIP.equals(target) || Utils.MIGRATION_MAILARCHIVE_TYPE_MBOX.equals(target) ) {
				log.info("start to call MailArchive migration for siteId="
						+ siteId + " tooId=" + toolId);
				StopWatch stopWatch = new StopWatch();
		        stopWatch.start();
		        log.info("Migration task started: siteId=" + siteId + " migation id=" + migrationId + " target=zip");

				migrationTaskService.downloadMailArchiveZipFile(env, request, response, remoteUser, sessionAttributes, siteId, migrationId, repository);
				stopWatch.stop();
		        log.info("Migration task started: siteId=" + siteId + " migation id=" + migrationId + " target=zip " + stopWatch.prettyPrint());
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
			response.setCharacterEncoding("UTF-8");
			if (jsonValue == null || jsonValue.isEmpty()) {
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				response.getWriter().write(errorMessage == null? "":errorMessage);
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
		// get CoSign user id
		String userId = Utils.getUserLoginId(request,env);
		String boxClientId = BoxUtils.getBoxClientIdOrSecret(userId, Utils.BOX_ID);
		String boxClientSecret = BoxUtils.getBoxClientIdOrSecret(userId, Utils.BOX_SECRET);
		//String remoteUserEmail = Utils.getCurrentUserEmail(request, env);
		String remoteUserEmail = getCurrentUserEmail(request, env);

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
			return BoxUtils.getBoxFolders(remoteUserEmail, boxClientId, boxClientSecret, uRepository);
		}
		return null;
	}

	public String getCurrentUserEmail(HttpServletRequest request, Environment env) {
		String remoteUserEmail = Utils.getCurrentUserId(request, env);
		log.info("getCurrentUserEmail currentUserId=" + remoteUserEmail);

		if (Utils.isCurrentUserCPMAdmin(request, env)) {
			// use admin account id instead
			remoteUserEmail = env.getProperty(Utils.BOX_ADMIN_ACCOUNT_ID);
			log.info("getCurrentUserEmail currentUserCPMAdmin=" + remoteUserEmail);
		}
		//remoteUserEmail = getUserEmailFromUserId(remoteUserEmail,env.getProperty(Utils.DEFAULT_EMAIL_MEMBER_SUFFIX));
		remoteUserEmail = migrationTaskService.getUserEmailFromUserId(remoteUserEmail);
		return remoteUserEmail;
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
		String userEmail = getCurrentUserEmail(request, env);

		// the return string
		String rv = "";

		// check whether the user authentication token is store in memory
		if (uRepository.findBoxAuthUserAccessToken(getCurrentUserEmail(request, env)) == null) {
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
		String userEmail = getCurrentUserEmail(request, env);
		String rv = uRepository.findBoxAuthUserAccessToken(userEmail);

		if (rv == null) {
			// get the authCode,
			// and get access token and refresh token subsequently
			String boxTokenUrl = env.getProperty(Utils.BOX_TOKEN_URL);
			String userId = Utils.getUserLoginId(request,env);
			String boxClientId = BoxUtils.getBoxClientIdOrSecret(userId, Utils.BOX_ID);
			String boxClientSecret = BoxUtils.getBoxClientIdOrSecret(userId, Utils.BOX_SECRET);
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
		return Boolean.valueOf(uRepository.findBoxAuthUserAccessToken(getCurrentUserEmail(request, env)) != null);
	}

	/**
	 * Save Migration record to DB
	 *
	 * @return HasMap key="status", value=status message; key="migration",
	 *         value=MigrationObject
	 */
	private HashMap<String, Object> saveMigrationRecord(
			HttpServletRequest request, String sessionId) {
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
		
		String allSiteOwners = migrationTaskService.getAllSiteUsersWithOwnerOrMaintainerRole(
				sessionId, siteId, userId);

		// assign null values to batch id and name
		// create new migration record
		rv = migrationTaskService.newMigrationRecord(status, null, null, siteId, siteName, toolId,
				toolName, destinationType, allSiteOwners, targetUrl);

		return rv;
	}

	/**
	 * Save Bulk Migration Email to Google record to DB
	 * 
	 * @return HasMap key="status", value=status message; key="migration",
	 *         value=MigrationObject
	 */
	private HashMap<String, Object> saveBulkGoogleMigrationRecord(
			String bulkMigrationId, String bulkMigrationName, String siteId,
			String siteName, String toolId, String toolName,
			String googleGroupId, String googleGroupName, String userId, String status) {
		// the return hashmap provide newly created Migration object, and status
		// message
		HashMap<String, Object> rv;

		log.info("Google group name =" + googleGroupName);

		String targetUrl = "<google_group_path>" + googleGroupId;

		// create new migration record
		rv = migrationTaskService.newMigrationRecordForMsgMigration(status, bulkMigrationId, bulkMigrationName,
				siteId, siteName, toolId, toolName, Utils.MIGRATION_TYPE_GOOGLE_GROUP, userId,
				targetUrl,null);

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
		String remoteUserEmail = getCurrentUserEmail(request, env);
		// get CoSign user id
		String userId = Utils.getUserLoginId(request,env);
		String boxClientId = BoxUtils.getBoxClientIdOrSecret(userId, Utils.BOX_ID);
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
				String siteStatusString = m.getStatus();

				if (siteStatusString == null)
				{
					siteMap.put("id", siteId);
					siteMap.put("name", m.getSite_name());
					siteMap.put("status", Utils.STATUS_ONGING);
					ongoing = true;
				}
				else if (siteStatusString.equals(Utils.NO_CTOOLS_SITE))
				{
					siteMap.put("id", siteId);
					siteMap.put("name", m.getSite_name());
					siteMap.put("status", Utils.STATUS_FAILURE);
					errorSiteCount++;
				}
				else
				{
					JSONObject siteStatusJson = new JSONObject(siteStatusString);
					String siteStatus = (String) siteStatusJson.get(Utils.REPORT_ATTR_STATUS);
					if (Utils.REPORT_STATUS_ERROR.equals(siteStatus))
					{
						errorSiteCount++;
					}
					siteMap.put("id", siteId);
					siteMap.put("name", m.getSite_name());
					siteMap.put("status", siteStatus);
				}

				// add this site into sites list
				sitesList.add(siteMap);
			}

			if (errorSiteCount != 0)
			{
				// error
				statusMap.put(Utils.REPORT_ATTR_STATUS, Utils.STATUS_FAILURE);
				statusMap.put("errors", errorSiteCount);
			}
			else if (ongoing)
			{
				// migration not finished yet
				statusMap.put(Utils.REPORT_ATTR_STATUS, Utils.STATUS_ONGING);
			}
			else
			{
				// all sites are migrated successfully within the bulk migration
				statusMap.put(Utils.REPORT_ATTR_STATUS, Utils.STATUS_SUCCESS);
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

		HashMap<String, Object> sessionAttributes = Utils.login_become_admin(env);
        
        if(sessionAttributes.isEmpty()){
        	log.error("Logging into Ctools failed for the admin user.");
        	return new ResponseEntity<String>("Cannot start batch upload because logging into Ctools failed for the admin user.", headers,
    				HttpStatus.BAD_REQUEST);
        }
        String sessionId = (String) sessionAttributes.get(Utils.SESSION_ID);


		// use the Box admin id
		String userId = env.getProperty(Utils.BOX_ADMIN_ACCOUNT_ID);

		// use the CTools server admin credentials
		String ctoolsAdminUserName = env.getProperty(Utils.ENV_PROPERTY_USERNAME);
		String ctoolsAdminUserPassword = env.getProperty(Utils.ENV_PROPERTY_PASSWORD);

		// the bulk migration name based on user input
		String bulkMigrationName = "Default Bulk Upload Name";
		
		MultipartHttpServletRequest multipartRequest = (MultipartHttpServletRequest) request;
		// the migration tool id, "sakai.resources" or "sakai.mailbox"
		String migrationToolId = "resources".equals(multipartRequest.getParameter("source"))?"sakai.resources":"sakai.mailbox" ;
		
		// the set of site ids for bulk migration
		Set<String> bulkUploadSiteIds = new HashSet<String>();
		try {

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
						
						StringBuffer aggregatedSiteId = new StringBuffer();
						// read site id, and insert into set
						String siteId = strLine.trim();
						aggregatedSiteId.append(siteId).append(Utils.BOX_BULK_UPLOAD_SEPARATOR);
						aggregatedSiteId.append(sessionId).append(Utils.BOX_BULK_UPLOAD_SEPARATOR);
						aggregatedSiteId.append(userId).append(Utils.BOX_BULK_UPLOAD_SEPARATOR);
						aggregatedSiteId.append(ctoolsAdminUserName).append(Utils.BOX_BULK_UPLOAD_SEPARATOR);
						aggregatedSiteId.append(bulkMigrationName).append(Utils.BOX_BULK_UPLOAD_SEPARATOR);
						aggregatedSiteId.append(migrationToolId);
						
						bulkUploadSiteIds.add(aggregatedSiteId.toString());
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

		// queue up the site ids for future migration
		migrationTaskService.queueMigrationSiteIds(bulkUploadSiteIds);
		
		// return early for the API call
		return new ResponseEntity<String>("Bulk Migration started.", headers,
				HttpStatus.ACCEPTED);
	}
	
	private JSONObject googleGlobalFailureReport(JSONObject statusObj, JSONObject details, String detailMsg,JSONObject addMembers) {
		details.put(Utils.REPORT_ATTR_MESSAGE, detailMsg);
		addMembers.put(Utils.REPORT_ATTR_STATUS,Utils.REPORT_STATUS_ERROR);
		statusObj.put(Utils.REPORT_ATTR_STATUS, Utils.REPORT_STATUS_ERROR);
		return statusObj;
	}

	// TODO: test adding /test name space for ad-hoc testing of individual methods.

	//////////////
	// add exception handler for when upstream servers don't have a useful response.
	// TODO: move / generalize the exception handler?
	@ExceptionHandler(org.springframework.web.client.HttpServerErrorException.class)
	void handleServerError(HttpServletResponse response) throws IOException {
		log.debug("in exception handler: "+response.getStatus());
		response.sendError(HttpStatus.BAD_GATEWAY.value(),"Upstream server failed to respond correctly");
	}

	// Get the json version of the site info.
	protected JSONObject getSiteInfoJson(String sessionId, String siteId) {

		log.debug("enter getSiteInfoJson: "+siteId);

		// get the site info from ctools
		JSONObject siteJSONObject = null;

		// get site info for site as json.
		RestTemplate restTemplate = new RestTemplate();
		// the url should be in the format of
		// "https://server/direct/site/<siteId>.json?_sessionId=<sessionId>"
		String requestUrl = Utils.directCallUrl(env, "site/" + siteId + ".json?", sessionId);
		log.info("siteInfo url: " + requestUrl);
		try {
			String siteJson = restTemplate.getForObject(requestUrl,
					String.class);
			siteJSONObject = new JSONObject(siteJson);
		} catch (RestClientException e) {
			log.error(requestUrl + e.getMessage());
			// Don't hide the error.
			throw e;
		}
		return siteJSONObject;
	}

	/******************* migration choices ********************/
	/**
	 * save user input for site delete choices into database
	 *  
	 * 1. to not migrate a tool:
	 * /deleteSite?siteId=<site_id>&toolId=<tool_id>
	 * 
	 * 2. to reset the previous setting:
	 * /deleteSite?siteId=<site_id>&toolId=<tool_id>&reset=true
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
		
		StringBuffer errorMessages = new StringBuffer();
		
		// we need to do series checks to make sure the migration request is valid
		// 1. check if missing siteId
		Map<String, String[]> parameterMap = request.getParameterMap();
		String[] siteIds = parameterMap.get("siteId");
		if (siteIds == null || siteIds.length == 0) {
			errorMessages.append("deleteSiteChoice request missing required parameter: siteId");
		}
		
		// 2. get the reset param if there is any
		boolean reset = false;
		String[] resetParam = parameterMap.get("reset");
		if (resetParam != null) { 
			if (resetParam.length != 1) {
				errorMessages.append("deleteSiteChoice request has multiple value for parameter: reset");
			}
			else
			{
				if (!"true".equals(resetParam[0]))
				{
					errorMessages.append("deleteSiteChoice request has wrong value " + resetParam[0] + " for parameter: reset");
				}
				else
				{
					reset = true;
				}
			}
		}
				
		if (errorMessages.length() > 0) {
			return new ResponseEntity<String>(errorMessages.toString(), headers, HttpStatus.BAD_REQUEST);
		}

		List<SiteDeleteChoice> cList = new ArrayList<SiteDeleteChoice>();
		for (int i = 0; i < siteIds.length; i++)
		{
			// now for all sites, save the delete site choice into database
			String siteId = siteIds[i];
			try {
				if (reset) {
					// remove the tool exempt record from database
					cRepository.deleteSiteDeleteChoice(siteId);
				}
				else {
					// save the choices into database  
					SiteDeleteChoice c = new SiteDeleteChoice(siteId, userId, 
							new java.sql.Timestamp(System.currentTimeMillis()));

					c = cRepository.save(c);
					
					// upon successful save
					// add the record into the return JSON
					cList.add(c);
				}
			} catch (Exception e) {
				errorMessages.append("Exception in saving siteDeleteChoice siteId=" + siteId + " " + e.getMessage());
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
	 * 1. to not migrate a tool:
	 * /doNotMigrateTool?siteId=<site_id>&toolId=<tool_id>&toolName=<tool_name>
	 * 
	 * 2. to reset the previous setting:
	 * /doNotMigrateTool?siteId=<site_id>&toolId=<tool_id>&toolName=<tool_name>&reset=true
	 * 
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

		StringBuffer errorMessages = new StringBuffer();
		
		// we need to do series checks to make sure the migration request is valid
		// 1. check if missing or more than one params for siteId, or toolId
		Map<String, String[]> parameterMap = request.getParameterMap();
		String[] siteIds = parameterMap.get("siteId");
		if (siteIds == null || siteIds.length != 1) {
			errorMessages.append("doNotMigrateToolChoice request missing or multiple required parameter: siteId");
		}
		// check if missing or more than one toolId
		String[] toolIds = parameterMap.get("toolId");
		if (toolIds == null || toolIds.length != 1) {
			errorMessages.append("doNotMigrateToolChoice request missing or multiple required parameter: toolId");
		}
		
		// check if missing or more than one toolName
		String[] toolTypes = parameterMap.get("toolType");
		if (toolTypes == null || toolTypes.length != 1) {
			errorMessages.append("doNotMigrateToolChoice request missing or multiple required parameter: toolType");
		}
		
		// get the reset param if there is any
		boolean reset = false;
		String[] resetParam = parameterMap.get("reset");
		if (resetParam != null) { 
			if (resetParam.length != 1) {
				errorMessages.append("doNotMigrateToolChoice request has multiple value for parameter: reset");
			}
			else
			{
				if (!"true".equals(resetParam[0]))
				{
					errorMessages.append("doNotMigrateToolChoice request has wrong value " + resetParam[0] + " for parameter: reset");
				}
				else
				{
					reset = true;
				}
			}
		}
		
		if (errorMessages.length() > 0)
		{
			// return if there is error in call parameters
			return new ResponseEntity<String>(errorMessages.toString(), headers, HttpStatus.BAD_REQUEST);
		}
		
		// the target site id and tool id
		String siteId = siteIds[0];
		String toolId = toolIds[0];
		String toolType = toolTypes[0];
		log.info("request set tool exemption for site " + siteId + ", toolId " + toolId + " and toolType " + toolType);

		try {
			if (reset) {
				// remove the tool exempt record from database
				tRepository.deleteSiteToolExemptChoice(siteId, toolId);
			}
			else {
				// save the choices into database
				SiteToolExemptChoice c = new SiteToolExemptChoice(siteId, toolId, toolType, userId, 
						new java.sql.Timestamp(System.currentTimeMillis()));
					c = tRepository.save(c);
			}
		} catch (Exception e) {
			errorMessages.append("Exception in saving siteToolExcemptChoice siteId = " + siteId + " toolId=" + toolId + " toolType=" + toolType  + " " + e.getMessage());
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

		Map<String, String[]> parameterMap = request.getParameterMap();
		// 1. get the siteId request parameter
		String[] siteIds = parameterMap.get("siteId");
		if (siteIds == null || siteIds.length != 1) {
			String errorMessage = "siteToolNotMigrate request missing or multiple required parameter: siteId";
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(errorMessage).build();
		}
		String siteId = siteIds[0];
		// 2. get the tool id request parameter
		String[] toolIds = parameterMap.get("toolId");
		if (toolIds == null || toolIds.length != 1) {
			String errorMessage = "siteToolNotMigrate request missing or multiple required parameter: toolId";
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity(errorMessage).build();
		}
		String toolId = toolIds[0];

		// 3. get the SiteToolExemptChoice record from database for this site
		try {
			return Response.status(Response.Status.OK)
					.entity(tRepository.findSiteToolExemptChoiceForSite(siteId, toolId)).build();
		} catch (Exception e) {
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Problem getting SiteToolExemptionChoice for site id = " + siteId
							+ " and toolId = " + ": " + e.getMessage()).build();
		}

	}

	/**************** zip download of Mail Archive content ***************/
	/**
	 * insert a new record of Migration
	 *
	 * @param request
	 */

	@GET
	@Produces("application/zip")
	@RequestMapping(value = "/migrationMailArchiveZip")
	@ResponseBody
    public void migrationMailArchiveZip(HttpServletRequest request,
                                        HttpServletResponse response) {

        // zip download
        Map<String, String[]> parameterMap = request.getParameterMap();
        String destination_type = parameterMap.get("destination_type")[0];

        if (Utils.isItMailArchiveZip(destination_type)) {
            migrationCallForMailArchive(request, response, Utils.MIGRATION_MAILARCHIVE_TYPE_ZIP);
        } else if (Utils.isItMailArchiveMbox(destination_type)) {
            migrationCallForMailArchive(request, response, Utils.MIGRATION_MAILARCHIVE_TYPE_MBOX);
        }

    }

    private void migrationCallForMailArchive
            (HttpServletRequest request, HttpServletResponse response, String migrationType) {
        log.info("The call to migrationMailArchive = " + migrationType);
        HashMap<String, String> callStatus = migration_call(request, response,
                migrationType, Utils.getCurrentUserId(request, env));
        if (callStatus.containsKey("errorMessage")) {
            log.info(this + migrationType + "call error message="
                    + callStatus.get("errorMessage"));
        } else if (callStatus.containsKey("migrationId")) {
            log.info(this + migrationType + " call migration started id="
                    + callStatus.get("migrationId"));
        }
    }

	@Override
	public String getErrorPath() {
		return PATH;
	}

	@RequestMapping(value = PATH)
	public ErrorJson error(HttpServletRequest request, HttpServletResponse response) {
		// Appropriate HTTP response code (e.g. 404 or 500) is automatically set by Spring.
		// Here we just define response body.
		response.setStatus(response.getStatus());
		return new ErrorJson(response.getStatus(), getErrorAttributes(request));
	}

	private Map<String, Object> getErrorAttributes(HttpServletRequest request) {
		RequestAttributes requestAttributes = new ServletRequestAttributes(request);
		// no need to include stack trace hence setting it to false
		return errorAttributes.getErrorAttributes(requestAttributes, false);
	}
}
