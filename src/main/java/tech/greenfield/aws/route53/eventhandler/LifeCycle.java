package tech.greenfield.aws.route53.eventhandler;

import static tech.greenfield.aws.Clients.autoscaling;

import java.util.Objects;

import com.amazonaws.services.autoscaling.model.CompleteLifecycleActionRequest;
import com.amazonaws.services.lambda.runtime.Context;

import tech.greenfield.aws.route53.EventHandler;
import tech.greenfield.aws.route53.LifeCycleNotification;
import tech.greenfield.aws.route53.Route53Message;

public class LifeCycle extends EventHandler {

	private LifeCycleNotification event;

	public LifeCycle(Context context, LifeCycleNotification event, Route53Message message) {
		super(context, event.getType(), event.getEC2InstanceId(), event.getAutoScalingGroupName(), message);
		this.event = event;
	}

	@Override
	public void handle() {
		super.handle();
		// after handling the event, we need to invoke the life cycle action handler
		// to complete the life cycle
		String lifecycleActionToken = event.getLifecycleActionToken();
		if (Objects.isNull(lifecycleActionToken)) {
			log("Skipping lifecycle completion because there's no token");
			return;
		}
		log("Completing life-cycle action with token " + lifecycleActionToken);
		autoscaling().completeLifecycleAction(new CompleteLifecycleActionRequest()
				.withAutoScalingGroupName(event.getAutoScalingGroupName())
				.withLifecycleHookName(event.getLifecycleHookName())
				.withLifecycleActionToken(lifecycleActionToken)
				.withLifecycleActionResult("CONTINUE"));
	}

}
