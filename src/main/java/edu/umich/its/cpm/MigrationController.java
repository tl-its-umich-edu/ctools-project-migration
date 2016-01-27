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

import java.util.Base64;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.util.Date;
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
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.context.annotation.PropertySource;
import org.json.JSONObject;
import org.json.JSONArray;

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

//import org.apache.axis.encoding.XMLType;
//import javax.xml.rpc.ParameterMode;
//import org.apache.axis.client.Call;
//import org.apache.axis.client.Service;

@PropertySource("file:${catalina.base:/usr/local/ctools/app/ctools/tl}/home/application.properties")
@RestController
public class MigrationController {

	private static final String BOX_AUTHORIZED_HTML = "<link rel=\"stylesheet\" href=\"vendors/bootstrap/bootstrap.min.css\"><div class=\"jumbotron\"><h1 role=\"alert\">Authorized!</h1><p>You can now select a Box folder and migrate project sites to it.</p><p><a target=\"top\" href=\"/\">Go back</a></p></div>";
	private static final String BOX_UNAUTHORIZED_HTML = "<link rel=\"stylesheet\" href=\"vendors/bootstrap/bootstrap.min.css\"><div class=\"jumbotron\"><h1 role=\"alert\">Unauthorized!</h1><p>You need to authorize Box.<a target=\"_top\" href=\"/\">Go back</a></p></div>";

	private static final Logger log = LoggerFactory
			.getLogger(MigrationController.class);

	@Autowired
	MigrationRepository repository;

	@Autowired
	private Environment env;

	@Context
	// injected response proxy supporting multiple threads
	private HttpServletResponse response;

	@Context
	// injected request proxy supporting multiple threads
	private HttpServletRequest request;

	@Autowired private MigrationService migrationService;
	
	// WSDL operation
	private static final String OPERATION_GET_CONTENT_DATA = "getContentData";
	private static final String OPERTAION_GET_CONTENT_DATA_PARAM_SESSIONID = "sessionid";
	private static final String OPERTAION_GET_CONTENT_DATA_PARAM_RESOURCEID = "resourceId";

