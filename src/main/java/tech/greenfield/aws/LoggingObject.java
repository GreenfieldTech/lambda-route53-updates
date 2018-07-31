package tech.greenfield.aws;

import java.util.Objects;

import com.amazonaws.services.lambda.runtime.LambdaLogger;

public class LoggingObject {
	
	protected static volatile LambdaLogger currentLogger;
	
	public static void setLogger(LambdaLogger logger) {
		currentLogger = logger;
	}
	
	static interface LoggingIf {
		public void log(String message);
	}
	
	protected static LoggingIf logger = new LoggingIf() {
		@Override
		public void log(String message) {
		}
	};
	
	protected String name = getClass().getSimpleName();

	public LoggingObject() {
		if (Objects.nonNull(currentLogger))
			logger = new LoggingIf() {
				@Override
				public void log(String message) {
					currentLogger.log("[" + name + "] " + message + "\n");
				}
			};
	}

	protected void log(String message) {
		logger.log(message);
	}

}
