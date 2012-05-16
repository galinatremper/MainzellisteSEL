package de.unimainz.imbei.mzid.webservice;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import de.unimainz.imbei.mzid.Config;
import de.unimainz.imbei.mzid.Field;
import de.unimainz.imbei.mzid.ID;
import de.unimainz.imbei.mzid.IDGeneratorFactory;
import de.unimainz.imbei.mzid.PID;
import de.unimainz.imbei.mzid.Patient;
import de.unimainz.imbei.mzid.Servers;
import de.unimainz.imbei.mzid.dto.Persistor;
import de.unimainz.imbei.mzid.exceptions.NotImplementedException;
import de.unimainz.imbei.mzid.exceptions.UnauthorizedException;
import de.unimainz.imbei.mzid.matcher.FieldTransformer;
import de.unimainz.imbei.mzid.matcher.MatchResult;
import de.unimainz.imbei.mzid.matcher.Matcher;

/**
 * Resource-based access to patients.
 * 
 * @author Martin
 *
 */
@Path("/patients")
public class PatientsResource {
	
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public List<Patient> getAllPatients() throws UnauthorizedException {
		//1. Auth pr�fen: Falls nicht IDAT-Admin, UnauthorizedException werfen
		
		//2. Jeden Patienten aus der DB laden. Die m�ssen vom EntityManager abgekoppelt sein und nur Felder f�hren, die IDs sind.
	
		//3. Patienten in Liste zur�ckgeben.
		return Persistor.instance.getPatients();
	}

	@POST
	@Consumes(MediaType.APPLICATION_FORM_URLENCODED)
	@Produces(MediaType.APPLICATION_JSON)
	public ID newPatient(
			@QueryParam("tokenId") String tokenId,
			MultivaluedMap<String, String> form){
		Token t = Servers.instance.getTokenByTid(tokenId);
		if(t == null || !t.getType().equals("addPatient")){
			throw new WebApplicationException(Response
				.status(Status.UNAUTHORIZED)
				.entity("Please supply a valid 'addPatient' token.")
				.build());
		}
		
		Patient p = new Patient();
		Map<String, Field<?>> chars = new HashMap<String, Field<?>>();
		
		for(String s: form.keySet()){ //TODO: Testfall mit defekten/leeren Eingaben
			chars.put(s, Field.build(s, form.getFirst(s)));
		}

		p.setFields(chars);
		
/*		Patient pNormalized = new Patient();
		Map<String, Field<?>> normalizedChars = new HashMap<String, Field<?>>();
		for (String fieldName : chars.keySet())
		{
			FieldTransformer<?, ?> thisTransformer = Config.instance.getFieldTransformer(fieldName);
			if (thisTransformer != null)
				normalizedChars.put(fieldName, thisTransformer.transform(chars.get(fieldName)));
			else
				normalizedChars.put(fieldName, chars.get(fieldName));
		}
		*/
		MatchResult match = Config.instance.getMatcher().match(p, getAllPatients());
		
		ID id;
		switch (match.getResultType())
		{
		case MATCH :
			id = match.getPatient().getId("pid");
		case NON_MATCH :
			id = IDGeneratorFactory.instance.getFactory("pid").getNext(); //TODO: generalisieren
			
			Set<ID> ids = new HashSet<ID>();
			ids.add(id);
			p.setIds(ids);
			Persistor.instance.addPatient(p);
			
		case POSSIBLE_MATCH : 
		default :
			// TODO
			id = null;
		}
		
		Servers.instance.deleteToken(tokenId);
		
		return id;
	}
	
	@Path("/pid/{pid}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Response getPatientViaPid(
			@PathParam("pid") String pidString){
		//IDAT-Admin?
		PID pid = (PID) IDGeneratorFactory.instance.getFactory("pid").buildId(pidString);
		Patient pat = Persistor.instance.getPatient(pid);
		if(pat == null){
			throw new WebApplicationException(Response
					.status(Status.NOT_FOUND)
					.entity("There is no patient with PID " + pid + ".")
					.build());
		} else {
			return Response.status(Status.OK).entity(pat).build();
		}
	}
	
	@Path("/pid/{pid}")
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	public Response setPatientByPid(
			@PathParam("pid") String pid,
			Patient p){
		//IDAT-Admin?
		Persistor.instance.updatePatient(p);
		return Response
				.status(Status.NO_CONTENT)
				.build();
	}
	
	@Path("/tempid/{tid}")
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	public Patient getPatient(
			@PathParam("tid") String tid){
		//Hier keine Auth notwendig. Wenn tid existiert, ist der Nutzer dadurch autorisiert.
		//Patient mit TempID tid zur�ckgeben
		throw new NotImplementedException();
	}
	
	@Path("/tempid/{tid}")
	@PUT
	@Consumes(MediaType.APPLICATION_JSON)
	public void setPatientByTempId(
			@PathParam("tid") String tid,
			Patient p){
		//Hier keine Auth notwendig. Wenn tid existiert, ist der Nutzer dadurch autorisiert.
		//Charakteristika des Patients in DB mit TempID tid austauschen durch die von p
		throw new NotImplementedException();
	}
}
