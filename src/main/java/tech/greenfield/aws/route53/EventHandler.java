package tech.greenfield.aws.route53;

import static tech.greenfield.aws.Clients.ec2;
import static tech.greenfield.aws.Clients.route53;

import java.util.*;
import java.util.stream.Collectors;

import com.amazonaws.SdkBaseException;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.route53.model.*;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import tech.greenfield.aws.route53.eventhandler.AutoScaling;
import tech.greenfield.aws.route53.eventhandler.LifeCycle;

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

	@FunctionalInterface
	public interface Route53UpdateTask {
		void run() throws NoIpException;
	}

	static private ObjectMapper s_mapper = new ObjectMapper();
	
	private LambdaLogger logger;
	private EventType eventType;
	private String ec2instanceId;
	private String autoScalingGroupName;
	private Route53Message message;
	
	static {
		s_mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
	}

	/**
	 * Constructor to parse the SNS message and perform additional initialization
	 * @param context Call context from engine
	 * @param msg SNS event to process
	 */
//	public static EventHandler create(Context context, Route53Message msg) {
//		String snsMessageText = msg.getSNS().getMessage();
//		if (msg.isDebug())
//			context.getLogger().log("Got SNS message: " + snsMessageText + "\n");
//		try {
//			ObjectNode obj = s_mapper.readValue(snsMessageText, ObjectNode.class);
//			if (obj.has("LifecycleHookName"))
//				return new LifeCycle(context, s_mapper.readValue(snsMessageText, LifeCycleNotification.class), msg);
//			else
//				return new AutoScaling(context, s_mapper.readValue(snsMessageText, AutoScalingNotification.class), msg);
//		} catch (IOException e) {
//			throw new RuntimeException("Unexpected parsing error: " + e.getMessage(),e);
//		}
//	}
	
	public static EventHandler create(Context context, Route53Message msg) {
		Map<String, Object> messageBody = msg.getBody();
		if (Route53Message.isDebug())
			context.getLogger().log("Got message: " + messageBody + "\n");
		if (messageBody.containsKey("LifecycleTransition")) 
			return new LifeCycle(context, s_mapper.convertValue(messageBody, LifeCycleNotification.class), msg);
		else if (msg.retreiveBody().containsKey("LifecycleTransition"))
			return new LifeCycle(context, s_mapper.convertValue(msg.retreiveBody(), LifeCycleNotification.class), msg);
		else
			return new AutoScaling(context, s_mapper.convertValue(messageBody, AutoScalingNotification.class), msg);
	}
	
	protected EventHandler(Context context, EventType eventType, String ec2InstanceId, String autoScalingGroupName, Route53Message message) {
		this.logger = context.getLogger();
		this.eventType = Objects.requireNonNull(eventType, "Missing event type");
		this.ec2instanceId = ec2InstanceId;
		this.autoScalingGroupName = autoScalingGroupName;
		this.message = message;
	}

	/**
	 * Event handler entry point
	 */
	public void handle() {
		try {
			switch (eventType) {
			case EC2_INSTANCE_LAUNCH:
				retryIfThrottled(() -> registerInstance(ec2instanceId));
				break;
			case EC2_INSTANCE_TERMINATE:
			case EC2_INSTANCE_TERMINATE_ERROR:
				retryIfThrottled(() -> deregisterInstance(ec2instanceId));
				break;
			default: // do nothing in case of launch error or test notification
			}
		} catch(NoIpException e) {
			logger.log("Error: " + e.getMessage());
			logger.log("No IP was found, starting plan B - update all instances");
			rebuildAllRRs(this.autoScalingGroupName);
		} catch (SilentFailure | SdkBaseException e) {
			log("Silently failing Route53 update: " + e);
		}
	}
	
	private void rebuildAllRRs(String asgName) {
		List<Instance> instances = Tools.getASGInstances(asgName).stream()
			.filter(i -> i.getHealthStatus().equals("Healthy"))
			.map(Tools::asInstanceToEC2)
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
		
		ChangeBatch changes = instances.isEmpty() ? message.getDeleteChanges() : message.getUpsertChanges(instances);
		if (Route53Message.isDebug())
			log("Sending DNS change request: " + changes);
		Tools.waitFor(route53().changeResourceRecordSets(new ChangeResourceRecordSetsRequest().withChangeBatch(changes)));
	}

	/**
	 * Start an DNS registration for the launched instance
	 * @param ec2InstanceId instance ID of instance that needs to be registered
	 * @param ttl TTL in seconds to use when creating a new record
	 */
	private void registerInstance(String ec2InstanceId) throws NoIpException{
		log("Registering " + ec2InstanceId);
		ChangeBatch cb = message.getUpsertChanges(Collections.singletonList(getInstance(ec2InstanceId)));
		
		if (Route53Message.isDebug())
			log("Adding instance with addresses: " + cb);

		for (Change c : cb.getChanges()) {
			ResourceRecordSet rr = c.getResourceRecordSet();
			ResourceRecordSet oldrr = Tools.getRecordSet(rr.getName(), rr.getType());
			if (Objects.nonNull(oldrr))
				oldrr.getResourceRecords().forEach(r -> rr.withResourceRecords(r));
		}
		ChangeResourceRecordSetsRequest req = new ChangeResourceRecordSetsRequest(Route53Message.getHostedZoneId(), cb);
		if (Route53Message.isDebug())
			log("Sending rr change request: " + req);
		Tools.waitFor(route53().changeResourceRecordSets(req));
	}
	
	/**
	 * Start a DNS re-registration for the terminated instance
	 * @param ec2InstanceId instance ID of instance that needs to be de-registered
	 * @param ttl TTL in seconds to use when creating a new record
	 * @throws NoIpException 
	 */
	private void deregisterInstance(String ec2InstanceId) throws NoIpException {
		log("Deregistering " + ec2InstanceId);
		ChangeBatch changes = message.getRemoveChanges(getInstance(ec2InstanceId));
		if (changes.getChanges().isEmpty()) {
			log("Nothing to remove");
			return;
		}
		ChangeResourceRecordSetsRequest req = new ChangeResourceRecordSetsRequest(Route53Message.getHostedZoneId(), changes);
		if (Route53Message.isDebug())
			log("Sending rr change request: " + req);
		Tools.waitFor(route53().changeResourceRecordSets(req));
	}
	
	/**
	 * Helper method to resolve an instance ID to an EC2 instance object
	 * @param ec2InstanceId instance Id to resolve
	 * @return EC2 instance found
	 * @throws RuntimeException in case no instance with the specified ID was found
	 */
	private Instance getInstance(String ec2InstanceId) {
		return ec2().describeInstances(
				new DescribeInstancesRequest().withInstanceIds(ec2InstanceId))
				.getReservations().stream()
				.flatMap(r -> r.getInstances().stream())
				.findFirst()
				.orElseThrow(() -> new RuntimeException(
						"Failed to locate instance " + ec2InstanceId));
	}

	private void retryIfThrottled(Route53UpdateTask action) throws NoIpException {
		while (true) {
			try {
				action.run();
				return;
			} catch (AmazonRoute53Exception e) {
				log("Throttled: " + e);
				// retry in case of 
				if (e.getMessage().contains("Rate exceeded")) {
					try {
						wait(1000);
						log("Retrying...");
					} catch (InterruptedException e1) { }
					continue;
				} else
					throw e;
			}
		}
	}

	protected void log(String message) {
		logger.log(message + "\n");
	}

}
