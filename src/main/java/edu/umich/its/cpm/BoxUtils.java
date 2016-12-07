package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.UUID;
import java.text.SimpleDateFormat;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.json.JSONObject;
import org.json.JSONArray;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIRequest;
import com.box.sdk.BoxAPIResponse;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxCollaborator;
import com.box.sdk.BoxCollaboration;
import com.box.sdk.BoxResource;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxItem;
import com.box.sdk.BoxItem.Info;
import com.box.sdk.BoxUser;

import edu.umich.its.cpm.MigrationInstanceService.MigrationFields;
import edu.umich.its.cpm.MigrationBoxFileRepository;
import edu.umich.its.cpm.BoxAuthUserRepository;
import edu.umich.its.cpm.BoxAuthUser;

import org.apache.commons.io.IOUtils;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.apache.http.StatusLine;
import org.apache.http.HttpStatus;
import org.springframework.context.EnvironmentAware;

/**
 * provides util functions for accessing Box APIs
 * 
 * @author zqian
 *
 */
@Component
@PropertySource("file:${catalina.base:/usr/local/ctools/app/ctools/tl}/home/application.properties")
public class BoxUtils implements EnvironmentAware {

	private static final int MAX_DEPTH = 0;

	private static final String CODE = "code";
	
	// the state variable used in OAuth process 
	// to associate Box returned tokens with right OAuth requester  
	private static final String STATE = "state";
	
	private static Environment env;

    @Override
    public void setEnvironment(final Environment environment) {
        this.env = environment;
    }
    
	/**
	 * get Box refresh token for given user
	 */
	public static String getBoxRefreshToken(String userId, BoxAuthUserRepository repository) {
		String boxRefreshToken = null;
		BoxAuthUser u = repository.findOne(userId);
		if (u != null) {
			// if the token exists for given user
			boxRefreshToken = u.getRefreshToken();
		}
		return boxRefreshToken;
	}

	private static final SimpleDateFormat date_formatter = new SimpleDateFormat(
			"yyyy-MM-dd-hh.mm.ss");

	private static final Logger log = LoggerFactory.getLogger(BoxUtils.class);

	public static void getCurrentUser(BoxAPIConnection connection) {
		BoxUser user = BoxUser.getCurrentUser(connection);
		BoxUser.Info info = user.getInfo();
	}

	public static String authenticateString(String boxAPIUrl,
	                String boxClientId, String boxClientRedirectUri,
	                String remoteUserEmail, HttpServletResponse response) {
		// Box authorization
		RestTemplate restTemplate = new RestTemplate();
        
		if (boxAPIUrl == null) {
			// log error
			log.error("No Box API url specified. ");
			return "";
		}
		
		String requestUrl = boxAPIUrl + "/oauth2/authorize"
							+ "?response_type=code" + "&client_id=" + boxClientId
							+ "&redirect_uri=" + boxClientRedirectUri
							+ "&state=&box_login=" + remoteUserEmail;

		
		try {
			return restTemplate.getForObject(requestUrl, String.class);
		} catch (RestClientException e) {
			log.error(requestUrl + e.getMessage());
		}
		return "";
	}
	
	public static void authenticate(String boxAPIUrl, String boxClientId,
			String boxClientRedirectUri, String remoteUserEmail,
			HttpServletResponse response, BoxAuthUserRepository repository) {
		// Box authorization
		RestTemplate restTemplate = new RestTemplate();

		if (boxAPIUrl == null) {
			// log error
			log.error("No Box API url specified. ");
			return;
		}
		
		// An arbitrary string of your choosing that will be included in the response to your application. 
		// Anything that might be useful for your application can be included. 
		// Box roundtrips this information back to your application, 
		// and strongly recommends that you include an anti-forgery token, 
		// and confirm it in the response to prevent CSRF attacks to your users.
		String state = UUID.randomUUID().toString();
		
		// create new BoxAuthUser object
		// store the state value into BoxAuthUser object, 
		// so that we will pick the right user when getting the access token and refresh token
		BoxAuthUser u = new BoxAuthUser(remoteUserEmail, state, "", "");
		try
		{
			// save user information into database
			repository.save(u);
		}
		catch (Exception e)
		{
			log.error("There is problem saving BoxAuthUser for user " + remoteUserEmail + " " + e);
			return;
		}
		
		String requestUrl = boxAPIUrl + "/oauth2/authorize"
				+ "?response_type=code" + "&client_id=" + boxClientId
				+ "&redirect_uri=" + boxClientRedirectUri
				+ "&state=" + state + "&box_login=" + remoteUserEmail;

		try {
			String resultString = restTemplate.getForObject(requestUrl,
					String.class);
			// open window with resultString
			response.setContentType("text/html");
			response.setCharacterEncoding("UTF-8");
			response.getWriter().println(resultString);
			response.flushBuffer();
			response.getWriter().close();
		} catch (RestClientException e) {
			log.error(requestUrl + e.getMessage());
		} catch (IOException e) {
			log.error(requestUrl + e.getMessage());
		}
	}

