package de.unimainz.imbei.mzid.dto;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;

import org.apache.openjpa.persistence.query.OpenJPAQueryBuilder;

import de.unimainz.imbei.mzid.ID;
import de.unimainz.imbei.mzid.PID;
import de.unimainz.imbei.mzid.Patient;
import de.unimainz.imbei.mzid.exceptions.NotImplementedException;

/**
 * Handles reading and writing from and to the database.
 * 
 * @author Martin Lablans
 */
public enum Persistor {
	instance;
	
	private EntityManagerFactory emf = Persistence.createEntityManagerFactory("mzid");
	
	public Patient getPatient(PID pid){
		EntityManager em = emf.createEntityManager();
		Query q = em.createQuery("SELECT p FROM Patient p JOIN p.ids id WHERE id.idString = :idString");
		q.setParameter("idString", pid.getIdString());
		List<Patient> result = q.getResultList();
		em.close();
		return result.get(0);
	}
	
	public List<Patient> getPatients() { //TODO: Filtern
		EntityManager em = emf.createEntityManager();
		List<Patient> pl = em.createQuery("select p from Patient p", Patient.class).getResultList();
		em.close(); // causes all entities to be detached
		return pl;
	}

	public synchronized void addPatient(Patient p){
		EntityManager em = emf.createEntityManager();
		em.getTransaction().begin();
		em.persist(p); //TODO: Fehlerbehandlung, falls PID schon existiert.
		em.getTransaction().commit();
		em.close();
	}
	
	public synchronized void updatePatient(Patient p){
		throw new NotImplementedException();
	}
}
