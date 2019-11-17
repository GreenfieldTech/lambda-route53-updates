package tech.greenfield.aws.route53;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.*;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

public class BaseNotifyRecords {
	
	static {
		System.setProperty("java.util.logging.SimpleFormatter.format", "%4$s: %2$s - %5$s %6$s%n");
	}
	
	@SuppressWarnings("serial")
	private Map<String,Level> loggerLevels = new HashMap<String, Level>() {{
		put("", Level.ALL);
		put("org.apache", Level.INFO);
		put("com.amazonaws", Level.INFO);
		put("com.sun", Level.INFO);
		put("tech.greenfield", Route53Message.isDebug() ? Level.ALL : Level.INFO);
	}};

	protected final Logger logger = Logger.getLogger(getClass().getName());
	
	protected void setupLogger(Context context) {
		String callId = context.getAwsRequestId();
		Logger root = Logger.getAnonymousLogger().getParent();
		for (Handler h : root.getHandlers())
			root.removeHandler(h);
		LambdaLogger mylog = context.getLogger();
		root.addHandler(new Handler() {
			
			{
				setFormatter(new SimpleFormatter());
				setLevel(Level.ALL);
			}
			
			@Override
			public void publish(LogRecord record) {
				mylog.log("[" + callId + "] " + getFormatter().format(record));
			}
			
			@Override
			public void flush() {
			}
			
			@Override
			public void close() throws SecurityException {
			}
		});
		loggerLevels.forEach((name, level) -> Logger.getLogger(name).setLevel(level));
		logger.info("Route 53 update lambda version " + Tools.getVersion());
	}
}
