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
import java.text.SimpleDateFormat;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.beans.factory.annotation.Autowired;
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

/**
 * provides util functions for accessing Box APIs
 * 
 * @author zqian
 *
 */
public class BoxUtils {

	private static final int MAX_DEPTH = 0;

	private static final String CODE = "code";

	private static HashMap<String, String> boxAccessTokens = new HashMap<String, String>();

	private static HashMap<String, String> boxRefreshTokens = new HashMap<String, String>();

	// HashMap, indexed by user id, holds queue for Box migration tasks
	private static HashMap<String, LinkedList<MigrationFields>> userBoxMigrationRequests = new HashMap<String, LinkedList<MigrationFields>>();

	@Autowired
	private Environment env;

	/**
	 * get Box migration request for all users
	 */
	public static HashMap<String, LinkedList<MigrationFields>> getBoxMigrationRequests() {
		// return all
		return userBoxMigrationRequests;
	}

	/**
	 * get Box migration request list for given user
	 */
	public static LinkedList<MigrationFields> getBoxMigrationRequestForUser(
			String userId) {
		LinkedList<MigrationFields> requests = null;
		if (userBoxMigrationRequests.containsKey(userId)) {
			// if the request exists for given user
			requests = userBoxMigrationRequests.get(userId);
		}
		return requests;
	}

	/**
	 * set Box migration request list for given user
	 */
	public static synchronized void setBoxMigrationRequestForUser(
			String userId, LinkedList<MigrationFields> requests) {
		if (requests.size() == 0) {
			// if the request list is empty, remove the user entry altogether
			userBoxMigrationRequests.remove(userId);
		} else {
			// set the new requests value
			userBoxMigrationRequests.put(userId, requests);
		}
	}

	/**
	 * get Box access token for given user
	 */
	public static String getBoxAccessToken(String userId) {
		String boxAccessToken = null;
		if (boxAccessTokens.containsKey(userId)) {
			// if the token exists for given user
			boxAccessToken = boxAccessTokens.get(userId);
		}
		return boxAccessToken;
	}

	/**
	 * set Box access token for given user
	 */
	public static void setBoxAccessToken(String userId, String boxAccessToken) {
		boxAccessTokens.put(userId, boxAccessToken);
	}

	/**
	 * remote Box access token for given user
	 */
	public static void removeBoxAccessToken(String userId) {
		boxAccessTokens.remove(userId);
	}

	/**
	 * get Box refresh token for given user
	 */
	public static String getBoxRefreshToken(String userId) {
		String boxRefreshToken = null;
		if (boxRefreshTokens.containsKey(userId)) {
			// if the token exists for given user
			boxRefreshToken = boxRefreshTokens.get(userId);
		}
		return boxRefreshToken;
	}

	/**
	 * set Box refresh token for given user
	 */
	public static void setBoxRefreshToken(String userId, String boxRefreshToken) {
		boxRefreshTokens.put(userId, boxRefreshToken);
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
			String resultString = restTemplate.getForObject(requestUrl,
					String.class);
			return resultString;
		} catch (RestClientException e) {
			log.error(requestUrl + e.getMessage());
		}

