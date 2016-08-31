package edu.umich.its.cpm;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.HashMap;

/**
 * Created by pushyami on 8/26/16.
 */
public class AttachmentHandler {
    private static final Logger log = LoggerFactory.getLogger(AttachmentHandler.class);
    private HttpServletRequest request;

    @Autowired
    private Environment env;

    public Environment getEnv() {
        return env;
    }

    public void setEnv(Environment env) {
        this.env = env;
    }

    public AttachmentHandler(HttpServletRequest req){
        this.request =req;
    }

    public byte[] getAttachmentContent(String attachmentUrl) {

        byte[] attachmentContent = null;
        String currentUserId = Utils.getCurrentUserId(request, env);
        HashMap<String, Object> sessionAttributes = Utils.login_becomeuser(env, request, currentUserId);
        if(sessionAttributes.isEmpty()){
            log.error("Logging into Ctools failed for the user: "+currentUserId);
            return attachmentContent;
        }
        String sessionId = (String) sessionAttributes.get(Utils.SESSION_ID);
        HttpContext httpContext = (HttpContext) sessionAttributes.get("httpContext");
        HttpClient httpClient = HttpClientBuilder.create().build();
        try {
            HttpGet request = new HttpGet(attachmentUrl + "?_sessionId=" + sessionId);
            request.setHeader("Content-Type", "application/x-www-form-urlencoded");
            HttpResponse response = httpClient.execute(request, httpContext);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                log.error(String.format("Failure to read the attachment \"%1$s\" content in a Email Message " +
                        "with status code %2$d ", attachmentUrl,statusCode ));
                return attachmentContent;
            }
            attachmentContent = EntityUtils.toByteArray(response.getEntity());

        } catch (IOException e) {
            log.error("Failure in reading the attachment content for the Email Message" + e);
        }
        return attachmentContent;
    }

}
