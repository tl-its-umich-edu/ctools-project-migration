package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.io.PrintWriter;
import java.io.IOException;

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

import org.json.JSONObject;
import org.json.JSONArray;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxItem;
import com.box.sdk.BoxItem.Info;
import com.box.sdk.BoxUser;

import com.google.gson.Gson;

@RestController
public class MigrationController {
	
	private static final String BOX_CLIENT_ID = "box_client_id";
	private static final String BOX_CLIENT_SECRET = "box_client_secret";
	private static final String BOX_CLIENT_REDIRECT_URL = "box_client_redirect_uri";

	private static final Logger log = LoggerFactory
			.getLogger(MigrationController.class);

	private final AtomicLong counter = new AtomicLong();

	@Autowired
	MigrationRepository repository;

	@Autowired
	private Environment env;
	
	@Context  //injected response proxy supporting multiple threads
	private HttpServletResponse response;
	
	@Context  //injected request proxy supporting multiple threads
	private HttpServletRequest request;

	/**
	 * get all CTools sites where user have site.upd permission
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/projects")
	public void getProjectSites(HttpServletResponse response) {
		String rv = null;
		String errorMessage = null;

		// login to CTools and get sessionId
		String sessionId = login_becomeuser(request);
		if (sessionId != null) {
			// get all sites that user have permission site.upd
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/withPerm/.json?permission=site.upd"
			String requestUrl = env.getProperty("ctools.direct.url")
					+ "site/withPerm/.json?permission=site.upd&_sessionId="
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
	public void getProjectSitePages(@PathVariable String site_id, HttpServletResponse response) {
		String rv = null;
		String errorMessage = null;

		// login to CTools and get sessionId
		String sessionId = login_becomeuser(request);
		if (sessionId != null) {
			// 3. get all sites that user have permission site.upd
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/SITE_ID.json"
			String requestUrl = env.getProperty("ctools.direct.url") + "site/"
					+ site_id + "/pages.json?_sessionId=" + sessionId;

			try {
				rv = restTemplate.getForObject(requestUrl, String.class);
			} catch (RestClientException e) {
				errorMessage = "Cannot find site by siteId: " + site_id + " " + e.getMessage();
				log.error(errorMessage);
			}
			
			// JSON response
			JSON_response(response, rv, errorMessage, requestUrl);
		}
	}

	/**
	 * login into CTools and become user with sessionId
	 */
	private String login_becomeuser(HttpServletRequest request) {
		// return the session id after login
		String sessionId = "";

		String remoteUser = request.getRemoteUser();
		log.info("remote user is " + remoteUser);
		// here is the CTools integration prior to CoSign integration ( read
		// session user information from configuration file)
		// 1. create a session based on user id and password
		RestTemplate restTemplate = new RestTemplate();
		// the url should be in the format of
		// "https://server/direct/session?_username=USERNAME&_password=PASSWORD"
		String requestUrl = env.getProperty("ctools.direct.url")
				+ "session?_username=" + env.getProperty("username")
				+ "&_password=" + env.getProperty("password");
		log.info(requestUrl);
		ResponseEntity<String> response = restTemplate.postForEntity(
				requestUrl, null, String.class);
		HttpStatus status = response.getStatusCode();
		if (!status.equals(HttpStatus.CREATED)) {
			// return error if a new CTools session could not be created using
			// username and password provided
			log.info("Wrong user id or password. Cannot login to CTools "
					+ env.getProperty("ctools.direct.url"));
		} else {
			// get the session id
			sessionId = response.getBody();
			log.info("successfully logged in as user "
					+ env.getProperty("username") + " with sessionId = "
					+ sessionId);

			// 2. become the user based on REMOTE_USER setting after CoSign
			// integration
			restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/session/SESSION_ID.json"
			requestUrl = env.getProperty("ctools.direct.url")
					+ "session/becomeuser/" + remoteUser + ".json?_sessionId="
					+ sessionId;
			log.info(requestUrl);

			try {
				String resultString = restTemplate.getForObject(requestUrl,
						String.class, sessionId);
				log.info(resultString);
			} catch (RestClientException e) {
				log.error(requestUrl + e.getMessage());

				// nullify sessionId if become user call is not successful
				sessionId = null;
			}
		}
		return sessionId;
	}