		return "";
	}

	public static void authenticate(String boxAPIUrl, String boxClientId,
			String boxClientRedirectUri, String remoteUserEmail,
			HttpServletResponse response) {
		// Box authorization
		RestTemplate restTemplate = new RestTemplate();

		if (boxAPIUrl == null) {
			// log error
			log.error("No Box API url specified. ");
			return;
		}

		String requestUrl = boxAPIUrl + "/oauth2/authorize"
				+ "?response_type=code" + "&client_id=" + boxClientId
				+ "&redirect_uri=" + boxClientRedirectUri
				+ "&state=&box_login=" + remoteUserEmail;

		try {
			String resultString = restTemplate.getForObject(requestUrl,
					String.class);
			// open window with resultString
			response.setContentType("text/html");
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
			String boxClientId, String boxClientSecret, String boxTokenUrl,
			String userId) {
		// get the code String from Box authorization callback
		String authCode = null;
		java.util.Enumeration<java.lang.String> e = request.getParameterNames();
		while (e.hasMoreElements()) {
			String paramName = e.nextElement();
			if (CODE.equals(paramName)) {
				authCode = request.getParameter(paramName);
				break;
			}
		}

		if (authCode == null) {
			log.error("getAuthCodeFromBoxCallback: authCode is null for user "
					+ userId);
			return null;
		}

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
					setBoxAccessToken(userId, (String) obj.get("access_token"));
					setBoxRefreshToken(userId,
							(String) obj.get("refresh_token"));

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
			String boxClientId, String boxClientSecret) {

		BoxAPIConnection api = getBoxAPIConnection(userId, boxClientId,
				boxClientSecret);
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
			String newBoxFolderName, String siteId) {
		BoxFolder.Info newFolderInfo = null;

		BoxAPIConnection api = getBoxAPIConnection(userId, boxClientId,
				boxClientSecret);
		if (api != null) {
			// get the root Box folder
			BoxFolder rootFolder = BoxFolder.getRootFolder(api);

			for (Info c : rootFolder.getChildren()) {
				if (newBoxFolderName.equals(c.getName())) {
					// folder already exist
					newFolderInfo = (BoxFolder.Info) c;
				}
			}

			if (newFolderInfo == null) {
				// no such folder yet, create the folder
				newFolderInfo = rootFolder.createFolder(newBoxFolderName);
				BoxFolder newFolder = newFolderInfo.getResource();
				newFolderInfo.setDescription(siteId);
				newFolder.updateInfo(newFolderInfo);
			}

			return newFolderInfo;
		}
		return null;
	}

	/**
	 * store the current access token and refresh token locally for given user
	 */
	public static BoxAPIConnection refreshAccessAndRefreshTokens(String userId,
			BoxAPIConnection api) {
		// refresh accessToken and refreshToken if necessary
		setBoxAccessToken(userId, api.getAccessToken());
		setBoxRefreshToken(userId, api.getRefreshToken());

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
	 * get the Box Client ID based on user role
	 * 
	 * @param request
	 * @param env
	 * @return
	 */
	public static String getBoxClientId(HttpServletRequest request,
			Environment env) {
		String boxClientId = env.getProperty(Utils.BOX_CLIENT_ID);

		if (Utils.isCurrentUserCPMAdmin(request, env)) {
			boxClientId = env.getProperty(Utils.BOX_ADMIN_CLIENT_ID);
		}
		return boxClientId;
	}

	/**
	 * get the Box Client secret based on user role
	 * 
	 * @param request
	 * @param env
	 * @return
	 */
	public static String getBoxClientSecret(HttpServletRequest request,
			Environment env) {
		String boxClientSecret = env.getProperty(Utils.BOX_CLIENT_SECRET);

		if (Utils.isCurrentUserCPMAdmin(request, env)) {
			boxClientSecret = env.getProperty(Utils.BOX_ADMIN_CLIENT_SECRET);
		}
		return boxClientSecret;
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
		/*
		 * if (Utils.isCurrentUserCPMAdmin(request, env)) { return
		 * env.getProperty(Utils.BOX_CLIENT_REDIRECT_URL) + "/adminAuthorized";
		 * } else { return env.getProperty(Utils.BOX_CLIENT_REDIRECT_URL) +
		 * "/authorized"; }
		 */
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
			String boxClientSecret) {
		BoxAPIConnection api = getBoxAPIConnection(boxAdminId, boxClientId,
				boxClientSecret);
		if (api != null) {
			try {
				BoxFolder folder = new BoxFolder(api, folderId);
				if ("Owner".equals(role)) {
					// CTools Owner user role
					folder.collaborate(userEmail, BoxCollaboration.Role.CO_OWNER);
				} else {
					// CTools other user roles, i.e. Member
					folder.collaborate(userEmail, BoxCollaboration.Role.VIEWER);
				}
			} catch (BoxAPIException e) {
				log.error("BoxUils:addCollaboration " + e.getResponse());
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
	public static BoxAPIConnection getBoxAPIConnection(String userId,
			String boxClientId, String boxClientSecret) {

		String boxAccessToken = getBoxAccessToken(userId);
		String boxRefreshToken = getBoxRefreshToken(userId);

		try {
			// make connection
			BoxAPIConnection api = new BoxAPIConnection(boxClientId,
					boxClientSecret, boxAccessToken, boxRefreshToken);
			api.setAutoRefresh(false);

			if (api.needsRefresh()) {
				api = refreshAccessAndRefreshTokens(userId, api);
			}
			return api;

		} catch (BoxAPIException e) {
			log.info("BoxUtils:addCollaboration " + e.toString());
			String response = e.getResponse();
			if (response.contains("Refresh token has expired")) {
				// time to refresh the refresh token
				// remove the locally stored token for the user
				// so that the user will need to go through the Box
				// authentication process again to generate refresh token and
				// access token
				boxRefreshTokens.remove(userId);
				boxAccessTokens.remove(userId);

			}
			return null;
		}
	}

}