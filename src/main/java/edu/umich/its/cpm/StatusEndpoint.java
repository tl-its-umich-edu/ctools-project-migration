package edu.umich.its.cpm;

import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

import java.util.Iterator;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.springframework.web.context.ServletContextAware;

import org.springframework.core.env.Environment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;

/**
 * this is to add a new status end point Return a page with application version
 * information, a list of other available status URLs for this instance of the
 * application, and a list of, or pointers to, specific external points of
 * integration for this installation and can potentially have information such
 * as how to diagnose connection issues
 * 
 * @author zqian
 *
 */
@PropertySource("file:${catalina.base:/usr/local/ctools/app/ctools/tl}/home/application.properties")
@Component
public class StatusEndpoint implements Endpoint<List<String>>, ServletContextAware{

	private static ServletContext servletContext = null;
	
	@Autowired
	private Environment env;
	
	public String getId() {
		return "status";
	}

	public boolean isEnabled() {
		return true;
	}

	public boolean isSensitive() {
		return true;
	}

	public List<String> invoke() {
		// Custom logic to build the output
		List<String> messages = new ArrayList<String>();
		messages.add("The status page:");
		
		try {
			String name = "/META-INF/MANIFEST.MF";
			Properties props = new Properties();
			props.load(servletContext.getResourceAsStream(name));
			// output the git version, CTools and Box url 
			messages.add("GIT version: " + (String) props.get("git-SHA-1"));
			messages.add("CTools server: " + env.getProperty("ctools.direct.url"));
			messages.add("Box server:" + env.getProperty("box_api_url"));
			
		} catch (Throwable e) {
			e.printStackTrace();
		}
		
		return messages;
	}
	
	public void setServletContext(ServletContext servletContext){
		this.servletContext = servletContext;
	}
	
}