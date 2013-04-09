package de.unimainz.imbei.mzid;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;
import javax.persistence.ManyToOne;
import javax.persistence.PostLoad;
import javax.persistence.PrePersist;
import javax.persistence.PreUpdate;
import javax.persistence.Transient;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.log4j.Logger;
import org.apache.openjpa.persistence.jdbc.ElementIndex;
import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import de.unimainz.imbei.mzid.exceptions.CircularDuplicateRelationException;
import de.unimainz.imbei.mzid.exceptions.InternalErrorException;

@XmlRootElement
@Entity
public class Patient {
	/**
	 * Internal ID. Set by JPA on first persistence.
	 */
	@Id
	@GeneratedValue
	@JsonIgnore
	private int patientJpaId; // JPA
	

	/**
	 * Returns the internal ID of the persistency engine.
	 * Needed to determine if two Patient object refer to the same database entry.
	 * @return the patientJpaId
	 */
	public int getPatientJpaId() {
		return patientJpaId;
	}

	/**
	 * Set of IDs for this patient.
	 */
	@OneToMany(cascade={CascadeType.DETACH, CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REFRESH}, fetch=FetchType.LAZY)
	private Set<ID> ids;
	
	/**
	 * The map of fields of this patient. Field names are map keys, the corresponding
	 * Field objects are the values.
	 * The fields are not persisted by this map (therefore the {@literal @Transient} annotation),
	 * as this leads to poor performance. Instead, fields are serialized by {@link #prePersist()} and
	 * deserialized by {@link #postLoad()} upon saving to and loading from the database. 
	 * @see #fieldsString 
	 */
	@Transient
	private Map<String, Field<?>> fields;
	
	/**
	 * Serialization of the fields as JSON string for efficient storage in the database.
	 * @see #fields
	 */
	@Column(columnDefinition="text",length=-1)
	@JsonIgnore
	private String fieldsString;
	
	/**
	 * Serialization of the input fields as JSON string for efficient storage in the database.
	 * @see #inputFields
	 */
	@Column(length=4096)
	@JsonIgnore
	private String inputFieldsString;
	
	/**
	 * Serializes the fields and input fields into JSON strings. Automatically called by
	 * the JPA engine right before saving the object to the database. 
	 */
	@PrePersist
	@PreUpdate
	public void prePersist() {
		this.fieldsString = fieldsToString(this.fields);
		this.inputFieldsString = fieldsToString(this.inputFields);
	}
	
	/**
	 * Deserializes fields and input fields from their JSON representation. Automatically
	 * called by the JPA engine right after loading the object from the database.
	 */
	@PostLoad
	public void postLoad() {
		this.fields = stringToFields(this.fieldsString);
		this.inputFields = stringToFields(this.inputFieldsString);
	}
	
	/**
	 * JSON serialization of a map of fields. Backend function for {@link #prePersist()}.
	 * @param fields Map of fields to serialize.
	 * @see #stringToFields(String)
	 */
	public static String fieldsToString(Map<String, Field<?>> fields) {
		try {
			JSONObject fieldsJson = new JSONObject();
			for (String fieldName : fields.keySet())
			{
				JSONObject thisField = new JSONObject();
				thisField.put("class", fields.get(fieldName).getClass().getName());
				thisField.put("value", fields.get(fieldName).getValueJSON());
				fieldsJson.put(fieldName, thisField);
			}
			return fieldsJson.toString();
		} catch (JSONException e) {
			Logger.getLogger(Patient.class).error("Exception: ", e);
			throw new InternalErrorException();
		}
	}
	
	/**
	 * Creates a map of fields from its JSON representation. 
	 * @param fieldsJsonString JSON string as created by {@link #fieldsToString(Map)}.
	 */
	public static Map<String, Field<?>> stringToFields(String fieldsJsonString) {
		try {
			Map<String, Field<?>> fields = new HashMap<String, Field<?>>();
			JSONObject fieldsJson = new JSONObject(fieldsJsonString);
			Iterator it = fieldsJson.keys();
			while(it.hasNext()) {
				String fieldName = (String) it.next();
				JSONObject thisFieldJson = fieldsJson.getJSONObject(fieldName); 
				String fieldClass = thisFieldJson.getString("class");
				String fieldValue = thisFieldJson.getString("value");
				Field<?> thisField = (Field) Class.forName(fieldClass).newInstance();
				thisField.setValue(fieldValue);
				fields.put(fieldName, thisField);
			} 
			return fields;
		} catch (Exception e) {
			Logger.getLogger(Patient.class).error("Exception: ", e);
			throw new InternalErrorException();
		}
	}
	
	/**
	 * Input fields as read from form (before transformation).
	 * @see #fields
	 * @see #inputFieldsString
	 */
	@Transient
	private Map<String, Field<?>> inputFields;
	
	@Transient
	@JsonIgnore
	private Logger logger = Logger.getLogger(this.getClass());
	
	@ElementCollection(fetch=FetchType.LAZY)
	@ElementIndex(name="blockInd")
	private Map<String, String> blockingFields = new HashMap<String, String>();
	
	private void updateBlockingFields() {
		String geburtsmonat = this.fields.get("geburtsmonat").toString();
		String geburtsjahr = this.fields.get("geburtsjahr").toString();
		String geburtstag = this.fields.get("geburtstag").toString();
		
		this.blockingFields.put("geburtsmonat", geburtsmonat);
		this.blockingFields.put("geburtstag", geburtstag);
		this.blockingFields.put("geburtsjahr", geburtsjahr);
//		this.blockingFields.put("geburtsmonat_geburtsjahr", geburtsmonat + "_" + geburtsjahr);
//		this.blockingFields.put("geburtstag_geburtsjahr", geburtstag + "_" + geburtsjahr);
//		this.blockingFields.put("geburtstag_geburtsmonat", geburtstag + "_" + geburtsmonat);
	}
	/**
	 * Returns the input fields, i.e. as they were transmitted in the last request that
	 * modified this patient, before transformations.
	 * @return Map with field names as keys and the corresponding Field objects as values.
	 */
	public Map<String, Field<?>> getInputFields() {
		return inputFields;
	}

