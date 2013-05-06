package de.unimainz.imbei.mzid;

import java.io.IOException;
import java.util.Enumeration;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.log4j.FileAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import de.unimainz.imbei.mzid.dto.Persistor;

/**
 * This class is responsible for setting up all singletons in the right order
 * and to fail early if anything goes wrong.
 * 
 * @author Martin Lablans
 */
public class Initializer implements ServletContextListener {
//	private @Context ServletContext servletContext;
	private static ServletContext context;
	
	@Override
	public void contextInitialized(ServletContextEvent sce) {
		context = sce.getServletContext();
		initialize();		
	}
	
	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		// nothing to do here.
	}
	
	@SuppressWarnings("unused")
	private void initialize(){
		Logger logger = Logger.getLogger(Initializer.class);
		logger.info("#####Initializing...");
		
		//<DEBUG>
		Enumeration<String> en = context.getInitParameterNames();
		while(en.hasMoreElements()){
			String paramName = en.nextElement();
			logger.debug("Init param " + paramName + "=" + context.getInitParameter(paramName));
		}
		//</DEBUG>
		
		Config c = Config.instance;
		log4jSetup();
		Persistor p = Persistor.instance;
		IDGeneratorFactory idgf = IDGeneratorFactory.instance;
		Servers s = Servers.instance;
		Validator v = Validator.instance;

		logger.info("#####Startup succeeded. Ready to take requests.");
	}
	
	private void log4jSetup(){
		Logger root = Logger.getRootLogger();
		root.setLevel(Config.instance.getLogLevel());
		String logFileName = Config.instance.getProperty("log.filename");
		if(logFileName == null){
			root.info("Using default logging output.");
		} else {
			PatternLayout layout = new PatternLayout("%d %p %t %c - %m%n");
			try {
				FileAppender app;
				app = new FileAppender(layout, logFileName);
				app.setName("MzidFileAppender");
				
				// In production mode, avoid spamming the servlet container's logfile.
				if(!Config.instance.debugIsOn()){
					root.warn("Redirecting mzid log to " + logFileName + ".");
					root.removeAllAppenders();
				}
				
				root.addAppender(app);
				root.info("Logger setup to log on level " + Config.instance.getLogLevel() + " to " + logFileName);
			} catch (IOException e) {
				root.fatal("Unable to log to " + logFileName + ": " + e.getMessage());
				return;
			}
		}
		root.info("#####BEGIN MZID LOG SESSION");
	}
	
	static ServletContext getServletContext(){
		return context;
	}
}
