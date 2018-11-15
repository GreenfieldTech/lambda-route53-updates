package tech.greenfield.aws.route53;

import java.util.logging.*;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;

public class BaseNotifyRecords {

	protected final Logger logger = Logger.getLogger(getClass().getName());
	
	protected void setupLogger(Context context) {
		Logger global = Logger.getGlobal();
		for (Handler h : global.getHandlers())
			global.removeHandler(h);
		LambdaLogger mylog = context.getLogger();
		global.addHandler(new Handler() {
			
			{
				setFormatter(new SimpleFormatter());
			}
			
			@Override
			public void publish(LogRecord record) {
				mylog.log(getFormatter().format(record));
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