	/**
	 * get all CTools sites where user have site.upd permission
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/projects")
	public void getProjectSites(HttpServletRequest request,
			HttpServletResponse response) {
		String rv = null;
		String errorMessage = null;

		// login to CTools and get sessionId
		HashMap<String, Object> sessionAttributes = Utils.login_becomeuser(request);
		if (sessionAttributes.containsKey("sessionId")) {
			String sessionId = (String) sessionAttributes.get("sessionId");
			
			// get all sites that user have permission site.upd
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/withPerm/.json?permission=site.upd"
			String requestUrl = env.getProperty("ctools.server.url")
					+ "direct/site/withPerm/.json?permission=site.upd&_sessionId="
					+ sessionId;
			try {
				rv = restTemplate.getForObject(requestUrl, String.class);
			} catch (RestClientException e) {
				errorMessage = requestUrl + e.getMessage();
				log.error(errorMessage);
			}

			// JSON response
			JSON_response(response, rv, errorMessage, requestUrl);
		}
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
		String rv = null;
		String errorMessage = null;

		// login to CTools and get sessionId
		HashMap<String, Object> sessionAttributes = Utils.login_becomeuser(request);
		if (sessionAttributes.containsKey("sessionId")) {
			String sessionId = (String) sessionAttributes.get("sessionId");
			
			// 3. get all sites that user have permission site.upd
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/SITE_ID.json"
			String requestUrl = env.getProperty("ctools.server.url")
					+ "direct/site/" + site_id + "/pages.json?_sessionId="
					+ sessionId;

			try {
				rv = restTemplate.getForObject(requestUrl, String.class);
			} catch (RestClientException e) {
				errorMessage = "Cannot find site by siteId: " + site_id + " "
						+ e.getMessage();
				log.error(errorMessage);
			}

			// JSON response
			JSON_response(response, rv, errorMessage, requestUrl);
		}
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
		String userId = "zqian";
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
			String userId = "zqian";
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
		String userId = "zqian";
		try {
			List<Migration> l = repository.findMigrated(userId);
			log.info("===== l size=" + l.size());
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
		String userId = "zqian";
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
	public Response migrationZip(HttpServletRequest request,
			HttpServletResponse response) {

		// zip download
		return migration_call(request, response, "zip");
	}
	
	/**
	 * handle migration request
	 * @param target migration target
	 */
	private Response migration_call(HttpServletRequest request, HttpServletResponse response, String target)
	{
		String currentUserId = "zqian";
		
		// save migration record into database
		HashMap<String, Object> saveMigration = saveMigrationRecord(request);
		Migration newMigration = null;

		Map<String, String[]> parameterMap = request.getParameterMap();
		String siteId = parameterMap.get("site_id")[0];
		
		// exit if there is no new Migration record saved into DB
		if (!saveMigration.containsKey("migration")) {
			
			// no new Migration record created
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot create migration records for user " + currentUserId + " and site=" + siteId).build();
		} else {
			Migration migration = (Migration) saveMigration.get("migration");
			String migrationId = migration.getMigration_id();
			try
			{
				Future<String> migrationStatus = null;
				
				if ("zip".equals(target))
				{
					// call asynchronous method for zip file download
					migrationStatus = migrationService.asyncDownloadZip(request, response, siteId, migrationId);
				}
				else if ("box".equals(target))
				{
					// call asynchronous method for Box file upload
					migrationStatus =migrationService.asyncUploadBox(request, response, migration.getMigration_id());
				}
				// update the status and end_time of migration record
				repository.setMigrationEndTime(
						new java.sql.Timestamp(System.currentTimeMillis()),
						migrationId);
				repository.setMigrationStatus(migrationStatus.get(),
						migrationId);
			}
			catch (java.util.concurrent.ExecutionException e)
			{
				log.error(e.getMessage() + " migration error for user " + currentUserId + " and site=" + siteId  + " target=" + target);
			}
			catch (java.lang.InterruptedException e)
			{
				log.error(e.getMessage() + " migration error for user " + currentUserId + " and site=" + siteId  + " target=" + target);
			}
			
			return Response.status(Response.Status.OK)
					.entity(saveMigration).build();
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
		String userId = "zqian";

		String boxClientId = env.getProperty(Utils.BOX_CLIENT_ID);
		String boxClientSecret = env.getProperty(Utils.BOX_CLIENT_SECRET);
		String boxAPIUrl = env.getProperty(Utils.BOX_API_URL);
		String boxClientRedirectUrl = env.getProperty(Utils.BOX_CLIENT_REDIRECT_URL)
				+ "/authorized";

		// need to have all Box app configurations
		if (boxClientId == null || boxClientSecret == null
				|| boxClientRedirectUrl == null) {
			log.error("Missing box integration parameters");
			return null;
		}
		String remoteUserEmail = Utils.getUserEmail(userId);

		if (BoxUtils.getBoxAccessToken(userId) == null) {
			// go to Box authentication screen
			// get access token and refresh token and store locally
			BoxUtils.authenticate(boxAPIUrl, boxClientId, boxClientRedirectUrl,
					remoteUserEmail, response);
		} else {
			// get box folders json
			return BoxUtils.getBoxFolders(userId, boxClientId, boxClientSecret);
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
		// get the current user id
		String userId = "zqian";
		String remoteUserEmail = Utils.getUserEmail(userId);

		String boxClientId = env.getProperty(Utils.BOX_CLIENT_ID);
		String boxClientSecret = env.getProperty(Utils.BOX_CLIENT_SECRET);
		String boxAPIUrl = env.getProperty(Utils.BOX_API_URL);
		String boxClientRedirectUrl = env.getProperty(Utils.BOX_CLIENT_REDIRECT_URL)
				+ "/authorized";

		log.info("in /box/authorize");

		if (BoxUtils.getBoxAccessToken(userId) == null) {
			log.info("user " + userId
					+ " has not authorized to use Box. Start auth process.");
			// go to Box authentication screen
			// get access token and refresh token and store locally
			return BoxUtils.authenticateString(boxAPIUrl, boxClientId,
					boxClientRedirectUrl, remoteUserEmail, response);
		} else {
			log.info("user " + userId + " already authorized");
			return "Authorized";
		}
	}

	/**
	 * get json string of box folders
	 * 
	 * @return
	 */
	@RequestMapping("/box/unauthorize")
	public Response unauthorizeBox(HttpServletRequest request,
			HttpServletResponse response) {

		// get the current user id
		String userId = "zqian";

		// the return string
		String rv = "";

		// check whether the user authentication token is store in memory
		if (BoxUtils.getBoxAccessToken(userId) == null) {
			rv = "Cannot find user's Box authentication info. ";
		} else {
			BoxUtils.removeBoxAccessToken(userId);
			rv = "User authentication info is removed. ";
		}

		log.info("/box/unauthorize for user " + userId + " " + rv);
		try {
			return Response.status(Response.Status.OK).entity(rv).build();
		} catch (Exception e) {
			return Response
					.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot remove box authentication info for user "
							+ userId + ": " + e.getMessage()).build();
		}
	}

	@RequestMapping("/authorized")
	@Produces(MediaType.APPLICATION_JSON)
	public String getBoxAuthzTokens(HttpServletRequest request) {

		String boxClientId = env.getProperty(Utils.BOX_CLIENT_ID);
		String boxClientSecret = env.getProperty(Utils.BOX_CLIENT_SECRET);
		String boxAPIUrl = env.getProperty(Utils.BOX_API_URL);
		String boxTokenUrl = env.getProperty(Utils.BOX_TOKEN_URL);
		log.info("token url=" + boxTokenUrl);

		// get the current user id
		String userId = "zqian";
		String rv = BoxUtils.getBoxAccessToken(userId);

		if (rv == null) {
			// get the authCode,
			// and get access token and refresh token subsequently
			BoxUtils.getAuthCodeFromBoxCallback(request, boxClientId,
					boxClientSecret, boxTokenUrl, userId);

			// try to get the access token after parsing the request string
			rv = BoxUtils.getBoxAccessToken(userId);
		}

		return rv != null ? BOX_AUTHORIZED_HTML : BOX_UNAUTHORIZED_HTML;
	}

	@RequestMapping("/box/checkAuthorized")
	@Produces(MediaType.APPLICATION_JSON)
	public Boolean boxCheckAuthorized(HttpServletRequest request) {

		// get the current user id
		String userId = "zqian";
		return Boolean.valueOf(BoxUtils.getBoxAccessToken(userId) != null);
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
		String userId = "zqian";

		Migration m = new Migration(siteId, siteName, toolId, toolName, userId,
				new java.sql.Timestamp(System.currentTimeMillis()), // start
																	// time is
																	// now
				null, destinationType, null, "" /* status */);

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
	@RequestMapping("/migrationBox")
	public Response migrationBox(HttpServletRequest request,
			HttpServletResponse response) {

		// box upload
		return migration_call(request, response, "box");
	}
}
