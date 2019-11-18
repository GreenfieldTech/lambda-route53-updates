package tech.greenfield.aws.route53;

import static tech.greenfield.aws.Clients.ec2;
import static tech.greenfield.aws.Clients.route53;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;
import java.util.logging.*;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.route53.model.*;

/**
 * Handler for a single SNS event that was submitted to the lambda implementation
 * @author odeda
 *
 *     Copyright (C) 2016  GreenfieldTech
 * 
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 * 
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 * 
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
public class EventHandler {

	static private ObjectMapper s_mapper = new ObjectMapper();
	
	protected Logger logger = Logger.getLogger(getClass().getName());
	private EventType eventType;
	private String ec2instanceId;
	private String autoScalingGroupName;
	private Route53Message message;
	
	static {
		s_mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
	}
	
	protected EventHandler(Context context, EventType eventType, String ec2InstanceId, String autoScalingGroupName, Route53Message message) {
		this.eventType = Objects.requireNonNull(eventType, "Missing event type");
		this.ec2instanceId = ec2InstanceId;
		this.autoScalingGroupName = autoScalingGroupName;
		this.message = message;
	}

	/**
	 * Event handler entry point
	 */
	public CompletableFuture<Void> handle() {
		return handleEventType()
				.thenApply(v -> CompletableFuture.<Void>completedFuture(null))
				.exceptionally(t -> {
					if (t instanceof NoIpException) {
						logger.warning("Error: " + t.getMessage());
						logger.warning("No IP was found, starting plan B - update all instances");
						return rebuildAllRRs(this.autoScalingGroupName);
					} else if (t instanceof SilentFailure) {
						Tools.logException(logger, "Silently failing Route53 update", t);
						return CompletableFuture.completedFuture(null);
					} else if (Objects.nonNull(t)) {
						CompletableFuture<Void> fail = new CompletableFuture<>();
						fail.completeExceptionally(t);
						return fail;
					}
					return CompletableFuture.completedFuture(null);
				}).thenCompose(f -> f);
	}

	private CompletableFuture<Void> handleEventType() {
		switch (eventType) {
		case EC2_INSTANCE_LAUNCH:
			return retryIfThrottled(() -> registerInstance(ec2instanceId));
		case EC2_INSTANCE_TERMINATE:
		case EC2_INSTANCE_TERMINATE_ERROR:
			return retryIfThrottled(() -> deregisterInstance(ec2instanceId));
		default: // do nothing in case of launch error or test notification
			logger.info("Unrecognized event type '" + eventType + "', ignoring");
			return CompletableFuture.completedFuture(null);
		}
	}
	
	private CompletableFuture<Void> rebuildAllRRs(String asgName) {
		return Tools.getASGInstances(asgName)
				.thenCompose(l -> l.stream()
						.filter(i -> i.healthStatus().equalsIgnoreCase("healthy"))
						.map(Tools::asInstanceToEC2)
						.collect(new CompletableFutureListCollector<>()))
				.thenApply(l -> l.stream()
						.filter(Objects::nonNull)
						.collect(Collectors.toList()))
				.thenCompose(instances -> instances.isEmpty() ? 
						CompletableFuture.completedFuture(message.getDeleteChanges()) : message.getUpsertChanges(instances))
				.whenComplete((changes, t) -> logger.fine("Sending DNS change request: " + changes))
				.thenCompose(changes -> route53()
						.changeResourceRecordSets(b -> b.hostedZoneId(Route53Message.getHostedZoneId()).changeBatch(changes)))
				.thenCompose(res -> Tools.waitFor(res.changeInfo()))
				.exceptionally(t -> {
					Tools.logException(logger, "Error in submitting Route53 update",t);
					throw new CompletionException(SdkException.builder().cause(t).build());
				});
	}

	/**
	 * Start an DNS registration for the launched instance
	 * @param ec2InstanceId instance ID of instance that needs to be registered
	 * @param ttl TTL in seconds to use when creating a new record
	 * @return 
	 */
	private CompletableFuture<Void> registerInstance(String ec2InstanceId) {
		return getInstance(ec2InstanceId)
				.thenCompose(i -> {
					logger.info("Registering " + ec2InstanceId + " - " + Tools.getIPAddress(i));
					return message.getUpsertChanges(i);
				})
				.thenCompose(cb -> {
					logger.fine("Adding instance with addresses: " + cb);
					return route53().changeResourceRecordSets(b -> b.hostedZoneId(Route53Message.getHostedZoneId())
							.changeBatch(cb).build());
				})
				.thenCompose(res -> Tools.waitFor(res.changeInfo()));
	}
	
	/**
	 * Start a DNS re-registration for the terminated instance
	 * @param ec2InstanceId instance ID of instance that needs to be de-registered
	 * @param ttl TTL in seconds to use when creating a new record
	 * @return 
	 */
	private CompletableFuture<Void> deregisterInstance(String ec2InstanceId) {
		return getInstance(ec2InstanceId)
				.thenCompose(i -> {
					logger.info("Deregistering " + ec2InstanceId + " - " + Tools.getIPAddress(i));
					return message.getRemoveChanges(i);
				})
				.thenCompose(changes -> {
					if (changes.changes().isEmpty()) {
						logger.info("Nothing to remove");
						return CompletableFuture.completedFuture(null);
					}
					logger.fine("Sending rr change request: " + changes);
					return route53().changeResourceRecordSets(b -> b
							.hostedZoneId(Route53Message.getHostedZoneId()).changeBatch(changes))
							.thenCompose(res -> Tools.waitFor(res.changeInfo()));
				});
	}
	
	/**
	 * Helper method to resolve an instance ID to an EC2 instance object
	 * @param ec2InstanceId instance Id to resolve
	 * @return EC2 instance found
	 * @throws RuntimeException in case no instance with the specified ID was found
	 */
	private CompletableFuture<Instance> getInstance(String ec2InstanceId) {
		return ec2().describeInstances(b -> b.instanceIds(ec2InstanceId))
				.thenApply(res -> res.reservations().stream()
						.flatMap(r -> r.instances().stream())
						.findFirst()
						.orElseThrow(() -> new CompletionException(new Exception("Failed to locate instance " + ec2InstanceId))));
	}

	private CompletableFuture<Void> retryIfThrottled(Supplier<CompletableFuture<Void>> action) {
		return action.get()
				.thenApply(v -> CompletableFuture.<Void>completedFuture(null))
				.exceptionally(t -> {
					if (t instanceof Route53Exception) {
						// retry in case of 
						if (t.getMessage().contains("Rate exceeded")) {
							logger.info("Throttled: " + t);
							return CompletableFuture.runAsync(Tools.delay(2000))
									.thenRun(() -> logger.info("Retrying..."))
									.thenCompose(v -> retryIfThrottled(action));
						}
					}
					throw new CompletionException(t);
				})
				.thenCompose(f -> f);
	}
	
}
