package net.gftc.aws.route53;

import static net.gftc.aws.Clients.ec2;
import static net.gftc.aws.Clients.route53;
import static net.gftc.aws.route53.NotifyRecords.*;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;
import java.util.Objects;

import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.RRType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import net.gftc.aws.route53.eventhandler.AutoScaling;
import net.gftc.aws.route53.eventhandler.LifeCycle;

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
	
	static {
		s_mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
	}

	/**
	 * Constructor to parse the SNS message and perform additional initialization
	 * @param context Call context from engine
	 * @param event SNS event to process
	 */
	public static EventHandler create(Context context, SNSRecord event) {
		String snsMessageText = event.getSNS().getMessage();
		if (isDebug())
			context.getLogger().log("Got SNS message: " + snsMessageText + "\n");
		try {
			ObjectNode obj = s_mapper.readValue(snsMessageText, ObjectNode.class);
			if (obj.has("LifecycleHookName"))
				return new LifeCycle(context, 
						s_mapper.readValue(snsMessageText, LifeCycleNotification.class));
			else
				return new AutoScaling(context, 
						s_mapper.readValue(snsMessageText, AutoScalingNotification.class));
		} catch (IOException e) {
			throw new RuntimeException("Unexpected parsing error: " + e.getMessage(),e);
		}
	}
	
	protected EventHandler(Context context, EventType eventType, String ec2InstanceId) {
		this.logger = context.getLogger();
		this.eventType = eventType;
		this.ec2instanceId = ec2InstanceId;
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
				deregisterIsntance(ec2instanceId);
				break;
			default: // do nothing in case of launch error or test notifcation
			}
		} catch (SilentFailure e) {
			log("Silently failing Route53 update: " + e.getMessage());
		}
	}

	/**
	 * Start an DNS registration for the launched instance
	 * @param ec2InstanceId instance ID of instance that needs to be registered
	 */
	private void registerInstance(String ec2InstanceId) {
		log("Registering " + ec2InstanceId);
		Instance i = getInstance(ec2InstanceId);
		ChangeResourceRecordSetsRequest req = createAddChangeRequest(i);
		if (isDebug())
			log("Sending rr change requset: " + req);
		Tools.waitFor(route53().changeResourceRecordSets(req));
	}
	
	/**
	 * Start a DNS re-registration for the terminated instance
	 * @param ec2InstanceId instance ID of instance that needs to be de-registered
	 */
	private void deregisterIsntance(String ec2InstanceId) {
		log("Deregistering " + ec2InstanceId);
		Instance i = getInstance(ec2InstanceId);
		ChangeResourceRecordSetsRequest req = createRemoveChangeRequest(i);
		if (isDebug())
			log("Sending rr change request: " + req);
		Tools.waitFor(route53().changeResourceRecordSets(req));
	}

	/**
	 * Create a "remove record" request for the specified instance
	 * @param i instance to create a de-registration request for
	 * @return record removal request for Route53
	 */
	private ChangeResourceRecordSetsRequest createRemoveChangeRequest(Instance i) {
		if (Objects.isNull(i.getPublicIpAddress()))
			throw new SilentFailure("Corwardly refusing to remove an instance with no IP address");
		
		if (isDebug())
			log("Removing instance with addresses: " + i.getPublicIpAddress() + ", " + i.getPublicDnsName());

		ChangeResourceRecordSetsRequest req = null;
		if (useDNSRR())
			req = Tools.getAndRemoveRecord(getDNSRR(), RRType.A, i.getPublicIpAddress());
		
		if (useSRV()) {
			SimpleEntry<String, String> record = getSRV(i.getPublicDnsName());
			ChangeResourceRecordSetsRequest srvReq = Tools.getAndRemoveRecord(record.getKey(), RRType.SRV, record.getValue());
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
	 * Create a "add ercord" request for the specified instance
	 * @param i instance to create a registration request for
	 * @return record addition request for Route53
	 */
	private ChangeResourceRecordSetsRequest createAddChangeRequest(Instance i) {
		if (Objects.isNull(i.getPublicIpAddress()))
			throw new SilentFailure("Corwardly refusing to add an instance with no IP address");
		
		if (isDebug())
			log("Adding instance with addresses: " + i.getPublicIpAddress() + ", " + i.getPublicDnsName());
		
		ChangeResourceRecordSetsRequest req = null;
		if (useDNSRR())
			req = Tools.getAndAddRecord(getDNSRR(), RRType.A, i.getPublicIpAddress());
		
		if (useSRV()) {
			SimpleEntry<String, String> record = getSRV(i.getPublicDnsName());
			ChangeResourceRecordSetsRequest srvReq = Tools.getAndAddRecord(record.getKey(), RRType.SRV, record.getValue());
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
