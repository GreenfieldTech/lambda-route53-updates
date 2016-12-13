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

public class EventHandler {

	private LambdaLogger logger;
	private AutoScalingNotification message;
	static private ObjectMapper s_mapper = new ObjectMapper();
	static private AmazonRoute53Client r53 = new AmazonRoute53Client(net.gftc.aws.Tools.getCreds());
	static private AmazonEC2Client ec2 = new AmazonEC2Client(net.gftc.aws.Tools.getCreds());

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

	private void registerInstance(String ec2InstanceId) {
		logger.log("Registering " + ec2InstanceId);
		Instance i = getInstance(ec2InstanceId);
		Tools.waitFor(r53.changeResourceRecordSets(createAddChangeRequest(i)));
	}
	
	private void deregisterIsntance(String ec2InstanceId) {
		logger.log("Deregistering " + ec2InstanceId);
		Instance i = getInstance(ec2InstanceId);
		Tools.waitFor(r53.changeResourceRecordSets(createRemoveChangeRequest(i)));
	}

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
