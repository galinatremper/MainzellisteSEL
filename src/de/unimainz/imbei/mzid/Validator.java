package de.unimainz.imbei.mzid;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import javax.ws.rs.core.MultivaluedMap;


import de.unimainz.imbei.mzid.exceptions.InternalErrorException;
import de.unimainz.imbei.mzid.exceptions.ValidatorException;




/**
 * Form validation.
 * Validation checks are stored in a Properties object passed to the constructor.
 * Supported checks:
 * 
 * <ul>
 * 	<li> Check required fields (i.e. not empty): validator.field.<i>fieldname</i>.required marks
 * 		field <i> fieldname</i> as required.
 *  <li> Check format: validator.field.<i>fieldname</i>.format defines a regular expression against
 *  	which the specified field is checked.
 *  <li> 
 * 
 * @author borg
 *
 */
public enum Validator {

	instance;

	private Set<String> requiredFields = new HashSet<String>();
	private Map<String, String> formats = new HashMap<String, String>();
	private List<List<String>> dateFields = new LinkedList<List<String>>();
	private List<String> dateFormat = new LinkedList<String>();
	
	private Validator() {

		Properties props = Config.instance.getProperties();
		
		Pattern pRequired = Pattern.compile("^validator\\.field\\.(\\w+)\\.required");
		Pattern pFormat = Pattern.compile("^validator\\.field\\.(\\w+)\\.format");
		Pattern pDateFields = Pattern.compile("^validator\\.date\\.(\\d+).fields");
		java.util.regex.Matcher m;
		
		for (Object thisPropKeyObj : props.keySet()) {
			String thisPropKey = (String) thisPropKeyObj;
			
			// Look for required fields
			m = pRequired.matcher(thisPropKey);
			if (m.find())
			{
				requiredFields.add(m.group(1).trim());
			}
			
			// Look for format definitions
			m = pFormat.matcher(thisPropKey);
			
			if (m.find())
			{
				String fieldName = m.group(1);
				String format = props.getProperty(thisPropKey).trim();
				// Check if format is a valid regular expression
				try {
					Pattern.compile(format);
				} catch (PatternSyntaxException e) {
					throw new InternalErrorException(e);
				}				
				formats.put(fieldName, format);
			}

			// Look for format definitions
			m = pDateFields.matcher(thisPropKey);
			if (m.find())
			{
				try {
					int dateInd = Integer.parseInt(m.group(1));
					List<String> theseFields = new LinkedList<String>();
					for (String thisFieldName : props.getProperty("validator.date." + dateInd + ".fields").split(",")) {
						theseFields.add(thisFieldName.trim());
					}
					dateFields.add(theseFields);
					// TODO: Datumsformat checken
					dateFormat.add(props.getProperty("validator.date." + dateInd + ".format").trim());
					} catch (NumberFormatException e) {
					throw new InternalErrorException(e);
				}
			}
			
		}		
	}
	
	public void validateField(String key, String value) {
		
		if (requiredFields.contains(key)) {
			if (value == null || value.equals("")) {
				throw new ValidatorException("Field " + key + " must not be empty!");
			}
		}

		if (formats.containsKey(key)) {
			String format = formats.get(key);
			if (value != null && !value.equals("") && !Pattern.matches(format, value)) {
				throw new ValidatorException("Field " + key + 
						" does not conform to the required format" + format);
			}
		}
	}
	
	public void validateDates(MultivaluedMap<String, String> form) {
		assert dateFields.size() == dateFormat.size();
		Iterator<List<String>> fieldIt = dateFields.iterator();
		Iterator<String> formatIt = dateFormat.iterator();
		
		while (fieldIt.hasNext()) {
			SimpleDateFormat sdf = new SimpleDateFormat(formatIt.next());
			sdf.setLenient(false);
			StringBuffer dateString = new StringBuffer();
			for (String dateElement : fieldIt.next()) {
				dateString.append(form.getFirst(dateElement));
			}
			try {
				Date date = sdf.parse(dateString.toString()); 
				if (date == null)
					throw new ValidatorException(dateString + " is not a valid date!");
			} catch (ParseException e) {
				throw new ValidatorException(dateString + " is not a valid date!");
			}
		}
		
	}
	
	public void validateForm(MultivaluedMap<String, String> form) {
		for (String key : form.keySet()) {
			for (String value : form.get(key)) {
				validateField(key, value);
			}			
		}
		validateDates(form);
		
	}
}
