/*
 * Copyright (C) 2013-2015 Martin Lablans, Andreas Borg, Frank Ückert
 * Contact: info@mainzelliste.de
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free 
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT 
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS 
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more 
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License 
 * along with this program; if not, see <http://www.gnu.org/licenses>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 *
 * If you modify this Program, or any covered work, by linking or combining it 
 * with Jersey (https://jersey.java.net) (or a modified version of that 
 * library), containing parts covered by the terms of the General Public 
 * License, version 2.0, the licensors of this Program grant you additional 
 * permission to convey the resulting work.
 */
package de.pseudonymisierung.mainzelliste.dto;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.NoResultException;
import javax.persistence.Persistence;
import javax.persistence.TypedQuery;

import org.apache.log4j.Logger;


import de.pseudonymisierung.mainzelliste.Config;
import de.pseudonymisierung.mainzelliste.ID;
import de.pseudonymisierung.mainzelliste.IDGeneratorMemory;
import de.pseudonymisierung.mainzelliste.IDRequest;
import de.pseudonymisierung.mainzelliste.Patient;
import de.pseudonymisierung.mainzelliste.exceptions.InternalErrorException;

/**
 * Handles reading and writing from and to the database. Implemented as a
 * singleton object, which can be referenced by Persistor.instance.
 */
public enum Persistor {
	
	/** The singleton instance. */
	instance;
	
	/** Factory for EntityManager. */
	private EntityManagerFactory emf;
	/** EntityManager. Instance that stays open (for cases where entities cannot be detached). */
	private EntityManager em;
	
	/** The logging instance. */
	private Logger logger = Logger.getLogger(this.getClass());
	
	/** Creates the singleton instance with the configured database connection. */
	private Persistor() {
		
		this.initPropertiesTable();
		
		HashMap<String, String> persistenceOptions = new HashMap<String, String>();
		
		// Settings from config
		persistenceOptions.put("javax.persistence.jdbc.driver", Config.instance.getProperty("db.driver"));
		persistenceOptions.put("javax.persistence.jdbc.url", Config.instance.getProperty("db.url"));
		persistenceOptions.put("javax.persistence.jdbc.user", Config.instance.getProperty("db.username"));
		persistenceOptions.put("javax.persistence.jdbc.password", Config.instance.getProperty("db.password"));
		
		// Other settings
		persistenceOptions.put("openjpa.jdbc.SynchronizeMappings", "buildSchema");
		persistenceOptions.put("openjpa.jdbc.DriverDataSource", "dbcp");
		persistenceOptions.put("openjpa.ConnectionProperties", "testOnBorrow=true, validationQuery=SELECT 1");
		
		emf = Persistence.createEntityManagerFactory("mainzelliste", persistenceOptions);
		em = emf.createEntityManager();
		
		new org.apache.openjpa.jdbc.schema.DBCPDriverDataSource();
		
		// update database schema (post-JPA)
		String dbVersion = this.getSchemaVersion();
		this.updateDatabaseSchemaJPA(dbVersion);
		
		// Check database connection
		getPatients();
		
		Logger.getLogger(Persistor.class).info("Persistence has initialized successfully.");
	}
	
	/**
	 * Get a patient by one of its IDs.
	 * 
	 * @param pid
	 *            An identifier of the patient to get.
	 * @return The patient with the given ID or null if no patient with the
	 *         given ID exists.
	 * 
	 */
	public Patient getPatient(ID pid){
		EntityManager em = emf.createEntityManager();
		TypedQuery<Patient> q = em.createQuery("SELECT p FROM Patient p JOIN p.ids id WHERE id.idString = :idString AND id.type = :idType", Patient.class);
		q.setParameter("idString", pid.getIdString());
		q.setParameter("idType", pid.getType());
		List<Patient> result = q.getResultList();
		if (result.size() > 1) {
			logger.fatal("Found more than one patient with ID: " + pid.toString());
			throw new InternalErrorException("Found more than one patient with ID: " + pid.toString());
		} 
		
		if (result.size() == 0)
			return null;

		Patient p = result.get(0);
		// Fetch lazy loaded IDs
		p.getIds();
		p.getOriginal().getIds();
		//em.refresh(p.getOriginal());
		em.close();
		return p;
	}
	
