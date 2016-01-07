package edu.umich.its.cpm;

import org.springframework.boot.actuate.endpoint.Endpoint;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.ArrayList;

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
@Component
public class StatusEndpoint implements Endpoint<List<String>> {

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
		messages.add("This is message 1");
		messages.add("This is message 2");
		return messages;
	}
}