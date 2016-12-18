package net.gftc.aws.route53.eventhandler;

import com.amazonaws.services.autoscaling.model.CompleteLifecycleActionRequest;
import com.amazonaws.services.lambda.runtime.Context;

import net.gftc.aws.route53.EventHandler;
import net.gftc.aws.route53.LifeCycleNotification;

import static net.gftc.aws.Clients.autoscaling;

public class LifeCycle extends EventHandler {

	private LifeCycleNotification event;

	public LifeCycle(Context context, LifeCycleNotification event) {
		super(context, event.getType(), event.getEC2InstanceId());
		this.event = event;
	}

	@Override
	public void handle() {
		super.handle();
		// after handling the event, we need to invoke the life cycle action handler
		// to complete the life cycle
		log("Completing life-cycle action with token " + event.getLifecycleActionToken());
		autoscaling().completeLifecycleAction(new CompleteLifecycleActionRequest()
				.withAutoScalingGroupName(event.getAutoScalingGroupName())
				.withLifecycleHookName(event.getLifecycleHookName())
				.withLifecycleActionToken(event.getLifecycleActionToken())
				.withLifecycleActionResult("CONTINUE"));
	}

}