	/**
	 * Returns all patients currently persisted in the patient list. This is not
	 * a copy! Caller MUST NOT perform write operations on the return value or
	 * its linked objects.
	 * 
	 * @return All persisted patients.
	 */
	public synchronized List<Patient> getPatients() { //TODO: Filtern
		// Entities are not detached, because the IDs are lazy-loaded
		List<Patient> pl;
		pl = this.em.createQuery("select p from Patient p", Patient.class).getResultList();
		return pl;
	}

	/**
	 * Check whether a patient with the given ID exists.
	 * 
	 * @param idType
	 *            The ID type.
	 * @param idString
	 *            The ID string.
	 * @return The patient with the given ID or null if no patient with the
	 *         given ID exists.
	 */
	public boolean patientExists(String idType, String idString) {
		EntityManager em = emf.createEntityManager();
		TypedQuery<Long> q = em.createQuery("SELECT COUNT(p) FROM Patient p JOIN p.ids id WHERE id.idString = :idString AND id.type = :idType", Long.class);
		q.setParameter("idString", idString);
		q.setParameter("idType", idType);
		Long count = q.getSingleResult();
		if (count > 0)
			return true;
		else 
			return false;
	}
	
	/**
	 * Check whether a patient with the given ID exists.
	 * 
	 * @param id
	 *            The ID to check.
	 * @return true if a patient with the given ID exists.
	 */
	public boolean patientExists(ID id) {
		return this.patientExists(id.getType(), id.getIdString());
	}

	/**
	 * Returns a detached list of the IDs of all patients.
	 * 
	 * @return A list where every item represents the IDs of one patient.
	 */
	public synchronized List<Set<ID>> getAllIds() {
		List<Patient> patients = this.getPatients();
		List<Set<ID>> ret = new LinkedList<Set<ID>>();
		for (Patient p : patients) {
			Set<ID> thisPatientIds = p.getIds();
			this.em.detach(thisPatientIds);
			ret.add(thisPatientIds);
		}
		return ret;
	}

	/**
	 * Add an ID request to the database. In cases where a new ID is created, a
	 * new Patient object is persisted.
	 * 
	 * @param req
	 *            The ID request to persist.
	 */
	public synchronized void addIdRequest(IDRequest req) {
		em.getTransaction().begin();
		em.persist(req); //TODO: Fehlerbehandlung, falls PID schon existiert.		
		em.getTransaction().commit();
	}
	
	/**
	 * Update the persisted properties of an ID generator (e.g. the counter from
	 * which PIDs are generated).
	 * 
	 * @param mem
	 *            The properties to persist.
	 */
	public synchronized void updateIDGeneratorMemory(IDGeneratorMemory mem) {
		EntityManager em = emf.createEntityManager();
		em.getTransaction().begin();
		em.merge(mem);
		em.getTransaction().commit();
		em.close();
	}
	
	/**
	 * Mark a patient as duplicate of another.
	 * 
	 * @param idOfDuplicate
	 *            ID of the patient to be marked as duplicate.
	 * @param idOfOriginal
	 *            ID of the patient of which the other one is a duplicate.
	 *            
	 * @see de.pseudonymisierung.mainzelliste.Patient#isDuplicate()
	 * @see de.pseudonymisierung.mainzelliste.Patient#getOriginal()
	 * @see de.pseudonymisierung.mainzelliste.Patient#setOriginal(Patient)
	 */
	public synchronized void markAsDuplicate(ID idOfDuplicate, ID idOfOriginal) {
		Patient pDuplicate = getPatient(idOfDuplicate);
		Patient pOriginal = getPatient(idOfOriginal);
		pDuplicate.setOriginal(pOriginal);
		updatePatient(pDuplicate);
	}
	
	/**
	 * Load the persisted properties for an ID generator.
	 * 
	 * @param idType
	 *            Identifier of the ID generator.
	 * @return The persisted properties or null if no properties have been
	 *         persisted for the given ID generator.
	 */
	public IDGeneratorMemory getIDGeneratorMemory(String idType) {
		EntityManager em = emf.createEntityManager();
		TypedQuery<IDGeneratorMemory> q = em.createQuery("SELECT m FROM IDGeneratorMemory m WHERE m.idType = :idType", IDGeneratorMemory.class);
		q.setParameter("idType", idType);
		try {
			IDGeneratorMemory result = q.getSingleResult();
			em.close();
			return result;
		} catch (NoResultException e) { // No result -> No IDGeneratorMemory object persisted yet.
			return null;
		}
	}
	
