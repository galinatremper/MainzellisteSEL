package de.pseudonymisierung.mainzelliste.exceptions;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class InvalidTokenException extends WebApplicationException {

	public InvalidTokenException(String message) {
		super(Response.status(Status.BAD_REQUEST).entity(message).build());
	}
}
