package edu.umich.its.cpm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
import org.springframework.http.ResponseEntity;
//import org.springframework.http.HttpStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;

import org.json.JSONObject;
import org.json.JSONArray;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIRequest;
import com.box.sdk.BoxAPIResponse;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxItem;
import com.box.sdk.BoxItem.Info;
import com.box.sdk.BoxUser;

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

	private static String box_access_token = null;

	private static String box_refresh_token = null;
	
	private static final String BOX_CLIENT_ID = "box_client_id";
	private static final String BOX_CLIENT_SECRET = "box_client_secret";
	
	/**
	 * get Box access token
	 */
	public static String getBoxAccessToken()
	{
		return box_access_token;
	}
	
	/**
	 * set Box access token
	 */
	public static void setBoxAccessToken(String boxAccessToken)
	{
		box_access_token = boxAccessToken;
	}
	
	
	/**
	 * get Box refresh token
	 */
	public static String getBoxRefreshToken()
	{
		return box_refresh_token;
	}
	
	/**
	 * set Box refresh token
	 */
	public static void setBoxRefreshToken(String boxRefreshToken)
	{
		box_refresh_token = boxRefreshToken;
	}
	
	private static final SimpleDateFormat date_formatter = new SimpleDateFormat(
			"yyyy-MM-dd-hh.mm.ss");

	private static final Logger log = LoggerFactory.getLogger(BoxUtils.class);
	
	public static void getCurrentUser(BoxAPIConnection connection) {
		BoxUser user = BoxUser.getCurrentUser(connection);
		BoxUser.Info info = user.getInfo();
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
	public static String getAuthCodeFromBoxCallback(HttpServletRequest request, String boxClientId, String boxClientSecret, String boxTokenUrl) {
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
		
		if (authCode != null)
		{
			// now that we have the authCode, use it to get access token and fresh token
		    // Returns a new access_token & refresh_token from an existing refresh_token
		    // Each access_token is valid for 1 hour. In order to get a new, valid token, you can use the accompanying
		    // refresh_token. Each refresh token is valid for 14 days. Every time you get a new access_token by using a
		    // refresh_token, we reset your timer for the 14 day period. This means that as long as your users use your
		    // application once every 14 days, their login is valid forever.
		    // Args:
		    //    - client_id: The client_id you obtained in the initial setup.
		    //    - client_secret: The client_secret you obtained in the initial setup.
		    //    - code: a string containing the code, or a dictionary containing the GET query
		    // Returns:
		    //    - a dictionary with the token and additional info
			try
			{
				CloseableHttpClient httpclient = HttpClients.createDefault();
				HttpPost httpPost = new HttpPost(boxTokenUrl);
				List <NameValuePair> nvps = new ArrayList <NameValuePair>();
				nvps.add(new BasicNameValuePair("grant_type", "authorization_code"));
				nvps.add(new BasicNameValuePair("client_id", boxClientId));
				nvps.add(new BasicNameValuePair("client_secret", boxClientSecret));
				nvps.add(new BasicNameValuePair("code", authCode));
				httpPost.setEntity(new UrlEncodedFormEntity(nvps));
				CloseableHttpResponse response = httpclient.execute(httpPost);

				try {
					StatusLine statusLine = response.getStatusLine();
				    log.info("token request status" + statusLine);
				    if (statusLine.getStatusCode() == HttpStatus.SC_OK)
				    {
				    	// if success, get the access_token and refresh_token
					    HttpEntity entity = response.getEntity();
					    InputStream body = entity.getContent();
						String theString = IOUtils.toString(body, "UTF-8");
						JSONObject obj = new JSONObject(theString);
						setBoxAccessToken((String) obj.get("access_token"));
						setBoxRefreshToken((String) obj.get("refresh_token"));
					
						// close inputstream and entity
						IOUtils.closeQuietly(body);
					    EntityUtils.consume(entity);
				    }
				} finally {
				    response.close();
				}
			}
			catch (java.net.MalformedURLException exception)
			{
				log.error("getAuthCodeFromBoxCallback MalformedURLException " + boxTokenUrl);
			}
			catch (java.io.IOException exception)
			{
				log.error("getAuthCodeFromBoxCallback IOException " + boxTokenUrl);
			}
		}

		return authCode;
	}
	
	/**
	 * method to return json list of Box folders
	 */
	public static List<HashMap<String, String>> getBoxFolders(String boxClientId, String boxClientSecret)
	{
		String boxAccessToken = getBoxAccessToken();
		String boxRefreshToken = getBoxRefreshToken();
		
		try
		{
			// make connection
			BoxAPIConnection api = new BoxAPIConnection(boxClientId,
					boxClientSecret, boxAccessToken, boxRefreshToken);
			// If the access token expires, you will have to manually refresh it.
			api.refresh();
	
			// get the root Box folder
			BoxFolder rootFolder = BoxFolder.getRootFolder(api);
			
			// get list of properties from all Box items contained
			List<HashMap<String, String>> folderItems = BoxUtils.listBoxFolders(
					null, api, rootFolder, "", 0);
			
			setBoxAccessToken(api.getAccessToken());
			setBoxRefreshToken(api.getRefreshToken());
			return folderItems;
		}
		catch (Exception e)
		{
			log.info("BoxUtils:getBoxFolders " + e.toString());
		}
		return null;
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
				String currentFolderPath = folderPath + "/"
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
			rv = date_formatter.format(date);
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
}