package net.gftc.aws.route53.eventhandler;

import com.amazonaws.services.lambda.runtime.Context;

import net.gftc.aws.route53.AutoScalingNotification;
import net.gftc.aws.route53.EventHandler;

public class AutoScaling extends EventHandler {

	public AutoScaling(Context context, AutoScalingNotification event) {
		super(context, event.getType(), event.getEC2InstanceId());
	}

}
