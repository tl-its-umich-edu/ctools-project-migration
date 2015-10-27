package edu.umich.its.cpm;

import javax.ws.rs.WebApplicationException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;

public class MigrationNotFoundException extends WebApplicationException {

	/**
	 * Create a HTTP 404 (Not Found) exception.
	 */
	public MigrationNotFoundException() {
		super(Response.status(Response.Status.NOT_FOUND)
				.type(MediaType.TEXT_PLAIN).build());
	}

	/**
	 * Create a HTTP 404 (Not Found) exception.
	 * 
	 * @param message
	 *            the String that is the entity of the 404 response.
	 */
	public MigrationNotFoundException(String message) {
		super(Response.status(Response.Status.NOT_FOUND).entity(message)
				.type(MediaType.TEXT_PLAIN).build());
	}
}
