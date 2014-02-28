package de.pseudonymisierung.mainzelliste;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import de.pseudonymisierung.mainzelliste.dto.Persistor;
import de.pseudonymisierung.mainzelliste.exceptions.InternalErrorException;
import de.pseudonymisierung.mainzelliste.exceptions.InvalidTokenException;
import de.pseudonymisierung.mainzelliste.matcher.MatchResult;
import de.pseudonymisierung.mainzelliste.matcher.MatchResult.MatchResultType;
import de.pseudonymisierung.mainzelliste.webservice.AddPatientToken;
import de.pseudonymisierung.mainzelliste.webservice.Token;

public enum PatientBackend {
	instance;
	
	private Logger logger = Logger.getLogger(this.getClass());
	/**
	 * PID request.
	 * Looks for a patient with the specified data in the database. If a match is found, the 
	 * ID of the matching patient is returned. If no match or possible match is found, a new
	 * patient with the specified data is created. If a possible match is found and the form
	 * has an entry "sureness" whose value can be parsed to true (by Boolean.parseBoolean()),
	 * a new patient is created. Otherwise, return null.
	 * @param tokenId
	 * @param form
	 * @return A map with the following members:
	 * 	<ul>
	 * 		<li> id: The generated id as an object of class ID. Null, if no id was generated due to an unsure match result.
	 * 		<li> result: Result as an object of class MatchResult. 
	 * @throws WebApplicationException if called with an invalid token.
	 */
	public IDRequest createNewPatient(
			String tokenId,
			MultivaluedMap<String, String> form) throws WebApplicationException {

		HashMap<String, Object> ret = new HashMap<String, Object>();
		// create a token if started in debug mode
		AddPatientToken t;

		Token tt = Servers.instance.getTokenByTid(tokenId);
		// Try reading token from session.
		if (tt == null) {
			// If no token found and debug mode is on, create token, otherwise fail
			if (Config.instance.debugIsOn())
			{
				Session s = Servers.instance.newSession();
				t = new AddPatientToken(null, "addPatient");
				Servers.instance.registerToken(s.getId(), t);
				tokenId = t.getId();
			} else {
				logger.error("No token with id " + tokenId + " found");
				throw new InvalidTokenException("Please supply a valid 'addPatient' token.");
			}
		} else { // correct token type?
			if (!(tt instanceof AddPatientToken)) {
				logger.error("Token " + tt.getId() + " is not of type 'addPatient' but '" + tt.getType() + "'");
				throw new InvalidTokenException("Please supply a valid 'addPatient' token.");
			} else {
				t = (AddPatientToken) tt;
			}
		}

		List<ID> returnIds = new LinkedList<ID>();
		MatchResult match;
		IDRequest request;

		// synchronize on token 
		synchronized (t) {
			/* Get token again and check if it still exist.
			 * This prevents the following race condition:
			 *  1. Thread A gets token t and enters synchronized block
			 *  2. Thread B also gets token t, now waits for A to exit the synchronized block
			 *  3. Thread A deletes t and exits synchronized block
			 *  4. Thread B enters synchronized block with invalid token
			 */
			
			t = (AddPatientToken) Servers.instance.getTokenByTid(tokenId);

			if(t == null){
				String infoLog = "Token with ID " + tokenId + " is invalid. It was invalidated by a concurrent request or the session timed out during this request.";
				logger.info(infoLog);
				throw new WebApplicationException(Response
					.status(Status.UNAUTHORIZED)
					.entity("Please supply a valid 'addPatient' token.")
					.build());
			}
			logger.info("Handling ID Request with token " + t.getId());
			Patient p = new Patient();
			Map<String, Field<?>> chars = new HashMap<String, Field<?>>();
			
			// get fields transmitted from MDAT server
			for (String key : t.getFields().keySet())
			{
				form.add(key, t.getFields().get(key));
			}
			
			Validator.instance.validateForm(form);
			
			for(String s: Config.instance.getFieldKeys()){
				chars.put(s, Field.build(s, form.getFirst(s)));
			}
	
			p.setFields(chars);
			
			// Normalisierung, Transformation
			Patient pNormalized = Config.instance.getRecordTransformer().transform(p);
			pNormalized.setInputFields(chars);
			
			match = Config.instance.getMatcher().match(pNormalized, Persistor.instance.getPatients());
			Patient assignedPatient; // The "real" patient that is assigned (match result or new patient) 
			
			// If a list of ID types is given in token, return these types
			Set<String> idTypes;
			idTypes = t.getRequestedIdTypes();
			if (idTypes.size() == 0) { // otherwise use the default ID type
				idTypes = new CopyOnWriteArraySet<String>();
				idTypes.add(IDGeneratorFactory.instance.getDefaultIDType());
			}

			switch (match.getResultType())
			{
			case MATCH :
				for (String idType : idTypes)
					returnIds.add(match.getBestMatchedPatient().getOriginal().getId(idType));
				
				assignedPatient = match.getBestMatchedPatient();
				// log token to separate concurrent request in the log file
				logger.info("Found match with ID " + returnIds.get(0).getIdString() + " for ID request " + t.getId()); 
				break;
				
			case NON_MATCH :
			case POSSIBLE_MATCH :
				if (match.getResultType() == MatchResultType.POSSIBLE_MATCH 
				&& (form.getFirst("sureness") == null || !Boolean.parseBoolean(form.getFirst("sureness")))) {
					return new IDRequest(p.getFields(), idTypes, match, null);
				}
				Set<ID> newIds = IDGeneratorFactory.instance.generateIds();			
				pNormalized.setIds(newIds);
				
				for (String idType : idTypes) {
					ID thisID = pNormalized.getId(idType);
					returnIds.add(thisID);				
					logger.info("Created new ID " + thisID.getIdString() + " for ID request " + (t == null ? "(null)" : t.getId()));
				}
				if (match.getResultType() == MatchResultType.POSSIBLE_MATCH)
				{
					pNormalized.setTentative(true);
					for (ID thisId : returnIds)
						thisId.setTentative(true);
					logger.info("New ID " + returnIds.get(0).getIdString() + " is tentative. Found possible match with ID " + 
							match.getBestMatchedPatient().getId(IDGeneratorFactory.instance.getDefaultIDType()).getIdString());
				}
				assignedPatient = pNormalized;
				break;
		
			default :
				logger.error("Illegal match result: " + match.getResultType());
				throw new InternalErrorException();
			}
			
			logger.info("Weight of best match: " + match.getBestMatchedWeight());
			
			request = new IDRequest(p.getFields(), idTypes, match, assignedPatient);
			
			ret.put("request", request);
			
			Persistor.instance.addIdRequest(request);
			
			if(t != null && ! Config.instance.debugIsOn())
				Servers.instance.deleteToken(t.getId());
		}
		// Callback aufrufen
		String callback = t.getDataItemString("callback");
		if (callback != null && callback.length() > 0)
		{
			try {
				logger.debug("Sending request to callback " + callback);
				HttpClient httpClient = new DefaultHttpClient();
				HttpPost callbackReq = new HttpPost(callback);
				callbackReq.setHeader("Content-Type", MediaType.APPLICATION_JSON);
				
				// Collect ids for Callback object
				JSONArray idsJson = new JSONArray(); 
				for (ID thisID : returnIds) {
						idsJson.put(thisID.toJSON()); 
				}

				/* FIXME FOlgendes für ApiVersion < 1.3
				JSONObject reqBody = new JSONObject()
						.put("tokenId", t.getId())
						//FIXME mehrere IDs zurückgeben -> bricht API, die ILF mitgeteilt wurde
						.put("id", returnIds.get(0).getIdString());
//						.put("id", id.toJSON());
 */
				JSONObject reqBody = new JSONObject()
					.put("tokenId", t.getId())
					.put("ids", idsJson);
				
				String reqBodyJSON = reqBody.toString();
				StringEntity reqEntity = new StringEntity(reqBodyJSON);
				reqEntity.setContentType("application/json");
				callbackReq.setEntity(reqEntity);				
				HttpResponse response = httpClient.execute(callbackReq);
				StatusLine sline = response.getStatusLine();
				// Accept callback if OK, CREATED or ACCEPTED is returned
				if ((sline.getStatusCode() < 200) || sline.getStatusCode() >= 300) {
					logger.error("Received invalid status form mdat callback: " + response.getStatusLine());
					throw new InternalErrorException("Request to callback failed!");
				}
						
				// TODO: Server-Antwort auslesen, Fehler abfangen.
			} catch (Exception e) {
				logger.error("Request to callback " + callback + "failed: ", e);
				throw new InternalErrorException("Request to callback failed!");
			}
		}
		return request;
	}
	

}