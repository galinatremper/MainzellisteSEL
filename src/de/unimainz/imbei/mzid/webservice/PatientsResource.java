package de.unimainz.imbei.mzid.webservice;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import de.unimainz.imbei.mzid.Field;
import de.unimainz.imbei.mzid.Config;
import de.unimainz.imbei.mzid.ID;
import de.unimainz.imbei.mzid.IDGeneratorFactory;
import de.unimainz.imbei.mzid.Matcher;
import de.unimainz.imbei.mzid.PID;
import de.unimainz.imbei.mzid.Patient;
import de.unimainz.imbei.mzid.dto.Persistor;
import de.unimainz.imbei.mzid.exceptions.NotImplementedException;
import de.unimainz.imbei.mzid.exceptions.UnauthorizedException;

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
	public ID newPatient(MultivaluedMap<String, String> form){
		Patient p = new Patient();
		Map<String, Field<?>> chars = new HashMap<String, Field<?>>();
		
		for(String s: form.keySet()){ //TODO: Testfall mit defekten/leeren Eingaben
			chars.put(s, Field.build(s, form.getFirst(s)));
		}

		p.setFields(chars);
		
		//hier normalisieren
		
		PID match = Matcher.instance.match(p);
		
		if(match != null)
			return match;
		
		ID id = IDGeneratorFactory.instance.getFactory("pid").getNext(); //TODO: generalisieren
		
		Set<ID> ids = new HashSet<ID>();
		ids.add(id);
		p.setIds(ids);
		Persistor.instance.addPatient(p);
		
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
