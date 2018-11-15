package tech.greenfield.aws.route53;

import java.util.Objects;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

public class BaseNotifyRecords {

	protected final Logger logger = Logger.getLogger(getClass().getName());
	
	protected void setupLogger(Context context) {
		for (Handler h : logger.getHandlers())
			logger.removeHandler(h);
		LambdaLogger mylog = context.getLogger();
		logger.addHandler(new Handler() {
			
			@Override
			public void publish(LogRecord record) {
				if (Objects.nonNull(mylog))
					mylog.log(getFormatter().format(record));
				else
					System.err.println(getFormatter().format(record));
			}
			
			@Override
			public void flush() {
			}
			
			@Override
			public void close() throws SecurityException {
			}
		});
	}
}
