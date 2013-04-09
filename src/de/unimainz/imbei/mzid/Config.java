package de.unimainz.imbei.mzid;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import de.unimainz.imbei.mzid.exceptions.InternalErrorException;
import de.unimainz.imbei.mzid.matcher.*;

/**
 * Configuration of the patient list. Implemented as a singleton object, which can be referenced
 * by Config.instance. The configuration is read from the properties file specified as
 * parameter de.unimainz.imbei.mzid.ConfigurationFile in context.xml
 * (see {@link java.util.Properties#load(InputStream) java.util.Properties}). 
 * 
 * 
 * @author borg
 *
 */
public enum Config {
	instance;
	
	public enum FieldType {
		PLAINTEXT,
		PLAINTEXT_NORMALIZED,
		HASHED, // Bloomfilter without prior normalization
		HASHED_NORMALIZED; // Bloomfilter with prior normalization
	}
	
	private final String version = "0.9";
	
	private final Map<String,Class<? extends Field<?>>> FieldTypes;
	
	private Properties props;
	private RecordTransformer recordTransformer;
	private Matcher matcher;
	
	private Logger logger = Logger.getLogger(Config.class);
	
	Config() throws InternalErrorException {
		props = new Properties();
		try {
			ServletContext context = Initializer.getServletContext();
			String configPath = context.getInitParameter("de.unimainz.imbei.mzid.ConfigurationFile");
			//configPath = null;//workaround warnecke
			if (configPath == null) configPath = "/mzid.conf";
			logger.info("Reading config from path " + configPath + "...");
			
			// First, try to read from resource (e.g. within the war file)
			InputStream configInputStream = getClass().getResourceAsStream(configPath);
			// Else: read from file System			
			if (configInputStream == null)
				configInputStream = new FileInputStream(configPath);
			
			props.load(configInputStream);
			/* 
			 * Read properties into Preferences for easier hierarchical access
			 * (e.g. it is possible to get the subtree of all idgenerators.* properties)
			 */
			Preferences prefs = Preferences.userRoot().node("de/unimainz/imbei/mzid");
			for (Object propName : props.keySet()) {
				Preferences prefNode = prefs;
				// Create a path in the preferences according to the property key.
				// (Path separated by ".") The last element is used as parameter name. 
				String prefKeys[] = propName.toString().split("\\.", 0);
				for (int i = 0; i < prefKeys.length - 1; i++)
					prefNode = prefNode.node(prefKeys[i]);
				prefNode.put(prefKeys[prefKeys.length - 1], props.getProperty(propName.toString()));
			}					
			configInputStream.close();
			logger.info("Config read successfully");
			logger.debug(props);
			
		} catch (IOException e)	{
			logger.fatal("Error reading configuration file: ", e);
			throw new InternalErrorException();
		}
		
		this.recordTransformer = new RecordTransformer(props);
		
		try {
			Class<?> matcherClass = Class.forName("de.unimainz.imbei.mzid.matcher." + props.getProperty("matcher"));
			matcher = (Matcher) matcherClass.newInstance();
			matcher.initialize(props);
			logger.info("Matcher of class " + matcher.getClass() + " initialized.");
		} catch (Exception e){
			logger.fatal("Initialization of matcher failed: " + e.getMessage(), e);
			throw new InternalErrorException();
		}
		
		// Read field types from configuration
		Pattern pattern = Pattern.compile("field\\.(\\w+)\\.type");
		java.util.regex.Matcher patternMatcher;
		this.FieldTypes = new HashMap<String, Class<? extends Field<?>>>();
		for (String propKey : props.stringPropertyNames()) {
			patternMatcher = pattern.matcher(propKey);
			if (patternMatcher.find())
			{
				String fieldName = patternMatcher.group(1);					
				String fieldClassStr = props.getProperty(propKey).trim();
				try {
					Class<? extends Field<?>> fieldClass;
					try {
						fieldClass = (Class<? extends Field<?>>) Class.forName(fieldClassStr);
					} catch (ClassNotFoundException e) {
						// Try with "de.unimainz..."
						fieldClass = (Class<? extends Field<?>>) Class.forName("de.unimainz.imbei.mzid." + fieldClassStr);
					}
					this.FieldTypes.put(fieldName, fieldClass);
					logger.debug("Initialized field " + fieldName + " with class " + fieldClass);
				} catch (Exception e) {
					logger.fatal("Initialization of field " + fieldName + " failed: ", e);
					throw new InternalErrorException();
				}
			}
		}
	}
	
	public RecordTransformer getRecordTransformer() {
		return recordTransformer;
	}

	public Properties getProperties() {
		return props;
	}

	public Matcher getMatcher() {
		return matcher;
	}

	public String getProperty(String propKey){
		return props.getProperty(propKey);
	}
	
	public Set<String> getFieldKeys(){
		return FieldTypes.keySet();
	}
	
	public Class<? extends Field<?>> getFieldType(String FieldKey){
		assert FieldTypes.keySet().contains(FieldKey);
		return FieldTypes.get(FieldKey);
	}
	
	public String getDist() {
		return getProperty("dist");
	}
	
	public String getVersion() {
		return version;
	}
	
	public boolean debugIsOn()
	{
		String debugMode = this.props.getProperty("debug");
		return (debugMode != null && debugMode.equals("true"));
	}
	
	Level getLogLevel() {
		String level = this.props.getProperty("loglevel");
		Level ret = Level.DEBUG;
		
		if (level == null || level.equals("DEBUG"))
			ret = Level.DEBUG;
		else if (level.equals("WARN"))
			ret = Level.WARN;
		else if (level.equals("ERROR"))
			ret = Level.ERROR;
		else if (level.equals("FATAL"))
			ret = Level.FATAL;
		else if (level.equals("INFO"))
			ret = Level.INFO;
		
		return ret;
	}
}