	/**
	 * get the authCode as embedded from Box callback response
	 */
	public static String getAuthCodeFromBoxCallback(HttpServletRequest request,
			String boxClientId, String boxClientSecret, String boxTokenUrl, BoxAuthUserRepository repository) {
		// get the code String from Box authorization callback
		String authCode = null;
		String state = null;
		java.util.Enumeration<java.lang.String> e = request.getParameterNames();
		while (e.hasMoreElements()) {
			String paramName = e.nextElement();
			if (CODE.equals(paramName)) {
				authCode = request.getParameter(paramName);
			}
			else if (STATE.equals(paramName)) {
				state = request.getParameter(paramName);
			}
		}

		if (authCode == null) {
			log.error("getAuthCodeFromBoxCallback: authCode is null");
			return null;
		}
		
		// find the right user by comparing the state value stored in database 
		// with the state value returned in the callback
		List<BoxAuthUser> uList = repository.findBoxAuthUserByState(state);
		if (uList.isEmpty())
		{
			log.error("getAuthCodeFromBoxCallback: cannot find BoxAuthUser with state = " + state);
			return null;
		}
		else if (uList.size() > 1)
		{
			log.error("getAuthCodeFromBoxCallback: find more than one BoxAuthUser with state = " + state);
			return null;
		}
		
		BoxAuthUser u = uList.get(0);

		// now that we have the authCode, use it to get access token and fresh
		// token
		// Returns a new access_token & refresh_token from an existing
		// refresh_token
		// Each access_token is valid for 1 hour. In order to get a new, valid
		// token, you can use the accompanying
		// refresh_token. Each refresh token is valid for 14 days. Every time
		// you get a new access_token by using a
		// refresh_token, we reset your timer for the 14 day period. This means
		// that as long as your users use your
		// application once every 14 days, their login is valid forever.
		// Args:
		// - client_id: The client_id you obtained in the initial setup.
		// - client_secret: The client_secret you obtained in the initial setup.
		// - code: a string containing the code, or a dictionary containing the
		// GET query
		// Returns:
		// - a dictionary with the token and additional info
		try {
			CloseableHttpClient httpclient = HttpClients.createDefault();
			HttpPost httpPost = new HttpPost(boxTokenUrl);
			List<NameValuePair> nvps = new ArrayList<NameValuePair>();
			nvps.add(new BasicNameValuePair("grant_type", "authorization_code"));
			nvps.add(new BasicNameValuePair("client_id", boxClientId));
			nvps.add(new BasicNameValuePair("client_secret", boxClientSecret));
			nvps.add(new BasicNameValuePair("code", authCode));
			httpPost.setEntity(new UrlEncodedFormEntity(nvps));
			CloseableHttpResponse response = httpclient.execute(httpPost);

			try {
				StatusLine statusLine = response.getStatusLine();
				log.info("token request status" + statusLine);
				if (statusLine.getStatusCode() == HttpStatus.SC_OK) {
					// if success, get the access_token and refresh_token
					HttpEntity entity = response.getEntity();
					InputStream body = entity.getContent();
					String theString = IOUtils.toString(body, "UTF-8");
					JSONObject obj = new JSONObject(theString);
					repository.setBoxAuthUserAccessToken((String) obj.get("access_token"), u.getUserId());
					repository.setBoxAuthUserRefreshToken((String) obj.get("refresh_token"), u.getUserId());

					// close inputstream and entity
					IOUtils.closeQuietly(body);
					EntityUtils.consume(entity);
				} else {
					// log when the statusLine did not return 200 code
					log.error("getAuthCodeFromBoxCallback: status code= "
							+ statusLine.getStatusCode() + " "
							+ statusLine.toString());
				}
			} finally {
				response.close();
			}
		} catch (java.net.MalformedURLException exception) {
			log.error("getAuthCodeFromBoxCallback MalformedURLException "
					+ boxTokenUrl);
		} catch (java.io.IOException exception) {
			log.error("getAuthCodeFromBoxCallback IOException " + boxTokenUrl);
		}
		return authCode;
	}

