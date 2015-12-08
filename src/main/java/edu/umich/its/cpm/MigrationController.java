package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import org.apache.axis.encoding.XMLType;
import javax.xml.rpc.ParameterMode;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;

@PropertySource("file:${catalina.base:/usr/local/ctools/app/ctools/tl}/home/application.properties")
@RestController
public class MigrationController {

	private static final String BOX_CLIENT_ID = "box_client_id";
	private static final String BOX_CLIENT_SECRET = "box_client_secret";
	private static final String BOX_API_URL = "box_api_url";
	private static final String BOX_TOKEN_URL = "box_token_url";
	private static final String BOX_CLIENT_REDIRECT_URL = "box_client_redirect_uri";

	private static final String CTOOLS_ACCESS_STRING = "/access/content";
	private static final String CTOOLS_CONTENT_STRING = "/content";

	private static final String COLLECTION_TYPE = "collection";

	// the at sign used in email address
	private static final String EMAIL_AT = "@";
	private static final String EMAIL_AT_UMICH = "@umich.edu";

	// integer value of stream operation buffer size
	private static final int STREAM_BUFFER_CHAR_SIZE = 1024;

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

	// WSDL operation
	private static final String OPERATION_GET_CONTENT_DATA = "getContentData";
	private static final String OPERTAION_GET_CONTENT_DATA_PARAM_SESSIONID = "sessionid";
	private static final String OPERTAION_GET_CONTENT_DATA_PARAM_RESOURCEID = "resourceId";