	/**
	 * Persist changes made to a patient.
	 * 
	 * @param p
	 *            The patient to persist.
	 */
	public synchronized void updatePatient(Patient p) {
		EntityManager em = emf.createEntityManager();
		em.getTransaction().begin();
		em.merge(p);
		em.getTransaction().commit();
		em.close();
		/* 
		 * Recreate open EntityManager so that getPatients() re-reads from 
		 * the database instead of returning objects from the cache 
		 */
		this.em.close();
		this.em = this.emf.createEntityManager();
	}
	
	/**
	 * Remove a patient from the database.
	 * 
	 * @param id An ID of the patient to persist.
	 */
	public synchronized void deletePatient(ID id) {
		em.getTransaction().begin();
		TypedQuery<Patient> q = em.createQuery("SELECT p FROM Patient p JOIN p.ids id WHERE id.idString = :idString AND id.type = :idType", Patient.class);
		q.setParameter("idString", id.getIdString());
		q.setParameter("idType", id.getType());
		Patient p = q.getSingleResult();
		if (p != null)
			em.remove(p);
		em.getTransaction().commit();
	}
	
	/**
	 * Performs database updates after JPA initialization.
	 * @param fromVersion The version from which to update.
	 */
	private void updateDatabaseSchemaJPA(String fromVersion)
	{
		EntityManager em = emf.createEntityManager();

		if ("1.0".equals(fromVersion)) { // 1.0 -> 1.1
			em.getTransaction().begin();
			em.createNativeQuery("UPDATE IDGeneratorMemory SET idType=idstring").executeUpdate();
			em.createNativeQuery("ALTER TABLE IDGeneratorMemory DROP COLUMN idString").executeUpdate();

			/*
			 * Delete invalid instances of IDGeneratorMemory (caused by Bug #3007
			 * Both id generators of version 1.0 use a field "counter". The memory object
			 * with the highest value has to be retained.
			 */
			List<String> idTypes = em.createQuery("SELECT DISTINCT m.idType FROM IDGeneratorMemory m", String.class).getResultList();
			for (String idType: idTypes) {
				if (idType != null) {
					TreeMap<Integer, IDGeneratorMemory> genMap = new TreeMap<Integer, IDGeneratorMemory>();
					List<IDGeneratorMemory> generators = em.createQuery("SELECT im FROM IDGeneratorMemory im WHERE im.idType = :idType", IDGeneratorMemory.class)
							.setParameter("idType", idType).getResultList();
					for (IDGeneratorMemory thisGen : generators) {
						genMap.put(Integer.parseInt(thisGen.get("counter")), thisGen);
					}
					// Remove the object with the highest counter. This should be kept. 
					genMap.pollLastEntry();
					// Remove the others.
					for (IDGeneratorMemory thisGen : genMap.values()) {
						em.remove(thisGen);
					}
				}
			}
			this.setSchemaVersion("1.1", em);
			fromVersion = "1.1";
			em.getTransaction().commit();
		} // End of update 1.0 -> 1.1
		if ("1.1".equals(fromVersion)) { // 1.1 -> 1.3.1
			em.getTransaction().begin();
			// Add index on idString for more efficient access to patients by ID
			em.createNativeQuery("CREATE INDEX i_id_idstring ON ID (idString)").executeUpdate();

			// Update schema version. Corresponds to Mainzelliste version, therefore the gap
			this.setSchemaVersion("1.3.1", em);
			fromVersion = "1.3.1";
			
			em.getTransaction().commit();
		} // End of update 1.1 -> 1.3.1
		
		// Update schema version to release version, even if no changes are necessary
		em.getTransaction().begin();
		this.setSchemaVersion(Config.instance.getVersion(), em);
		em.getTransaction().commit();
	}
	