	/**
	 * method to return json list of Box folders
	 */
	public static List<HashMap<String, String>> getBoxFolders(String userId,
			String boxClientId, String boxClientSecret, BoxAuthUserRepository repository) {

		BoxAPIConnection api = getBoxAPIConnection(userId, repository);
		if (api != null) {

			// get the root Box folder
			BoxFolder rootFolder = BoxFolder.getRootFolder(api);

			// get list of properties from all Box items contained
			List<HashMap<String, String>> folderItems = BoxUtils
					.listBoxFolders(null, api, rootFolder, "", 0);

			return folderItems;
		}
		return null;
	}

	/**
	 * method add a folder to the root level
	 */
	public static Info createNewFolderAtRootLevel(String userId,
			String boxClientId, String boxClientSecret,
			String newBoxFolderName, String siteId, BoxAuthUserRepository repository) {
		BoxFolder.Info newFolderInfo = null;

		BoxAPIConnection api = getBoxAPIConnection(userId, repository);
		if (api != null) {
			BoxFolder rootFolder = null;
			try {
				// get the root Box folder
				rootFolder = BoxFolder.getRootFolder(api);
				
				// test the newly created object
				// and watch for any BoxAPIException
				for (Info c : rootFolder.getChildren("name", "description")) {
					if (newBoxFolderName.equals(c.getName()))
					{
						
						if (siteId.equals(c.getDescription())) {
							// if the Box folder name matches site title, 
							// AND Box folder description matches site id
							// return null to mark the Box folder already exist
							return null;
						}
						else
						{
							// if only Box folder name matches site title
							// but Box folder description does NOT match site id
							// we need to create a new Box folder
							if (newBoxFolderName.equals(Utils.CTOOLS_MYWORKSPACE_TITLE))
							{
								// for MyWorkspace sites, attach uniqname to Box folder name
								newFolderInfo = rootFolder.createFolder(newBoxFolderName + "_" + userId);
							}
							else
							{
								// for other type of sites, attach the site id to Box folder name
								newFolderInfo = rootFolder.createFolder(newBoxFolderName + "_" + siteId);
							}
							BoxFolder newFolder = newFolderInfo.getResource();
							newFolderInfo.setDescription(siteId);
							newFolder.updateInfo(newFolderInfo);
						}
						break;
					}
				}

				if (newFolderInfo == null) {
					// no there is no Box folder name match site title
					// create the Box folder
					newFolderInfo = rootFolder.createFolder(newBoxFolderName);
					BoxFolder newFolder = newFolderInfo.getResource();
					newFolderInfo.setDescription(siteId);
					newFolder.updateInfo(newFolderInfo);
				}
			} catch (BoxAPIException e) {
				log.error("createNewFolderAtRootLevel: message=" + e.getMessage() + " response=" + e.getResponse());
			}
		}

		return newFolderInfo;
	}

