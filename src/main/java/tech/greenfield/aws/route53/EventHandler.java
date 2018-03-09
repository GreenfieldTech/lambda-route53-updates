package tech.greenfield.aws.route53;

import static tech.greenfield.aws.Clients.autoscaling;
import static tech.greenfield.aws.Clients.ec2;
import static tech.greenfield.aws.Clients.route53;

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
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.RRType;
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
		else
			return new AutoScaling(context, s_mapper.convertValue(messageBody, AutoScalingNotification.class), msg);
	}
	
	protected EventHandler(Context context, EventType eventType, String ec2InstanceId, String autoScalingGroupName, Route53Message message) {
		this.logger = context.getLogger();
		this.eventType = eventType;
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
			default: // do nothing in case of launch error or test notifcation
			}
		} catch(NoIpException e) {
			logger.log("No IP was found, starting plan B - update all instances");
			DescribeAutoScalingGroupsRequest request = new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(this.autoScalingGroupName);
			List<com.amazonaws.services.autoscaling.model.Instance> instances = autoscaling().describeAutoScalingGroups(request).getAutoScalingGroups().get(0).getInstances();
			Entry<String, List<String>> instancesToUpdate = getEc2InstancesFromAsgInstances(instances);
			ChangeResourceRecordSetsRequest req = createChangeRequest(instancesToUpdate.getValue(), instancesToUpdate.getKey(), Route53Message.getTTL());
			if (Route53Message.isDebug())
				log("Sending rr change request: " + req);
			Tools.waitFor(route53().changeResourceRecordSets(req));
		} catch (SilentFailure | SdkBaseException e) {
			log("Silently failing Route53 update: " + e);
		}
	}

	private Map.Entry<String,List<String>> getEc2InstancesFromAsgInstances(List<com.amazonaws.services.autoscaling.model.Instance> instances) {
		List<String> instancesToUpdate = new ArrayList<>();
		String host = null;
		for(com.amazonaws.services.autoscaling.model.Instance ins : instances) {
			if(!ins.getHealthStatus().equals("Healthy"))
				continue;
			DescribeInstancesRequest requestEc2 = new DescribeInstancesRequest().withInstanceIds(ins.getInstanceId());
			if(ec2().describeInstances(requestEc2).getReservations().isEmpty() || ec2().describeInstances(requestEc2).getReservations().get(0).getInstances().isEmpty())
				continue;
			com.amazonaws.services.ec2.model.Instance ec2Instance = ec2().describeInstances(requestEc2).getReservations().get(0).getInstances().get(0);
			if(Objects.nonNull(getIPAddress(ec2Instance)))
				instancesToUpdate.add(getIPAddress(ec2Instance));
			if(instancesToUpdate.size()<2)
				host = getHostAddress(ec2Instance);
		}
		return new AbstractMap.SimpleEntry<String,List<String>>(host, instancesToUpdate);
	}

	/**
	 * Start an DNS registration for the launched instance
	 * @param ec2InstanceId instance ID of instance that needs to be registered
	 * @param ttl TTL in seconds to use when creating a new record
	 */
	private void registerInstance(String ec2InstanceId) throws NoIpException{
		log("Registering " + ec2InstanceId);
		try {
			Instance i = getInstance(ec2InstanceId);
			ChangeResourceRecordSetsRequest req = createAddChangeRequest(getIPAddress(i), getHostAddress(i), Route53Message.getTTL());
			if (Route53Message.isDebug())
				log("Sending rr change request: " + req);
			Tools.waitFor(route53().changeResourceRecordSets(req));
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
		log("Deregistering " + ec2InstanceId);
		Instance i = getInstance(ec2InstanceId);
		ChangeResourceRecordSetsRequest req = createRemoveChangeRequest(getIPAddress(i), getHostAddress(i), Route53Message.getTTL());
		if (Route53Message.isDebug())
			log("Sending rr change request: " + req);
		Tools.waitFor(route53().changeResourceRecordSets(req));
	}

	private String getHostAddress(Instance i) {
		String addr = Route53Message.isPrivate() ? i.getPrivateDnsName() : i.getPublicDnsName();
		if (Objects.nonNull(addr) && !addr.isEmpty())
			return addr;
		return getIPAddress(i);
	}

	private String getIPAddress(Instance i) {
		return Route53Message.isPrivate() ? i.getPrivateIpAddress() : i.getPublicIpAddress();
	}

	/**
	 * Create a "remove record" request for the specified instance
	 * @param ip IP Address of the instance to remove from records
	 * @param addr host name of the instance to remove from records
	 * @param ttl TTL in seconds to use when creating a new record
	 * @return record removal request for Route53
	 * @throws NoIpException 
	 */
	private ChangeResourceRecordSetsRequest createRemoveChangeRequest(String ip, String addr, long ttl) throws NoIpException {
		if (Objects.isNull(ip))
			throw new NoIpException("Cowardly refusing to remove an instance with no IP address");
		
		if (Route53Message.isDebug())
			log("Removing instance with addresses: " + ip + ", " + addr);

		ChangeResourceRecordSetsRequest req = null;
		if (message.useDNSRR())
			req = Tools.getAndRemoveRecord(message.getDNSRR_RECORD().stream().map(hostname -> new SimpleEntry<>(hostname, ip)), RRType.A, ttl);
		if (message.useSRV()) {
			ChangeResourceRecordSetsRequest srvReq = Tools.getAndRemoveRecord(message.getSRVEntries(addr).entrySet().stream(), RRType.SRV, ttl);
			if (Objects.isNull(req))
				req = srvReq;
			else {
				// already have a DNS RR change batch in queue, just add our changes
				ChangeBatch b = req.getChangeBatch();
				srvReq.getChangeBatch().getChanges().forEach(b::withChanges);
			}
		}
		
		if (Objects.isNull(req))
			throw new UnsupportedOperationException(
					"Please specify either DNSRR_RECORD or SRV_RECORD");
		return req;
	}

	/**
	 * Create a "add record" request for the specified instance
	 * @param ip IP address of the instance to register
	 * @param addr host name of the instance to register
	 * @param ttl TTL in seconds to use when creating a new record
	 * @return record addition request for Route53
	 * @throws NoIpException 
	 */
	private ChangeResourceRecordSetsRequest createAddChangeRequest(String ip, String addr, long ttl) throws NoIpException {
		if (Objects.isNull(ip))
			throw new NoIpException("Cowardly refusing to add an instance with no IP address");
		
		if (Route53Message.isDebug())
			log("Adding instance with addresses: " + ip + ", " + addr);
		
		ChangeResourceRecordSetsRequest req = null;
		if (message.useDNSRR()) {
			req = Tools.getAndAddRecord(message.getDNSRR_RECORD().stream().map(name -> new SimpleEntry<>(name, ip)), RRType.A, ttl);
		}
		if (message.useSRV()) {
			Map<String, String> records = message.getSRVEntries(addr);
			ChangeResourceRecordSetsRequest srvReq = Tools.getAndAddRecord(records.entrySet().stream(), RRType.SRV, ttl);
			if (Objects.isNull(req))
				req = srvReq;
			else {
				// already have a DNS RR change batch in queue, just add our changes
				ChangeBatch b = req.getChangeBatch();
				srvReq.getChangeBatch().getChanges().forEach(b::withChanges);
			}
		}
		
		if (Objects.isNull(req))
			throw new UnsupportedOperationException(
					"Please specify either DNSRR_RECORD or SRV_RECORD");
		return req;
	}
	
	private ChangeResourceRecordSetsRequest createChangeRequest(List<String> instances, String addr, long ttl) {
//		if (Objects.isNull(ip))
//			throw new SilentFailure("Cowardly refusing to add an instance with no IP address");
//		if (isDebug())
//			log("Adding instance with addresses: " + ip + ", " + addr);
		
		ChangeResourceRecordSetsRequest req = null;
		if (message.useDNSRR())
			req = Tools.createRecordSet(message.getDNSRR_RECORD().stream().map(hostname -> new SimpleEntry<>(hostname, instances)), RRType.A, ttl);
		
		if (message.useSRV()) {
			List<Map.Entry<String, List<String>>> rrsList = new ArrayList<>();
			message.getSRV_RECORD().forEach(conf -> {
				String[] parts = conf.split(":");
				List<String> x = instances.stream().map(ip -> Stream.of(parts[0], parts[1], parts[2], ip).collect(Collectors.joining(" "))).collect(Collectors.toList());
				rrsList.add(new AbstractMap.SimpleEntry<String,List<String>>(parts[3], x));
			});
			ChangeResourceRecordSetsRequest srvReq = Tools.createRecordSet(rrsList.stream(), RRType.SRV, ttl);
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

	protected void log(String message) {
		logger.log(message + "\n");
	}

}