	/**
	 * Reads the release version from the database (1.0 is assumed if
	 * this information cannot be found).
	 * 
	 * This function does not make use of JPA in order to be compatible
	 * with updates that have to be made before JPA initialization 
	 * (e.g. if the Object-DB mapping would be broken without the update).
	 * 
	 * Run initPropertiesTable() first to ensure that version information exists.
	 * 
	 * @return The persisted release version.
	 */
	private String getSchemaVersion() {
		Connection conn;
		Properties connectionProps = new Properties();
		connectionProps.put("user",  Config.instance.getProperty("db.username"));
		connectionProps.put("password",  Config.instance.getProperty("db.password"));
		String url = Config.instance.getProperty("db.url");
		try {
			Class.forName(Config.instance.getProperty("db.driver"));
			conn = DriverManager.getConnection(url, connectionProps);
			ResultSet rs = conn.createStatement().executeQuery("SELECT value FROM mainzelliste_properties " +
					"WHERE property='version'");
			if (!rs.next()) {
				logger.fatal("Properties table not initialized correctly!");
				throw new Error("Properties table not initialized correctly!");	
			}				
			return rs.getString("value");
		} catch (SQLException e) {
			logger.fatal("Could not update database schema!", e);
			throw new Error(e);			
		} catch (ClassNotFoundException e) {
			logger.fatal("Could not find database driver!", e);
			throw new Error(e);
		}			
	}
	
	/**
	 * Update version information in the database. Should be run in one
	 * transaction on the provided EntityManager together with the changes made
	 * for this version so that no inconsistencies arise if any of the update
	 * statements fail.
	 * 
	 * @param toVersion
	 *            The version string to set.
	 * @param em
	 *            A valid EntityManager object.
	 */
	private void setSchemaVersion(String toVersion, EntityManager em) {
		em.createNativeQuery("UPDATE mainzelliste_properties SET value='" + toVersion + 
				"' WHERE property='version'").executeUpdate(); 
	}
	
	/**
	 * Create mainzelliste_properties if not exists. Check if JPA schema was
	 * initialized. If no, set version to current, otherwise, it is assumed that
	 * the database schema was created by version 1.0 (where the properties
	 * table did not exist) and this version is set.
	 * 
	 * Must be called before JPA initialization, i.e. before an EntityManager is
	 * created.
	 */
	private void initPropertiesTable() {
		Connection conn;
		Properties connectionProps = new Properties();
		connectionProps.put("user",  Config.instance.getProperty("db.username"));
		connectionProps.put("password",  Config.instance.getProperty("db.password"));
		String url = Config.instance.getProperty("db.url");
		try {
			Class.forName(Config.instance.getProperty("db.driver"));
			conn = DriverManager.getConnection(url, connectionProps);
			// Check if there is a properties table 
			DatabaseMetaData metaData = conn.getMetaData();
			// Look for patients table to determine if schema is yet to be created
			String tableName;
			if (metaData.storesLowerCaseIdentifiers())
				tableName = "patient";
			else
				tableName = "Patient";
			ResultSet rs = metaData.getTables(null, null, tableName, null);
			boolean firstRun = !rs.next(); // First invocation with this database 
			
			// Check if there is a properties table 
			metaData = conn.getMetaData();
			// "value" is a reserved word so it must be quoted in SQL queries
			String identifierQuote = metaData.getIdentifierQuoteString();
			String valueQuoted = identifierQuote + "value" + identifierQuote;
			logger.debug("Quoted: " + valueQuoted);
			rs = metaData.getTables(null, null, "mainzelliste_properties", null);
			// Assume version 1.0 if none is provided
			if (!rs.next()) {
				// Create table				
				conn.createStatement().execute("CREATE TABLE mainzelliste_properties" +
						"(property varchar(256), " + valueQuoted +" varchar(256))");
			} 
			rs = conn.createStatement().executeQuery("SELECT " + valueQuoted + 
					" FROM mainzelliste_properties WHERE property='version'");
			if (!rs.next()) {
				// Properties table exists, but no version information
				String setVersion = firstRun ? Config.instance.getVersion() : "1.0";
				conn.createStatement().execute("INSERT INTO mainzelliste_properties" +
						"(property, value) VALUES ('version', '" + setVersion + "')");
			}
		} catch (SQLException e) {
			logger.fatal("Could not update database schema!", e);
			throw new Error(e);			
		} catch (ClassNotFoundException e) {
			logger.fatal("Could not find database driver!", e);
			throw new Error(e);
		}			
	}
}