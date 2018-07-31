package tech.greenfield.aws.route53;

import static tech.greenfield.aws.Clients.autoscaling;
import static tech.greenfield.aws.Clients.ec2;
import static tech.greenfield.aws.Clients.route53;

import java.net.InetAddress;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.SdkBaseException;
import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.route53.model.*;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import tech.greenfield.aws.LoggingObject;
import tech.greenfield.aws.Tools;
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
public class EventHandler extends LoggingObject {

	static private ObjectMapper s_mapper = new ObjectMapper();
	
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
				registerInstance(ec2instanceId);
				break;
			case EC2_INSTANCE_TERMINATE:
			case EC2_INSTANCE_TERMINATE_ERROR:
				deregisterInstance(ec2instanceId);
				break;
			default: // do nothing in case of launch error or test notification
			}
		} catch(NoIpException e) {
			log("No IP was found, starting plan B - update all instances");
			DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(this.autoScalingGroupName);
			List<com.amazonaws.services.autoscaling.model.Instance> instances = autoscaling().describeAutoScalingGroups(request).getAutoScalingGroups().get(0).getInstances();
			Entry<String,List<InetAddress>> instancesToUpdate = Tools.getEc2InstancesFromAsgInstances(instances);
			ChangeResourceRecordSetsRequest req;
			if (instancesToUpdate.getValue().size() == 0)
				req = createDeleteRequest(instancesToUpdate.getKey()); 
			else
				req = createChangeRequest(instancesToUpdate.getValue(), instancesToUpdate.getKey(), Route53Message.getTTL());
			if (Route53Message.isDebug())
				log("Sending rr change request: " + req);
			DNSTools.waitFor(route53().changeResourceRecordSets(req));
		} catch (SilentFailure | SdkBaseException e) {
			log("Silently failing Route53 update: " + e);
		}
	}

	/**
	 * Start an DNS registration for the launched instance
	 * @param ec2InstanceId instance ID of instance that needs to be registered
	 * @param ttl TTL in seconds to use when creating a new record
	 */
	private void registerInstance(String ec2InstanceId) throws NoIpException{
		log("Registering " + ec2InstanceId);
		try {
			while (true) {
				try {
					ChangeResourceRecordSetsRequest req = message.createAddRequest(getInstance(ec2InstanceId));
					if (Route53Message.isDebug())
						log("Sending rr change request: " + req);
					DNSTools.waitFor(route53().changeResourceRecordSets(req));
				} catch (AmazonRoute53Exception e) {
					// retry in case of 
					if (e.getMessage().contains("Rate exceeded")) {
						try {
							wait(1000);
						} catch (InterruptedException e1) { }
						continue;
					} else
						throw e;
				}
				break;
			}
		} catch (RuntimeException e) {
			throw new NoIpException(e.getMessage());
		}
	}
	
	/**
	 * Start a DNS re-registration for the terminated instance
	 * @param ec2InstanceId instance ID of instance that needs to be de-registered
	 * @param ttl TTL in seconds to use when creating a new record
	 * @throws NoIpException 
	 */
	private void deregisterInstance(String ec2InstanceId) throws NoIpException {
		while (true) {
			try {
				log("Deregistering " + ec2InstanceId);
				ChangeResourceRecordSetsRequest req = message.createRemoveRequest(getInstance(ec2InstanceId));
				if (Route53Message.isDebug())
					log("Sending rr change request: " + req);
				DNSTools.waitFor(route53().changeResourceRecordSets(req));
			} catch (AmazonRoute53Exception e) {
				// retry in case of 
				if (e.getMessage().contains("Rate exceeded")) {
					try {
						wait(1000);
					} catch (InterruptedException e1) { }
					continue;
				} else
					throw e;
			}
			break;
		}
	}
	
	private ChangeResourceRecordSetsRequest createChangeRequest(List<InetAddress> list, String addr, long ttl) {
//		if (Objects.isNull(ip))
//			throw new SilentFailure("Cowardly refusing to add an instance with no IP address");
//		if (isDebug())
//			log("Adding instance with addresses: " + ip + ", " + addr);
		
		ChangeResourceRecordSetsRequest req = null;
		if (message.useDNSRR())
			req = DNSTools.createRecordSet(message.getDNSRR_RECORD().stream().map(hostname -> new SimpleEntry<>(hostname, list)), RRType.A, ttl);
		
		if (message.useSRV()) {
			Map<String, List<String>> rrsList = message.getSRV_RECORD().stream().flatMap(conf -> {
				String[] parts = conf.split(":");
				return list.stream().map(ip -> Stream.of(parts[0], parts[1], parts[2], ip).collect(Collectors.joining(" ")))
						.map(s -> new AbstractMap.SimpleEntry<String,String>(parts[3],s));
			}).collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
			ChangeResourceRecordSetsRequest srvReq = DNSTools.createRecordSet(rrsList.entrySet().stream(), RRType.SRV, ttl);
			if (Objects.isNull(req))
				req = srvReq;
			else {
				// already have a DNS RR change batch in queue, just add our changes
				ChangeBatch b = req.getChangeBatch();
				srvReq.getChangeBatch().getChanges().forEach(b::withChanges);
			}
		}
		
		if (Objects.isNull(req))
			throw new UnsupportedOperationException("Please specify either DNSRR_RECORD or SRV_RECORD");
		return req;
	}
	
	private ChangeResourceRecordSetsRequest createDeleteRequest(String addr) {
		ArrayList<Change> changes = new ArrayList<>();
		if (message.useDNSRR())
			changes.add(new Change(ChangeAction.DELETE, new ResourceRecordSet(addr, RRType.A)));
		if (message.useSRV())
			changes.add(new Change(ChangeAction.DELETE, new ResourceRecordSet(addr, RRType.SRV)));
		if (changes.isEmpty())
			throw new UnsupportedOperationException("Please specify either DNSRR_RECORD or SRV_RECORD");
		return new ChangeResourceRecordSetsRequest(Route53Message.getHostedZoneId(), new ChangeBatch(changes));
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

}