	/**
	 * Refresh Box access token and refresh token if necessary
	 * Store new tokens into database
	 */
	public static BoxAPIConnection refreshAccessAndRefreshTokens(String boxClientId, String boxClientSecret, 
			String userId, BoxAPIConnection api, BoxAuthUserRepository repository) {
		// refresh accessToken and refreshToken if necessary
		try
		{
			api.refresh();
			String newAccessToken = api.getAccessToken();
			String newRefreshToken = api.getRefreshToken();
			
			// save the new tokens into database
			repository.setBoxAuthUserAccessToken(newAccessToken, userId);
			repository.setBoxAuthUserRefreshToken(newRefreshToken, userId);
			
			// return construct new BoxAPIConnection object
			api = new BoxAPIConnection(boxClientId,
					boxClientSecret, newAccessToken, newRefreshToken);
			}
		catch (BoxAPIException e)
		{
			log.error("refreshAccessAndRefreshTokens message=" + e.getMessage() + " response=" + e.getResponse());
			if (400 == e.getResponseCode())
			{
				// refresh token is expired
				// user needs to authenticate again
				repository.deleteBoxAuthUserAccessToken(userId);
				repository.deleteBoxAuthUserRefreshToken(userId);
			}
		}

		return api;
	}

	/**
	 * recursively get all file and folder items inside the root folder this is
	 * a depth-first list
	 */
	public static List<HashMap<String, String>> listBoxFolders(
			List<HashMap<String, String>> folderMap, BoxAPIConnection api,
			BoxFolder folder, String folderPath, int folderDepth) {
		if (folderMap == null)
			folderMap = new ArrayList<HashMap<String, String>>();
		for (BoxItem.Info itemInfo : folder) {
			if (itemInfo instanceof BoxFolder.Info) {

				BoxFolder.Info folderInfo = (BoxFolder.Info) itemInfo;
				BoxFolder xfolder = new BoxFolder(api, folderInfo.getID());
				String currentFolderPath = folderPath + Utils.PATH_SEPARATOR
						+ folderInfo.getName();
				folderMap.add(getBoxItemProperties(xfolder.getInfo(),
						currentFolderPath));
				if (folderDepth < MAX_DEPTH) {
					// go one level deeper in folder structure
					listBoxFolders(folderMap, api, xfolder, currentFolderPath,
							folderDepth + 1);
				}
			}
		}

		return folderMap;

	}

	/**
	 * get BoxItem properties
	 */
	private static HashMap<String, String> getBoxItemProperties(
			BoxItem.Info info, String path) {
		HashMap<String, String> properties = new HashMap<String, String>();
		properties.put("ID", info.getID());
		properties.put("name", info.getName());
		properties.put("size", String.valueOf(info.getSize()));

		// type defaults to folder
		String type = "folder";
		if (info instanceof BoxFile.Info) {
			properties.put("sh1", ((BoxFile.Info) info).getSha1());
			type = "file";
		}
		properties.put("type", type);

		properties.put("content_created_at",
				format_date(info.getContentCreatedAt()));
		properties.put("content_modified_at",
				format_date(info.getContentModifiedAt()));
		properties.put("created_at", format_date(info.getCreatedAt()));
		properties.put("created_by", get_box_user_name(info.getCreatedBy()));
		properties.put("modified_by", get_box_user_name(info.getCreatedBy()));
		properties.put("owned_by", get_box_user_name(info.getOwnedBy()));
		properties.put("description", info.getDescription());
		properties.put("path", path);
		return properties;
	}

	/**
	 * a function to format Date object
	 */
	private static String format_date(Date date) {
		String rv = "";
		if (date != null) {
			// SimpleDateFormat is not threadsafe
			synchronized (date_formatter) {
				rv = date_formatter.format(date);
			}
		}
		return rv;
	}

	/**
	 * return the name of BoxUser
	 */
	private static String get_box_user_name(BoxUser.Info uInfo) {
		String rv = "";
		if (uInfo != null) {
			rv = uInfo.getName();
		}
		return rv;
	}

	/**
	 * get the Box Client ID or secret based on user role
	 * @param userId
	 * @param forParam
	 * @return
	 */
	public static String getBoxClientIdOrSecret(String userId, String forParam) {
		String rv = "";
		
		// is current user a Box Admin user?
		boolean isAdminUser = Utils.isCurrentUserCPMAdmin(userId, env) || env.getProperty(Utils.BOX_ADMIN_ACCOUNT_ID).equals(userId);
			
		if (Utils.BOX_ID.equals(forParam))
		{
			// return id
			if (isAdminUser)
				// return admin Box id
				rv = env.getProperty(Utils.BOX_ADMIN_CLIENT_ID);
			else
				// return Box id
				rv = env.getProperty(Utils.BOX_CLIENT_ID);
		}
		else if (Utils.BOX_SECRET.equals(forParam))
		{
			if (isAdminUser)
				// return admin Box secret
				rv = env.getProperty(Utils.BOX_ADMIN_CLIENT_SECRET);
			else
				// return Box secret
				rv = env.getProperty(Utils.BOX_CLIENT_SECRET);
		}
		return rv;
	}