	// String values used in content json feed
	private static final String CONTENT_JSON_ATTR_CONTENT_COLLECTION = "content_collection";
	private static final String CONTENT_JSON_ATTR_CONTAINER = "container";
	private static final String CONTENT_JSON_ATTR_TITLE = "title";
	private static final String CONTENT_JSON_ATTR_TYPE = "type";
	private static final String CONTENT_JSON_ATTR_URL = "url";
	private static final String CONTENT_JSON_ATTR_DESCRIPTION = "description";
	private static final String CONTENT_JSON_ATTR_AUTHOR = "author";

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
		String sessionId = login_becomeuser(request);
		if (sessionId != null) {
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
		String sessionId = login_becomeuser(request);
		if (sessionId != null) {
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
		String requestUrl = env.getProperty("ctools.server.url")
				+ "direct/session?_username=" + env.getProperty("username")
				+ "&_password=" + env.getProperty("password");
		log.info(requestUrl);
		ResponseEntity<String> response = restTemplate.postForEntity(
				requestUrl, null, String.class);
		HttpStatus status = response.getStatusCode();
		if (!status.equals(HttpStatus.CREATED)) {
			// return error if a new CTools session could not be created using
			// username and password provided
			log.info("Wrong user id or password. Cannot login to CTools "
					+ env.getProperty("ctools.server.url"));
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
			requestUrl = env.getProperty("ctools.server.url")
					+ "direct/session/becomeuser/" + remoteUser
					+ ".json?_sessionId=" + sessionId;
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
	public Response migrations(HttpServletRequest request) {
		String userId = request.getRemoteUser();
		try {
			return Response.status(Response.Status.OK)
					.entity(repository.findMigrations(userId)).build();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot get migration records for user " + userId + ": " + e.getMessage())
					.build();
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
	public Response migrations(@PathVariable("migration_id") String migration_id, HttpServletRequest request) {
		Object o = repository.findOne(migration_id);
		String rv = "";
		if (o == null) {
			// no such migration record
			throw new MigrationNotFoundException(
					"no matching record for /migrations/" + migration_id);
		}
		else
		{
			String userId = request.getRemoteUser();
			String migratedBy = ((Migration) o).getMigrated_by();
			if (!migratedBy.equals(userId))
			{
				// different user started the migration
				throw new MigrationNotFoundException(
						"record for /migrations/" + migration_id + " was done by user id=" + migratedBy + " , instead of current user " + userId);
			}
			else
			{
				// find migration record with id
				return Response.status(Response.Status.OK).entity((Migration) o)
						.build();
			}
			
		}
	}

	/**
	 * found all migrated records (where the migration record have "end_time" field
	 * value
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/migrated")
	@Produces(MediaType.APPLICATION_JSON)
	public Response migrated(HttpServletRequest request) {
		String userId = request.getRemoteUser();
		try {
			return Response.status(Response.Status.OK)
					.entity(repository.findMigrated(userId)).build();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot get migrated records for user " + userId + ": " + e.getMessage())
					.build();
		}
	}

	/**
	 * found all migrating records (where the migration record have NO "end_time" field)
	 * value
	 * 
	 * @return
	 */
	@GET
	@RequestMapping("/migrating")
	@Produces(MediaType.APPLICATION_JSON)
	public Response migrating(HttpServletRequest request) {
		String userId = request.getRemoteUser();
		try {
			return Response.status(Response.Status.OK)
					.entity(repository.findMigrating(userId)).build();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot get migrating records for user " + userId + ": " + e.getMessage())
					.build();
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
	public void migrationZip(HttpServletRequest request,
			HttpServletResponse response) {
		// the status of migration
		StringBuffer statusBuffer = new StringBuffer();
		
		// save migration record into database
		HashMap<String, Object> saveMigration = saveMigrationRecord(request);
		Migration newMigration = null;
		statusBuffer.append(saveMigration.get("status"));
		
		// exit if there is no new Migration record saved into DB
		if (!saveMigration.containsKey("migration")) {
			// no new Migration record created
			return;
		}
		else
		{
			newMigration = (Migration) saveMigration.get("migration");
		}
		
		log.info("migration", newMigration);

		// download zip file
		Map<String, String[]> parameterMap = request.getParameterMap();
		String siteId = parameterMap.get("site_id")[0];
		String downloadStatus = downloadZippedFile(request, response, siteId);
		// get status
		statusBuffer.append(downloadStatus + "\n");

		// update the status and end_time of migration record
		repository.setMigrationEndTime(
				new java.sql.Timestamp(System.currentTimeMillis()),
				newMigration.getMigration_id());
		repository.setMigrationStatus(statusBuffer.toString(),
				newMigration.getMigration_id());
	}

	/**
	 * Download CTools site resource in zip file
	 * @return status of download
	 */
	private String downloadZippedFile(HttpServletRequest request,
			HttpServletResponse response, String site_id) {
		// hold download status
		StringBuffer downloadStatus = new StringBuffer();
		
		// login to CTools and get sessionId
		String sessionId = login_becomeuser(request);
		log.info(sessionId);
		if (sessionId != null) {
			// 3. get all sites that user have permission site.upd
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/SITE_ID.json"
			String requestUrl = env.getProperty("ctools.server.url")
					+ "direct/content/site/" + site_id + ".json?_sessionId="
					+ sessionId;
			String siteResourceJson = null;
			try {
				siteResourceJson = restTemplate.getForObject(requestUrl,
						String.class);

				// null zip content
				byte[] zipContent = null;

				log.info(":downloadZippedFile begin: start downloading content zip file for site "
						+ site_id);
				try {
					ByteArrayOutputStream baos = new ByteArrayOutputStream();
					ZipOutputStream out = new ZipOutputStream(baos);

					// prepare zip entry for site content objects
					String zipStatus = zipSiteContent(siteResourceJson, sessionId, out);
					log.info(zipStatus);
					downloadStatus.append(zipStatus + "\n");

					out.flush();
					baos.flush();
					out.close();
					baos.close();
					zipContent = baos.toByteArray();
				} catch (IOException ee) {
					String eeString = "downloadZippedFile: IOException of constructing zip file.";
					log.warn(eeString);
					downloadStatus.append(eeString + "\n");
				}

				if (zipContent != null) {
					//
					// Sends the response back to the user / browser. The
					// content for zip file type is "application/zip". We
					// also set the content disposition as attachment for
					// the browser to show a dialog that will let user
					// choose what action will he do to the sent content.
					//
					ServletOutputStream sos = response.getOutputStream();
					response.setContentType("application/zip");
					String zipFileName = site_id + "_content.zip";
					response.setHeader("Content-Disposition",
							"attachment;filename=\"" + zipFileName + "\"");

					sos.write(zipContent);
					sos.flush();

					String downloadEndSuccess = "downloadZippedFile end: successfully download zip file for site "
							+ site_id;
					log.info(downloadEndSuccess);
					downloadStatus.append(downloadEndSuccess + "\n");
				} else {
					String noContent = site_id + " has no content to download."; 
					log.error(noContent);
					downloadStatus.append(noContent + "\n");
				}

			} catch (RestClientException e) {
				String errorMessage = "Cannot find site by siteId: " + site_id
						+ " " + e.getMessage();
				Response.status(Response.Status.NOT_FOUND).entity(errorMessage)
						.type(MediaType.TEXT_PLAIN).build();
				log.error(errorMessage);
				downloadStatus.append(errorMessage + "\n");
			} catch (IOException e) {
				String errorMessage = "Problem getting content zip file for "
						+ site_id + " " + e.getMessage();
				Response.status(Response.Status.INTERNAL_SERVER_ERROR)
						.entity(errorMessage).type(MediaType.TEXT_PLAIN)
						.build();
				log.error(errorMessage);
				downloadStatus.append(errorMessage + "\n");
				
			}
		}
		else
		{
			String userError = "Cannot become user " + request.getRemoteUser();
			log.error(userError);
			downloadStatus.append(userError + "\n");
		}
		return downloadStatus.toString();
	}

	/**
	 * create zip entry for folders and files
	 */
	private String zipSiteContent(String siteResourceJson, String sessionId,
			ZipOutputStream out) {
		// zip status information
		StringBuffer zipStatus = new StringBuffer();
		
		// site root folder
		String rootFolderPath = null;

		JSONObject obj = new JSONObject(siteResourceJson);

		JSONArray array = obj
				.getJSONArray(CONTENT_JSON_ATTR_CONTENT_COLLECTION);

		for (int i = 0; i < array.length(); i++) {
			JSONObject contentItem = array.getJSONObject(i);

			// get only the url after "/access/" string
			String contentUrl = URLDecoder.decode(contentItem
					.getString(CONTENT_JSON_ATTR_URL));
			contentUrl = contentUrl.substring(contentUrl
					.indexOf(CTOOLS_ACCESS_STRING)
					+ CTOOLS_ACCESS_STRING.length());

			// inside the JSON feed, the container string is of format
			// /content/<folder_url>
			// remote the prefix "/content"
			String container = URLDecoder.decode(contentItem
					.getString(CONTENT_JSON_ATTR_CONTAINER));

			String type = contentItem.getString(CONTENT_JSON_ATTR_TYPE);
			String title = contentItem.getString(CONTENT_JSON_ATTR_TITLE);

			// files
			if (contentUrl == null && contentUrl.length() == 0) {
				// log error if the content url is missing
				String noUrlError = "No url for content " + title;
				log.error(noUrlError);
				zipStatus.append(noUrlError + "\n");
				break;
			} else if (container == null && container.length() == 0) {
				// log error if the content url is missing
				String noContainerError = "No container folder url for content " + title;
				log.error(noContainerError);
				zipStatus.append(noContainerError + "\n");
				break;
			}

			if (COLLECTION_TYPE.equals(type)) {
				// folders
				if (rootFolderPath == null) {
					rootFolderPath = contentUrl;
				} else {
					// create the zipentry for the sub-folder first
					String folderName = contentUrl.replace(rootFolderPath, "");
					ZipEntry folderEntry = new ZipEntry(folderName);
					try {
						out.putNextEntry(folderEntry);
					} catch (IOException e) {
						String ioError = "zipSiteContent: problem closing zip entry "
								+ folderName + " " + e;
						log.error(ioError);
						zipStatus.append(ioError + "\n");
					}
				}

			} else {
				// Call the zipFiles method for creating a zip stream.
				//
				String filePath = contentUrl.replace(rootFolderPath, "");
				String zipFileStatus = zipFiles(filePath, contentUrl, sessionId, out);
				zipStatus.append(zipFileStatus + "\n");
			}
		} // for
		return zipStatus.toString();
	}

	/**
	 * create zip entry for files
	 */
	private String zipFiles(String fileName, String fileUrl, String sessionId,
			ZipOutputStream out) {
		// record zip status
		StringBuffer zipFileStatus = new StringBuffer();
		
		String contentString = "";
		try {
			Service service = new Service();
			Call nc = (Call) service.createCall();

			nc.setTargetEndpointAddress(env.getProperty("ctools.server.url")
					+ "sakai-axis/ContentHosting.jws");

			nc.removeAllParameters();
			nc.setOperationName(OPERATION_GET_CONTENT_DATA);
			nc.addParameter(OPERTAION_GET_CONTENT_DATA_PARAM_SESSIONID,
					XMLType.XSD_STRING, ParameterMode.IN);
			nc.addParameter(OPERTAION_GET_CONTENT_DATA_PARAM_RESOURCEID,
					XMLType.XSD_STRING, ParameterMode.IN);
			nc.setReturnType(XMLType.XSD_STRING);
			contentString = (String) nc.invoke(new Object[] { sessionId,
					fileUrl });

		} catch (Exception e) {
			String exceptionString = "zipFiles " + fileUrl + " " + e.getMessage();
			log.error(exceptionString);
			zipFileStatus.append(exceptionString + "\n");
		}

		InputStream content = null;
		try {

			Base64.Decoder decoder = Base64.getDecoder();
			content = new ByteArrayInputStream(decoder.decode(contentString));

			int length = 0;
			byte data[] = new byte[STREAM_BUFFER_CHAR_SIZE * 10];
			BufferedInputStream bContent = null;
			try {

				log.info("download file " + fileName);
				bContent = new BufferedInputStream(content);
				ZipEntry fileEntry = new ZipEntry(fileName);
				out.putNextEntry(fileEntry);
				int bCount = -1;
				while ((bCount = bContent.read(data)) != -1) {
					out.write(data, 0, bCount);
					length = length + bCount;
				}

				try {
					out.closeEntry(); // The zip entry need to be closed
				} catch (IOException ioException) {
					String ioExceptionString = "zipFiles: problem closing zip entry "
							+ fileName + " " + ioException;
					log.error(ioExceptionString);
					zipFileStatus.append(ioExceptionString + "\n");
				}
			} catch (IllegalArgumentException iException) {
				String IAExceptionString = "zipFiles: problem creating BufferedInputStream with content and length "
						+ data.length + iException;
				log.warn(IAExceptionString);
				zipFileStatus.append(IAExceptionString + "\n");
			} finally {
				if (bContent != null) {
					try {
						bContent.close(); // The BufferedInputStream needs to be
											// closed
					} catch (IOException ioException) {
						String ioExceptionString = "zipFiles: problem closing FileChannel "
								+ ioException;
						log.warn(ioExceptionString);
						zipFileStatus.append(ioExceptionString + "\n");
					}
				}
			}
		} catch (IOException e) {
			String ioExceptionString = " zipFiles--IOException: : fileName=" + fileName;
			log.warn(ioExceptionString);
			zipFileStatus.append(ioExceptionString + "\n");
		} finally {
			if (content != null) {
				try {
					content.close(); // The input stream needs to be closed
				} catch (IOException ioException) {
					String ioExceptionString = "zipFiles: problem closing Inputstream content for" + fileName
							+ ioException;
					log.warn(ioExceptionString);
					zipFileStatus.append(ioExceptionString + "\n");
				}
			}
		}
		
		// return success message
		if (zipFileStatus.length() == 0)
		{
			zipFileStatus.append(fileName + " was added into zip file successfully.");
		}
		
		return zipFileStatus.toString();
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
		String userId = request.getRemoteUser();

		String boxClientId = env.getProperty(BOX_CLIENT_ID);
		String boxClientSecret = env.getProperty(BOX_CLIENT_SECRET);
		String boxAPIUrl = env.getProperty(BOX_API_URL);
		String boxClientRedirectUrl = env.getProperty(BOX_CLIENT_REDIRECT_URL)
				+ "/authorized";

		// need to have all Box app configurations
		if (boxClientId == null || boxClientSecret == null
				|| boxClientRedirectUrl == null) {
			log.error("Missing box integration parameters");
			return null;
		}
		String remoteUserEmail = userId;
		if (remoteUserEmail.indexOf(EMAIL_AT) == -1) {
			// if the remote user value is not of email format
			// then it is the uniqname of umich user
			// we need to attach "@umich.edu" to it to make it a full email
			// address
			remoteUserEmail = remoteUserEmail + EMAIL_AT_UMICH;
		}

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
	public String  boxAuthenticate(
			HttpServletRequest request, HttpServletResponse response) {
		// get the current user id
		String userId = request.getRemoteUser();
		String remoteUserEmail = userId + "@umich.edu";
		
		String boxClientId = env.getProperty(BOX_CLIENT_ID);
		String boxClientSecret = env.getProperty(BOX_CLIENT_SECRET);
		String boxAPIUrl = env.getProperty(BOX_API_URL);
		String boxClientRedirectUrl = env.getProperty(BOX_CLIENT_REDIRECT_URL)
				+ "/authorized";
		
		log.info("in /box/authorize");
		
		if (BoxUtils.getBoxAccessToken(userId) == null) {
			log.info("user " + userId + " has not authorized to use Box. Start auth process.");
			// go to Box authentication screen
			// get access token and refresh token and store locally
			return BoxUtils.authenticateString(boxAPIUrl, boxClientId, boxClientRedirectUrl,
					remoteUserEmail, response);
		}
		else
		{
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
	public Response unauthorizeBox(
			HttpServletRequest request, HttpServletResponse response) {
		
		// get the current user id
		String userId = request.getRemoteUser();
		
		// the return string
		String rv = "";
		
		// check whether the user authentication token is store in memory
		if (BoxUtils.getBoxAccessToken(userId) == null)
		{
			rv = "Cannot find user's Box authentication info. ";
		}
		else
		{
			BoxUtils.removeBoxAccessToken(userId);
			rv = "User authentication info is removed. ";
		}
		
		log.info("/box/unauthorize for user " + userId + " " + rv);
		try {
			return Response.status(Response.Status.OK)
					.entity(rv).build();
		} catch (Exception e) {
			return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
					.entity("Cannot remove box authentication info for user " + userId + ": " + e.getMessage())
					.build();
		}
	}

	@RequestMapping("/authorized")
	@Produces(MediaType.APPLICATION_JSON)
	public String getBoxAuthzTokens(HttpServletRequest request) {

		String boxClientId = env.getProperty(BOX_CLIENT_ID);
		String boxClientSecret = env.getProperty(BOX_CLIENT_SECRET);
		String boxAPIUrl = env.getProperty(BOX_API_URL);
		String boxTokenUrl = env.getProperty(BOX_TOKEN_URL);
		log.info("token url=" + boxTokenUrl);
		
		// get the current user id
		String userId = request.getRemoteUser();
		String rv = BoxUtils.getBoxAccessToken(userId);
		
		if (rv == null)
		{
			// get the authCode,
			// and get access token and refresh token subsequently
			BoxUtils.getAuthCodeFromBoxCallback(request, boxClientId,
					boxClientSecret, boxTokenUrl, userId);
			
			// try to get the access token after parsing the request string
			rv = BoxUtils.getBoxAccessToken(userId);
		}
		
		return rv != null?"Authorized":"Unauthorized";
	}
	
	@RequestMapping("/box/checkAuthorized")
	@Produces(MediaType.APPLICATION_JSON)
	public Boolean boxCheckAuthorized(HttpServletRequest request) {
		
		// get the current user id
		String userId = request.getRemoteUser();
		return Boolean.valueOf(BoxUtils.getBoxAccessToken(userId) != null);
	}
	
	/**
	 * Save Migration record to DB
	 * @return HasMap key="status", value=status message; key="migration", value=MigrationObject
	 */
	private HashMap<String, Object> saveMigrationRecord(HttpServletRequest request)
	{
		// the return hashmap provide newly created Migration object, and status message
		HashMap<String, Object> rv = new HashMap<String, Object>();
		
		// status message
		StringBuffer status = new StringBuffer();
		
		// get parameters
		Map<String, String[]> parameterMap = request.getParameterMap();
		String siteId = parameterMap.get("site_id")[0];
		String siteName = parameterMap.get("site_name")[0];
		String toolId = parameterMap.get("tool_id")[0];
		String toolName = parameterMap.get("tool_name")[0];
		String destinationType = "zip";
		String userId = request.getRemoteUser();
		
		Migration m = new Migration(siteId, siteName,
			toolId,
			toolName, userId,
			new java.sql.Timestamp(System.currentTimeMillis()), // start
																// time is
																// now
			null, destinationType, null, "" /* status */);

		Migration newMigration = null;

		StringBuffer insertMigrationDetails = new StringBuffer();
		insertMigrationDetails.append("Save migration record site_id=")
				.append(siteId).append(" site_name=")
				.append(siteName).append(" tool_id=")
				.append(toolId).append(" tool_name=")
				.append(toolName)
				.append(" migrated_by=").append(userId)
				.append(" destination_type=").append(destinationType)
				.append(" \n ");
		log.info(insertMigrationDetails.toString());
		try {
			newMigration = repository.save(m);
		} catch (Exception e) {
			log.error("Exception " + insertMigrationDetails.toString() + e.getMessage());
			status.append(e.getMessage());
		}
		
		// put Migration object into HashMap
		if (newMigration != null)
		{
			rv.put("migration", newMigration);
		}
		// put status message into HashMap
		if (status.length() == 0)
		{
			status.append("Database Migration record successfully created.");
		}
		rv.put("status", status.toString());
		
		return rv;
	}

	/**
	 * upload resource files into Box folder
	 * 
	 * @return
	 */
	@RequestMapping("/migrationBox")
	public void migrationBox(HttpServletRequest request,
			HttpServletResponse response) {

		// get user id
		String userId = request.getRemoteUser();
		
		StringBuffer boxMigrationStatus = new StringBuffer();
		// save migration record into database
		HashMap<String, Object> saveMigration = saveMigrationRecord(request);
		Migration newMigration = null;
		boxMigrationStatus.append(saveMigration.get("status"));
		
		// exit if there is no new Migration record saved into DB
		if (!saveMigration.containsKey("migration")) {
			// no new Migration record created
			return;
		}
		else
		{
			newMigration = (Migration) saveMigration.get("migration");
		}
		
		String boxClientId = env.getProperty(BOX_CLIENT_ID);
		String boxClientSecret = env.getProperty(BOX_CLIENT_SECRET);
		String boxClientRedirectUrl = env.getProperty(BOX_CLIENT_REDIRECT_URL);
		String boxAPIUrl = env.getProperty(BOX_API_URL);
		// need to have all Box app configurations
		if (boxClientId == null || boxClientSecret == null) {
			String boxClientIdError = "Missing Box integration parameters (Box client id, client secret)";
			log.error(boxClientIdError);
			boxMigrationStatus.append(boxClientIdError + "\n");
		}
		String remoteUserEmail = request.getRemoteUser();

		if (BoxUtils.getBoxAccessToken(userId) == null) {
			// go to Box authentication screen
			// get access token and refresh token and store locally
			BoxUtils.authenticate(boxAPIUrl, boxClientId, boxClientRedirectUrl,
					remoteUserEmail, response);
			return;
		}

		// get the CTools site id and target box folder id
		Map<String, String[]> parameterMap = request.getParameterMap();
		String siteId = parameterMap.get("site_id")[0];
		String boxFolderId = parameterMap.get("box_folder_id")[0];
		if (siteId == null || boxFolderId == null) {
			String boxFolderIdError = "Missing params for CTools site id, or target Box folder id.";
			log.error(boxFolderIdError);
			boxMigrationStatus.append(boxFolderIdError + "\n");
		}

		// login to CTools and get sessionId
		String sessionId = login_becomeuser(request);
		log.info(sessionId);
		if (sessionId != null) {
			// 3. get all sites that user have permission site.upd
			RestTemplate restTemplate = new RestTemplate();
			// the url should be in the format of
			// "https://server/direct/site/SITE_ID.json"
			String requestUrl = env.getProperty("ctools.server.url")
					+ "direct/content/site/" + siteId + ".json?_sessionId="
					+ sessionId;
			String siteResourceJson = null;
			try {
				siteResourceJson = restTemplate.getForObject(requestUrl,
						String.class);
				String downloadStatusString = boxUploadSiteContent(userId, sessionId, boxClientId, boxClientSecret,
						siteResourceJson, boxFolderId);
				boxMigrationStatus.append(downloadStatusString + "\n");

			} catch (RestClientException e) {
				String errorMessage = "Cannot find site by siteId: " + siteId
						+ " " + e.getMessage();
				Response.status(Response.Status.NOT_FOUND).entity(errorMessage)
						.type(MediaType.TEXT_PLAIN).build();
				log.error(errorMessage);
				boxMigrationStatus.append(errorMessage + "\n");
			}

			String uploadFinished = "Finished upload site content for site " + siteId;
			log.info(uploadFinished);
			boxMigrationStatus.append(uploadFinished + "\n");
		}
		else
		{
			String errorBecomeUser = "Problem become user to " + userId;
			log.error(errorBecomeUser);
			boxMigrationStatus.append(errorBecomeUser + "\n");
		}

		// update the status and end_time of migration record
		repository.setMigrationEndTime(
				new java.sql.Timestamp(System.currentTimeMillis()),
				newMigration.getMigration_id());
		repository.setMigrationStatus(boxMigrationStatus.toString(),
					newMigration.getMigration_id());
	}

	/**
	 * iterating though content json and upload folders and files to Box
	 */
	private String boxUploadSiteContent(String userId, String sessionId, String boxClientId,
			String boxClientSecret, String siteResourceJson, String boxFolderId) {
		StringBuffer status = new StringBuffer();
		
		BoxAPIConnection api = new BoxAPIConnection(boxClientId,
				boxClientSecret, BoxUtils.getBoxAccessToken(userId),
				BoxUtils.getBoxRefreshToken(userId));
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
			JSONObject contentItem = array.getJSONObject(i);

			// get only the url after "/access/content" string
			String contentUrl = URLDecoder.decode(contentItem
					.getString(CONTENT_JSON_ATTR_URL));
			contentUrl = contentUrl.substring(contentUrl
					.indexOf(CTOOLS_ACCESS_STRING)
					+ CTOOLS_ACCESS_STRING.length());

			// inside the JSON feed, the container string is of format
			// /content/<folder_url>
			// remote the prefix "/content"
			String container = URLDecoder.decode(contentItem
					.getString(CONTENT_JSON_ATTR_CONTAINER));
			container = container.substring(container
					.indexOf(CTOOLS_CONTENT_STRING)
					+ CTOOLS_CONTENT_STRING.length());

			String type = contentItem.getString(CONTENT_JSON_ATTR_TYPE);
			String title = contentItem.getString(CONTENT_JSON_ATTR_TITLE);
			
			// metadata
			String description = contentItem.getString(CONTENT_JSON_ATTR_DESCRIPTION);
			String author = contentItem.getString(CONTENT_JSON_ATTR_AUTHOR);
			
			
			// files
			if (contentUrl == null && contentUrl.length() == 0) {
				// log error if the content url is missing
				String urlError = "No url for content " + title; 
				log.error(urlError);
				status.append(urlError + "\n");
				break;
			} else if (container == null && container.length() == 0) {
				// log error if the content url is missing
				String containerError = "No container folder url for content " + title;
				log.error(containerError);
				status.append(containerError + "\n");
				break;
			}

			if (COLLECTION_TYPE.equals(type)) {
				// folders
				if (rootFolderPath == null) {
					// root folder
					rootFolderPath = contentUrl;

					// insert into stack
					containerStack.push(contentUrl);
					boxFolderIdStack.push(boxFolderId);
				} else {
					log.info("Begin to create folder " + title);

					log.debug("top of stack folder id =" + containerStack.peek()
							+ " " + " container folder id =" + container);
					// pop the stack till the container equals to stack top
					while (!containerStack.empty()
							&& !container.equals(containerStack.peek())) {
						// sync pops
						containerStack.pop();
						boxFolderIdStack.pop();
					}

					// create box folder
					BoxFolder parentFolder = new BoxFolder(api,
							boxFolderIdStack.peek());
					try {
						BoxFolder.Info childFolderInfo = parentFolder
								.createFolder(title);
						log.info("folder " + title + " created.");

						// push the current folder id into the stack
						containerStack.push(contentUrl);
						boxFolderIdStack.push(childFolderInfo.getID());
						log.debug("top of stack folder id = " + containerStack.peek()
								+ " " + " container folder id=" + container);
						log.debug("*******");
					} catch (BoxAPIException e) {
						if (e.getResponseCode() == org.apache.http.HttpStatus.SC_CONFLICT) {
							// 409 means name conflict - item already existed
							String folderExistError = "There is already a folder with name "
									+ title;
							log.info(folderExistError);
							status.append(folderExistError + "\n");
							
							String exisingFolderId = getExistingBoxFolderIdFromBoxException(e, title);
							if (exisingFolderId != null)
							{
								// push the current folder id into the stack
								containerStack.push(contentUrl);
								boxFolderIdStack.push(exisingFolderId);
								log.debug("top of stack folder id = " + containerStack.peek()
										+ " " + " container folder id=" + container);
								log.debug("*******");
							}
							else
							{
								log.info("Cannot find conflicting Box folder id for folder name " + title);
							}
							
						}
					}
				}

			} else {
				// files
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
					String parentError = "Cannot find parent folder for file " + contentUrl;
					log.info(parentError);
					status.append(parentError + "\n");
					break;
				}

				String fileName = contentUrl.replace(rootFolderPath, "");
				String uploadFileStatus = uploadFile(boxFolderIdStack.peek(), fileName, contentUrl,
						sessionId, api);
				status.append(uploadFileStatus + "\n");
			}
		} // for

		// refresh tokens
		BoxUtils.refreshAccessAndRefreshTokens(userId, api);
		return status.toString();
	}
	
	/**
	 * Based on the JSON returned inside BoxAPIException object, find out the id of conflicting box folder
	 * @return id 
	 */
	private String getExistingBoxFolderIdFromBoxException(BoxAPIException e, String folderTitle)
	{
		String existingFolderId = null;
		// here is the example JSON returned
		//{  
		//   "type":"error",
		//   "status":409,
		//   "code":"item_name_in_use",
		//   "context_info":{  
		//      "conflicts":[  
		//         {  
		//            "type":"folder",
		//            "id":"5443268429",
		//            "sequence_id":"0",
		//            "etag":"0",
		//            "name":"folder1"
		//         }
		//      ]
		//   },
		//   "help_url":"http:\/\/developers.box.com\/docs\/#errors",
		//   "message":"Item with the same name already exists",
		//   "request_id":"153175908556537d483098d"
		//}
		if (e.getResponse() == null)
			return null;
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
			if (conflictType != null && conflictType.equals("folder"))
			{
				String folderId = conflict.getString("id");
				String folderName = conflict.getString("name");
				if (folderName != null && folderName.equals(folderTitle))
				{
					// found the existing folder id, break
					existingFolderId = folderId;
					break;
				}
			}
		}
		return existingFolderId;
	}

	/**
	 * upload files to Box
	 */
	private String uploadFile(String boxFolderId, String fileName,
			String fileUrl, String fileDescription, String fileAuthor, String sessionId, BoxAPIConnection api) {
		// status string
		StringBuffer status = new StringBuffer();
		
		log.info("begin to upload file " + fileUrl + " to box folder "
				+ boxFolderId);
		String contentString = "";
		try {
			Service service = new Service();
			Call nc = (Call) service.createCall();

			nc.setTargetEndpointAddress(env.getProperty("ctools.server.url")
					+ "sakai-axis/ContentHosting.jws");

			nc.removeAllParameters();
			nc.setOperationName(OPERATION_GET_CONTENT_DATA);
			nc.addParameter(OPERTAION_GET_CONTENT_DATA_PARAM_SESSIONID,
					XMLType.XSD_STRING, ParameterMode.IN);
			nc.addParameter(OPERTAION_GET_CONTENT_DATA_PARAM_RESOURCEID,
					XMLType.XSD_STRING, ParameterMode.IN);
			nc.setReturnType(XMLType.XSD_STRING);
			contentString = (String) nc.invoke(new Object[] { sessionId,
					fileUrl });

		} catch (Exception e) {
			String exceptionString = "zipFiles " + fileUrl + " " + e.getMessage();
			log.error(exceptionString);
			status.append(exceptionString + "\n");
		}

		InputStream content = null;

		Base64.Decoder decoder = Base64.getDecoder();
		content = new ByteArrayInputStream(decoder.decode(contentString));

		int length = 0;
		byte data[] = new byte[STREAM_BUFFER_CHAR_SIZE * 10];
		BufferedInputStream bContent = null;
		try {

			bContent = new BufferedInputStream(content);
			BoxFolder folder = new BoxFolder(api, boxFolderId);
			final String uploadFileName = fileName;
			BoxFile.info newFile = folder.uploadFile(bContent, fileName, STREAM_BUFFER_CHAR_SIZE,
					new ProgressListener() {
						public void onProgressChanged(long numBytes,
								long totalBytes) {
							double percentComplete = numBytes / totalBytes;
							log.debug(uploadFileName + " uploaded "
									+ percentComplete);
						}
					});
			newFile.
			log.info("upload success for file " + fileUrl);
		} catch (BoxAPIException e) {
			if (e.getResponseCode() == org.apache.http.HttpStatus.SC_CONFLICT) {
				// 409 means name conflict - item already existed
				String conflictString = "There is already a file with name " + fileName;
				log.info(conflictString);
				status.append(conflictString + "\n");
			}
		} catch (IllegalArgumentException iException) {
			String ilExceptionString = "problem creating BufferedInputStream for file " + fileName + " with content and length "
					+ data.length + iException;
			log.warn(ilExceptionString);
			status.append(ilExceptionString + "\n");
		} finally {
			if (bContent != null) {
				try {
					bContent.close(); // The BufferedInputStream needs to be
										// closed
				} catch (IOException ioException) {
					String ioExceptionString = "problem closing FileChannel for file " + fileName + " " + ioException;
					log.error(ioExceptionString);
					status.append(ioExceptionString + "\n");
				}
			}
		}
		if (content != null) {
			try {
				content.close(); // The input stream needs to be closed
			} catch (IOException ioException) {
				String ioExceptionString = "zipFiles: problem closing Inputstream content for file " + fileName + " " + ioException;
				log.error(ioExceptionString);
				status.append(ioExceptionString + "\n");
			}
		}
		
		// box upload success
		if (status.length() == 0)
		{
			status.append("Box upload successful for file " + fileName + "\n");
		}
		return status.toString();
	}
}
