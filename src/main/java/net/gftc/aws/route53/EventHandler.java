package net.gftc.aws.route53;

import java.io.IOException;
import java.util.AbstractMap.SimpleEntry;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;
import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.RRType;
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

	private LambdaLogger logger;
	private AutoScalingNotification message;
	static private ObjectMapper s_mapper = new ObjectMapper();
	static private AmazonRoute53Client r53 = new AmazonRoute53Client(net.gftc.aws.Tools.getCreds());
	static private AmazonEC2Client ec2 = new AmazonEC2Client(net.gftc.aws.Tools.getCreds());

	/**
	 * Constructor to parse the SNS message and perform additional initialization
	 * @param context Call context from engine
	 * @param event SNS event to process
	 */
	public EventHandler(Context context, SNSRecord event) {
		logger = context.getLogger();
		String snsMessageText = event.getSNS().getMessage();
		if (NotifyRecords.isDebug())
			logger.log("Got SNS message: " + snsMessageText);
		try {
			message = s_mapper.readValue(snsMessageText, 
					AutoScalingNotification.class);
		} catch (IOException e) {
			throw new RuntimeException("Unexpected parsing error: " + e.getMessage(),e);
		} 
	}

	/**
	 * Event handler entry point
	 */
	public void handle() {
		switch (message.getType()) {
		case EC2_INSTANCE_LAUNCH:
			registerInstance(message.getEC2InstanceId());
			break;
		case EC2_INSTANCE_TERMINATE:
		case EC2_INSTANCE_TERMINATE_ERROR:
			deregisterIsntance(message.getEC2InstanceId());
			break;
		default: // do nothing in case of launch error
		}
	}

	/**
	 * Start an DNS registration for the launched instance
	 * @param ec2InstanceId instance ID of instance that needs to be registered
	 */
	private void registerInstance(String ec2InstanceId) {
		logger.log("Registering " + ec2InstanceId);
		Instance i = getInstance(ec2InstanceId);
		Tools.waitFor(r53.changeResourceRecordSets(createAddChangeRequest(i)));
	}
	
	/**
	 * Start a DNS re-registration for the terminated instance
	 * @param ec2InstanceId instance ID of instance that needs to be de-registered
	 */
	private void deregisterIsntance(String ec2InstanceId) {
		logger.log("Deregistering " + ec2InstanceId);
		Instance i = getInstance(ec2InstanceId);
		Tools.waitFor(r53.changeResourceRecordSets(createRemoveChangeRequest(i)));
	}

	/**
	 * Create a "remove record" request for the specified instance
	 * @param i instance to create a de-registration request for
	 * @return record removal request for Route53
	 */
	private ChangeResourceRecordSetsRequest createRemoveChangeRequest(Instance i) {
		if (NotifyRecords.useDNSRR())
			return Tools.getAndRemoveRecord(NotifyRecords.getDNSRR(), RRType.A, i.getPublicDnsName());
		
		if (NotifyRecords.useSRV()) {
			SimpleEntry<String, String> record = NotifyRecords.getSRV(i.getPublicDnsName());
			return Tools.getAndRemoveRecord(record.getKey(), RRType.SRV, record.getValue());
		}
		
		throw new UnsupportedOperationException(
				"Please specify either DNSRR_RECORD or SRV_RECORD");
	}

	/**
	 * Create a "add ercord" request for the specified instance
	 * @param i instance to create a registration request for
	 * @return record addition request for Route53
	 */
	private ChangeResourceRecordSetsRequest createAddChangeRequest(Instance i) {
		if (NotifyRecords.useDNSRR())
			return Tools.getAndAddRecord(NotifyRecords.getDNSRR(), RRType.A, i.getPublicIpAddress());
		
		if (NotifyRecords.useSRV()) {
			SimpleEntry<String, String> record = NotifyRecords.getSRV(i.getPublicDnsName());
			return Tools.getAndAddRecord(record.getKey(), RRType.SRV, record.getValue());
		}
		
		throw new UnsupportedOperationException(
					"Please specify either DNSRR_RECORD or SRV_RECORD");
	}
	
	/**
	 * Helper method to resolve an instance ID to an EC2 instance object
	 * @param ec2InstanceId instance Id to resolve
	 * @return EC2 instance found
	 * @throws RuntimeException in case no instance with the specified ID was found
	 */
	private Instance getInstance(String ec2InstanceId) {
		return ec2.describeInstances(
				new DescribeInstancesRequest().withInstanceIds(ec2InstanceId))
				.getReservations().stream()
				.flatMap(r -> r.getInstances().stream())
				.findFirst()
				.orElseThrow(() -> new RuntimeException(
						"Failed to locate instance " + ec2InstanceId));
	}

}