package edu.umich.its.cpm;

import lombok.Data;
import lombok.NonNull;

// Wrapper for results of API calls.  Will make the status code, optional message, and actual result
// available in standard JSON format. 


@Data
public class ApiResultWrapper {
	
	public final static int HTTP_SUCCESS = 200;
	public final static int HTTP_BAD_REQUEST = 400;
	public final static int HTTP_UNAUTHORIZED = 401;
	public final static int HTTP_FORBIDDEN = 403;
	public final static int HTTP_NOT_FOUND = 404;
	public final static int HTTP_GATEWAY_TMEOUT = 504;
	public final static int API_UNKNOWN_ERROR = 666;
	public final static int API_EXCEPTION_ERROR = 667;
	
	@NonNull
	Integer status;
	@NonNull
	String message;
	@NonNull
	String result;
}
