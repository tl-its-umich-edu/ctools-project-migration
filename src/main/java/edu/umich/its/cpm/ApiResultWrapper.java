package edu.umich.its.cpm;

import lombok.Data;
import lombok.NonNull;

// Wrapper for results of API calls.  Will make the status code, status message, and actual result
// available in standard JSON format. 


@Data
public class ApiResultWrapper {

	// For established Http status codes use another sources such as 
	// import org.springframework.http.HttpStatus;
	// These are additions so that we can use the ApiResultWrapper for request that end
	// up with a non-standard status.
	public final static int API_UNKNOWN_ERROR = 666;
	public final static int API_EXCEPTION_ERROR = 667;
	
	@NonNull
	Integer status;
	@NonNull
	String message;
	@NonNull
	String result;
}
