package tech.greenfield.aws.route53;

import static tech.greenfield.aws.Clients.ec2;
import static tech.greenfield.aws.Clients.route53;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.SdkBaseException;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.route53.model.*;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

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
		logger.info("Route 53 update lambda version " + Tools.getVersion());
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
		} catch (NoIpException e) {
			logger.warning("Error: " + e.getMessage());
			logger.warning("No IP was found, starting plan B - update all instances");
			rebuildAllRRs(this.autoScalingGroupName);
		} catch (SilentFailure | SdkBaseException e) {
			Tools.logException(logger, "Silently failing Route53 update", e);
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
			logger.info("Sending DNS change request: " + changes);
		try {
			ChangeResourceRecordSetsRequest req = new ChangeResourceRecordSetsRequest(Route53Message.getHostedZoneId(), changes);
			Tools.waitFor(route53().changeResourceRecordSets(req));
		} catch (IllegalArgumentException e) {
			Tools.logException(logger, "Error in submitting Route53 update",e);
			throw new SdkBaseException(e);
		}
	}

	/**
	 * Start an DNS registration for the launched instance
	 * @param ec2InstanceId instance ID of instance that needs to be registered
	 * @param ttl TTL in seconds to use when creating a new record
	 */
	private void registerInstance(String ec2InstanceId) throws NoIpException{
		Instance i = getInstance(ec2InstanceId);
		logger.info("Registering " + ec2InstanceId + " - " + Tools.getIPAddress(i));
		ChangeBatch cb = message.getUpsertChanges(i);
		
		if (Route53Message.isDebug())
			logger.info("Adding instance with addresses: " + cb);

		for (Change c : cb.getChanges()) {
			ResourceRecordSet rr = c.getResourceRecordSet();
			ResourceRecordSet oldrr = Tools.getRecordSet(rr.getName(), rr.getType());
			if (Objects.isNull(oldrr))
				continue;
			rr.setResourceRecords(Stream.concat(oldrr.getResourceRecords().stream(), rr.getResourceRecords().stream())
					.distinct().collect(Collectors.toList()));
		}
		ChangeResourceRecordSetsRequest req = new ChangeResourceRecordSetsRequest(Route53Message.getHostedZoneId(), cb);
		if (Route53Message.isDebug())
			logger.info("Sending rr change request: " + req);
		Tools.waitFor(route53().changeResourceRecordSets(req));
	}
	
	/**
	 * Start a DNS re-registration for the terminated instance
	 * @param ec2InstanceId instance ID of instance that needs to be de-registered
	 * @param ttl TTL in seconds to use when creating a new record
	 * @throws NoIpException 
	 */
	private void deregisterInstance(String ec2InstanceId) throws NoIpException {
		Instance i = getInstance(ec2InstanceId);
		logger.info("Deregistering " + ec2InstanceId + " - " + Tools.getIPAddress(i));
		ChangeBatch changes = message.getRemoveChanges(i);
		if (changes.getChanges().isEmpty()) {
			logger.info("Nothing to remove");
			return;
		}
		ChangeResourceRecordSetsRequest req = new ChangeResourceRecordSetsRequest(Route53Message.getHostedZoneId(), changes);
		if (Route53Message.isDebug())
			logger.fine("Sending rr change request: " + req);
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
				// retry in case of 
				if (e.getMessage().contains("Rate exceeded")) {
					logger.info("Throttled: " + e);
					try {
						Thread.sleep(2000);
						logger.info("Retrying...");
					} catch (InterruptedException e1) { }
					continue;
				} else
					throw e;
			}
		}
	}
	
}
