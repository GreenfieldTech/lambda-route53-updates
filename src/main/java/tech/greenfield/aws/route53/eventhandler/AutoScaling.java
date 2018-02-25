package tech.greenfield.aws.route53.eventhandler;

import com.amazonaws.services.lambda.runtime.Context;

import tech.greenfield.aws.route53.AutoScalingNotification;
import tech.greenfield.aws.route53.EventHandler;
import tech.greenfield.aws.route53.Route53Message;

public class AutoScaling extends EventHandler {

	public AutoScaling(Context context, AutoScalingNotification event, Route53Message message) {
		super(context, event.getType(), event.getEC2InstanceId(), event.getAutoScalingGroupName(), message);
	}

}