	/**
	 * get all migration records
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/migrations")
	@Produces(MediaType.APPLICATION_JSON)
	public Response migrations() {
		try
		{
			return Response.status(Response.Status.OK).entity(repository.findAll()).build();
		}
		catch (Exception e)
		{
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Cannot get migration records" + e.getMessage()).build();
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
	public Response migrations(@PathVariable("migration_id") String migration_id) {
		Object o = repository.findOne(migration_id);
		String rv = "";
		if (o == null) {
			// no such migration record
			throw new MigrationNotFoundException("no matching record for /migrations/" + migration_id);
		}
		// find migration record with id
		return Response.status(Response.Status.OK).entity((Migration) o).build();
	}

	/**
	 * found all migrated records (where the migration record have "end" field
	 * value
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/migrated")
	@Produces(MediaType.APPLICATION_JSON)
	public List<Migration> migrated() {
		return repository.findMigrated();
	}

	/**
	 * insert a new record of Migration
	 * 
	 * @param request
	 */
	@POST
	@RequestMapping(value = "/migration")
	@Produces(MediaType.APPLICATION_JSON)
	public void migration(HttpServletRequest request, HttpServletResponse response) {
		Map<String, String[]> parameterMap = request.getParameterMap();
		Migration m = new Migration(
				parameterMap.get("site_id")[0],
				parameterMap.get("site_name")[0],
				parameterMap.get("tool_id")[0],
				parameterMap.get("tool_name")[0], 
				request.getRemoteUser(),
				new java.sql.Timestamp(System.currentTimeMillis()), // start time is now
				null, // no end time
				parameterMap.get("destination_type")[0], null);
		
		Migration newMigration = null;
		
		StringBuffer insertMigrationDetails = new StringBuffer();
		insertMigrationDetails.append("Save migration record site_id=")
				.append(parameterMap.get("site_id")[0])
				.append(" site_name=")
				.append(parameterMap.get("site_name")[0])
				.append(" tool_id=").append(parameterMap.get("tool_id")[0])
				.append(" tool_name=")
				.append(parameterMap.get("tool_name")[0])
				.append(" migrated_by=").append(request.getRemoteUser())
				.append(" destination_type=")
				.append(parameterMap.get("destination_type")[0])
				.append(" \n ");
		try {
			newMigration = repository.save(m);
		} catch (Exception e) {
			log.error("Exception " + insertMigrationDetails.toString() + e.getMessage());
		}
		
	    try {
	    	if (newMigration != null)
	    	{
		    	// new Migration record created
	    		// set HTTP code to "201 Created"
			    response.setStatus(HttpServletResponse.SC_CREATED);
				response.getWriter().println(new JSONObject().put("migration", newMigration).toString());
	    	}
	    	else
	    	{
	    		// no new Migration record created
	    		response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				response.getWriter().println(new JSONObject().put("error", insertMigrationDetails.toString()).toString());
	    	}
	        response.flushBuffer();
	        response.getWriter().close();
	    }catch(Exception e){
	    	Response.status(HttpServletResponse.SC_INTERNAL_SERVER_ERROR).entity("Cannot insert migration record " + insertMigrationDetails.toString() + e.getMessage()).build();
	    }
	}

	/**
	 * generate output for JSON_ready input value
	 */
	private void JSON_response(HttpServletResponse response, String jsonValue, String errorMessage, String requestUrl)
	{
		try
		{
			// output json
			response.setContentType(MediaType.APPLICATION_JSON);
			if (jsonValue == null)
			{
				response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
				response.getWriter().write(errorMessage);
			}
			else
			{
				response.setStatus(HttpServletResponse.SC_OK);
				response.getWriter().write(jsonValue);
			}
		    response.getWriter().close();
		}catch (IOException e) {
			log.error(requestUrl + e.getMessage());
		}
	}
	
	/************* Box integration *****************/
	/**
	 * get json string og box folders
	 * 
	 * @return
	 */
	@RequestMapping("/box/folders")
	public void getBoxFolders(HttpServletRequest request, HttpServletResponse response) {
		
		String boxClientId = env.getProperty(BOX_CLIENT_ID);
		String boxClientSecret = env.getProperty(BOX_CLIENT_SECRET);
		String boxClientRedirectUri = env.getProperty(BOX_CLIENT_REDIRECT_URL) + "/box/folders_authorized";
		
		// need to have all Box app configurations
		if (boxClientId == null || boxClientSecret == null || boxClientRedirectUri == null)
		{
			log.error("Missing box integration parameters");
			return;
		}
		String remoteUserEmail = request.getRemoteUser();
		
		// go to authentication screen
		String boxAPIUrl = env.getProperty("box_api_url");
		BoxUtils.authenticate(boxAPIUrl, boxClientId, boxClientRedirectUri, remoteUserEmail, response);
	}
	
	@RequestMapping("/box/folders_authorized")
	@Produces(MediaType.APPLICATION_JSON)
	public List<HashMap<String, String>> getBoxFoldersAuthorized(HttpServletRequest request) {
		String rv = "";
		// get the authCode
		String authCode = BoxUtils.getAuthCodeFromBoxCallback(request);
		String boxClientId = env.getProperty(BOX_CLIENT_ID);
		String boxClientSecret = env.getProperty(BOX_CLIENT_SECRET);
		
		if (boxClientId == null || boxClientSecret == null || authCode == null)
		{
			log.error("Missing box integration parameters " );
			return null;
		}
		
		// make connection
		BoxAPIConnection api = new BoxAPIConnection(boxClientId,
				boxClientSecret, authCode);
		BoxUser.Info userInfo = BoxUser.getCurrentUser(api).getInfo();
		log.info("Box user log in success: user name = " + userInfo.getName()
				+ " user login = " + userInfo.getLogin());

		// get the root Box folder
		BoxFolder rootFolder = BoxFolder.getRootFolder(api);

		// get list of properties from all Box items contained
		List<HashMap<String, String>> folderItems = BoxUtils.listBoxFolders(null, api, rootFolder, "", 0);
		
		return folderItems;
	}
}