	/**
	 * Set the input fields. Whenever a request modifies this patient object (or upon creation)
	 * the input fields as transmitted in the request, before transformation, should be set
	 * with this method. This allows redisplaying them in the admin interface.
	 * @param inputFields Map with field names as keys and corresponding Field objects as values.
	 */
	public void setInputFields(Map<String, Field<?>> inputFields) {
		this.inputFields = inputFields;
	}

	/**
	 * True if this patient is suspected to be a duplicate.
	 */
	private boolean isTentative = false;

	/**
	 * Check if this patient is suspectedly a duplicate of another one.
	 * @return
	 */
	public boolean isTentative() {
		return isTentative;
	}

	/**
	 * Sets the "tentative" status of this patient, i.e. if it is suspected that
	 * the patient is a duplicate of another.
	 * 
	 * @param isTentative
	 */
	public void setTentative(boolean isTentative) {
		this.isTentative = isTentative;
		for (ID id : this.ids)
		{
			id.setTentative(isTentative);
		}
	}
	
	/**
	 * Check whether p and this patient are the same in the database
	 * (i.e. their patientJpaId values are equal).
	 * @param p A patient object.
	 */
	public boolean sameAs(Patient p)
	{
		return (this.getPatientJpaId() == p.getPatientJpaId()); 
	}

	/**
	 * Gets the original of this patient, i.e. the patient of which this patient is a
	 * duplicate. More precisely: A patient p_1 is the original of a patient p_n if either
	 * p_1 and p_n are the same or if there exists
	 * a chain p_1, p_2, ... , p_n of patients where p_k is a duplicate of p_k+1 for 1<=k<n.
	 * 
	 */
	public Patient getOriginal() {
		if (this.original == null || this.original == this) return this;
		else return this.original.getOriginal();
	}

	/**
	 * Set the original of a patient (see {@link #getOriginal() for a definition}. This 
	 * effectively marks this patient as a duplicate of the argument.
	 * @param original The patient which is to be set as the original of this. Can be null,
	 * which means that this patient is not a duplicate at all.
	 */
	public void setOriginal(Patient original) {
		if (original == null || original.sameAs(this))
		{
			this.original = null;
			return;
		}
		// Check if operation would lead to a circular relation
		// (setting a as duplicate of b when b is a duplicate of a)
		if (original.getOriginal().sameAs(this))
		{
			// TODO generalisieren f�r andere IDs
			CircularDuplicateRelationException e = new CircularDuplicateRelationException(
					this.getId("pid").getIdString(), original.getId("pid").getIdString());
			logger.error(e.getMessage());
			throw e;
		}
			this.original = original;
	}
	
	/**
	 * Check whether this patient is the duplicate of another.
	 * @see #getOriginal() 
	 * @see #setOriginal(Patient)
	 */
	public boolean isDuplicate()
	{
		return (this.original != null);
	}

	/**
	 * If p.original is not null, p is considered a duplicate of p.original. PID requests
	 * that find p as the best matching patient should return the PID of p.getOriginal().
	 * 
	 */
	@ManyToOne(cascade={CascadeType.DETACH, CascadeType.MERGE, CascadeType.PERSIST, CascadeType.REFRESH}, fetch=FetchType.EAGER)
	@JsonIgnore
	private Patient original = null;
	
	
	/**
	 * Construct an empty patient object.
	 */
	public Patient() {}
	
	/**
	 * Construct a patient object with the specified ids and fields.
	 * @param ids A set of ID objects that identify the patient.
	 * @param c The fields of the patient. A map with field names as keys and the corresponding
	 * Field objects as values.
	 */
	public Patient(Set<ID> ids, Map<String, Field<?>> c) {
		this.ids = ids;
		this.setFields(c);
	}
	
	/**
	 * Get the set of ids for this patient.
	 * @return The ids of the patient as unmodifiable set. While the set itself is unmodifiable,
	 * 	modification of the elements (ID objects) affect the patient object.  
	 */
	public Set<ID> getIds(){
		return Collections.unmodifiableSet(ids);
	}
	
	/**
	 * Get an ID of a specified type from this patient.
	 * @param type The string that identifies the ID string. See {@link IDGeneratorFactory#getFactory(String)}
	 */
	public ID getId(String type)
	{
		for (ID thisId : ids)
		{
			if (thisId.getType().equals(type))
				return thisId;
		}
		return null;
	}
	
	/**
	 * Set the IDs for this patient.
	 * @param ids Set of IDs. The set is copied by reference.
	 */
	public void setIds(Set<ID> ids) {
		this.ids = ids;
	}
	
	/**
	 * Get the fields of this patient.
	 * @return An unmodifiable map with field names as keys and corresponding field objects as values.
	 * 	Although the map itself is unmodifiable, modifications of its members affect the patient object.
	 */
	public Map<String, Field<?>> getFields() {
		return Collections.unmodifiableMap(fields);
	}
	
	/**
	 * Set the fields of this patient.
	 * @param Fields A map with field names as keys and corresponding Field objects as values. The map is copied by reference.
	 */
	public void setFields(Map<String, Field<?>> Fields) {
		this.fields = Fields;
		this.updateBlockingFields();
	}
	
	/**
	 * Returns a string representation of this patient by calling toString on the map.
	 */
	@Override
	public String toString() {
		return fields.toString();
	}
}