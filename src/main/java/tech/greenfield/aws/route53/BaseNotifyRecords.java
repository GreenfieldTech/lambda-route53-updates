package tech.greenfield.aws.route53;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseNotifyRecords {
	
	protected final Logger log = LoggerFactory.getLogger(getClass().getName());
	
	{
		log.info("Route 53 update lambda version {}", Tools.getVersion());
	}
}
