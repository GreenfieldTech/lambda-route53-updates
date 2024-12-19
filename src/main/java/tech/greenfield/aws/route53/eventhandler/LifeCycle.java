package tech.greenfield.aws.route53.eventhandler;

import static tech.greenfield.aws.Clients.autoscaling;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.amazonaws.services.lambda.runtime.Context;

import software.amazon.awssdk.services.autoscaling.model.AutoScalingException;
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
	public CompletableFuture<Void> handle() {
		String lifecycleActionToken = event.getLifecycleActionToken();
		return super.handle().thenCompose(v -> handleLifecycleAction(lifecycleActionToken));
	}
	
	private CompletableFuture<Void> handleLifecycleAction(String lifecycleActionToken) {
		// after handling the event, we need to invoke the life cycle action handler
		// to complete the life cycle
		if (Objects.isNull(lifecycleActionToken)) {
			log.info("Skipping lifecycle completion because there's no token");
			return CompletableFuture.completedFuture(null);
		}
		log.info("Completing life-cycle action with token " + lifecycleActionToken);
		return completeLifecycle(lifecycleActionToken, "CONTINUE")
				.thenApply(v -> CompletableFuture.<Void>completedFuture(null))
				.exceptionally(e -> {
					log.error("Error in lifecycle event handling, abandoning lifecycle with token " + lifecycleActionToken + ": " + e);
					if (Objects.nonNull(lifecycleActionToken))
						return completeLifecycle(lifecycleActionToken, "ABANDON");
					log.warn("Skipping lifecycle completion because there's no token");
					return CompletableFuture.completedFuture(null);
				})
				.thenCompose(ft -> ft);
	}

	/**
	 * @param lifecycleActionToken the token identifying the life cycle action
	 * @param result The result of the life cycle action to publish
	 * @return life cycle action result
	 */
	private CompletableFuture<Void> completeLifecycle(String lifecycleActionToken, String result) {
		return autoscaling().completeLifecycleAction(b -> b
					.autoScalingGroupName(event.getAutoScalingGroupName())
					.lifecycleHookName(event.getLifecycleHookName())
					.lifecycleActionToken(lifecycleActionToken)
					.lifecycleActionResult(result))
				.<Void>thenApply(res -> null)
				.exceptionally(t -> {
					if (t instanceof AutoScalingException && 
							t.getMessage().contains("No active Lifecycle Action found"))
						return null;
					throw new CompletionException(t);
				});
	}

}
