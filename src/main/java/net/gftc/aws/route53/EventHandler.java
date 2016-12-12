package net.gftc.aws.route53;

import java.io.IOException;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;
import com.fasterxml.jackson.databind.ObjectMapper;

public class EventHandler {

	private LambdaLogger logger;
	private AutoScalingNotification message;
	static private ObjectMapper s_mapper = new ObjectMapper();

	public EventHandler(Context context, SNSRecord event) {
		logger = context.getLogger();
		try {
			message = s_mapper.readValue(event.getSNS().getMessage(), 
					AutoScalingNotification.class);
		} catch (IOException e) {
			throw new RuntimeException("Unexpected parsing error: " + e.getMessage(),e);
		} 
	}

	public void handle() {
		switch (message.getType()) {
		case EC2_INSTANCE_LAUNCH:
			registerInstance(message.getEC2InstanceId());
			break;
		case EC2_INSTANCE_TERMINATE:
		case EC2_INSTANCE_TERMINATE_ERROR:
			deregisterIsntance(message.getEC2InstanceId());
			break;
		default: // do nothing in case of launch error
		}
	}

	private void deregisterIsntance(String ec2InstanceId) {
		logger.log("Registering " + ec2InstanceId);
	}

	private void registerInstance(String ec2InstanceId) {
		logger.log("Deregistering " + ec2InstanceId);
	}

}