	/**
	 * get the Box Client redirect url based on user
	 * 
	 * @param request
	 * @param env
	 * @return
	 */
	public static String getBoxClientRedirectUrl(HttpServletRequest request,
			Environment env) {
		return env.getProperty(Utils.BOX_CLIENT_REDIRECT_URL) + "/authorized";
	}

	/**
	 * add user as collaborate to Box folder
	 * 
	 * @param userEmail
	 * @param role
	 * @param folderId
	 * @param boxClientId
	 * @param boxClientSecret
	 */
	public static void addCollaboration(String boxAdminId, String userEmail,
			String role, String folderId, String boxClientId,
			String boxClientSecret, BoxAuthUserRepository repository) {
		BoxAPIConnection api = getBoxAPIConnection(boxAdminId, repository);
		
		BoxFolder folder = null;
		
		if (api != null) {
			try {
				folder = new BoxFolder(api, folderId);

				// map CTools roles to Box Collaborator roles
				// default to be Box View role
				BoxCollaboration.Role boxRole = BoxCollaboration.Role.VIEWER;
				if (Utils.ROLE_OWNER.equals(role))
				{
					boxRole = BoxCollaboration.Role.CO_OWNER;
				}
				else if (Utils.ROLE_ORGANIZER.equals(role))
				{
					boxRole = BoxCollaboration.Role.EDITOR;
				}
				else if (Utils.ROLE_MEMBER.equals(role))
				{
					boxRole = BoxCollaboration.Role.VIEWER_UPLOADER;
				}
				else if (Utils.ROLE_OBSERVER.equals(role))
				{
					boxRole = BoxCollaboration.Role.VIEWER;
				}
				else if (Utils.ROLE_MAINTAINER.equals(role))
				{
					boxRole = BoxCollaboration.Role.CO_OWNER;
				}
				else if (Utils.ROLE_INSTRUCTOR.equals(role))
				{
					boxRole = BoxCollaboration.Role.EDITOR;
				}
				else if (Utils.ROLE_STUDENT.equals(role))
				{
					boxRole = BoxCollaboration.Role.VIEWER_UPLOADER;
				}
				folder.collaborate(userEmail, boxRole);
			} catch (BoxAPIException e) {
				log.warn("addCollaboration messsage=" + e.getMessage() + " response=" + e.getResponse() );
			}
		}

	}

	/**
	 * get the BoxAPIConnection object renew access token and refresh token if
	 * necessary
	 * 
	 * @param boxClientId
	 * @param boxClientSecret
	 * @param boxAccessToken
	 * @param boxRefreshToken
	 */
	public static BoxAPIConnection getBoxAPIConnection(String userId, BoxAuthUserRepository repository) {
		String boxClientId = getBoxClientIdOrSecret(userId, Utils.BOX_ID);
		String boxClientSecret = getBoxClientIdOrSecret(userId, Utils.BOX_SECRET);
		
		String boxAccessToken = repository.findBoxAuthUserAccessToken(userId);
		String boxRefreshToken = repository.findBoxAuthUserRefreshToken(userId);
		try {
			// make connection
			BoxAPIConnection api = new BoxAPIConnection(boxClientId,
					boxClientSecret, boxAccessToken, boxRefreshToken);
			
			return api;

		} catch (BoxAPIException e) {
			log.error("BoxUtils getBoxAPIConnection " + e.getResponse());
			String response = e.getResponse();
			if (response.contains("Refresh token has expired")) {
				// time to refresh the refresh token
				// remove the locally stored token for the user
				// so that the user will need to go through the Box
				// authentication process again to generate refresh token and
				// access token
				repository.deleteBoxAuthUserAccessToken(userId);
				repository.deleteBoxAuthUserRefreshToken(userId);
			}
			return null;
		}
	}

}