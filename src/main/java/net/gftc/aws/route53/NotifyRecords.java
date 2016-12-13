package net.gftc.aws.route53;

import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;
import com.amazonaws.services.sqs.model.UnsupportedOperationException;

public class NotifyRecords implements RequestHandler<SNSEvent, String>{
	
	public String handleRequest(SNSEvent input, Context context) {
		if (Objects.isNull(input)) {
			context.getLogger().log("Invalid SNS input object");
			return "Error: no SNS event input";
		}
		List<SNSRecord> records = input.getRecords();
		if (Objects.isNull(records)) {
			context.getLogger().log("No SNS events in input");
			return "Error: no SNS events";
		}
		records.parallelStream()
			.map(e -> new EventHandler(context, e))
			.forEach(EventHandler::handle);
		return "OK";
	}
	
	public static boolean isDebug() {
		String debug = System.getenv("DEBUG");
		return Objects.nonNull(debug) && !debug.isEmpty();
	}
	
	public static boolean useSRV() {
		String var = System.getenv("SRV_RECORD");
		return Objects.nonNull(var) && !var.isEmpty();
	}
	
	public static SimpleEntry<String,String> getSRV(String hostname) {
		String var = System.getenv("SRV_RECORD");
		if (Objects.isNull(var) || var.isEmpty())
			throw new UnsupportedOperationException(
					"Cannot construct SRV record without SRV_RECORD environment variable");
		String[] parts = var.split(":");
		if (parts.length != 4)
			throw new UnsupportedOperationException("Invalid SRV_RECORD format - " +
					"must conform to format '<priority>:<weight>:<port>:<name>'");
		return new AbstractMap.SimpleEntry<String,String>(
				parts[3],
				Stream.of(parts[0], parts[1],parts[2],hostname)
					.collect(Collectors.joining(" ")));
	}
	
	public static boolean useDNSRR() {
		String var = System.getenv("DNSRR_RECORD");
		return Objects.nonNull(var) && !var.isEmpty();
	}
	
	public static String getDNSRR() {
		String var = System.getenv("DNSRR_RECORD");
		if (Objects.isNull(var) || var.isEmpty())
			throw new UnsupportedOperationException(
					"Cannot construct DNS-RR record without DNSRR_RECORD environment variable");
		return var;
	}

	public static String getHostedZoneId() {
		String var = System.getenv("HOSTED_ZONE_ID");
		if (Objects.isNull(var) || var.isEmpty())
			throw new UnsupportedOperationException(
					"Please specify Route53 zone ID using HOSTED_ZONE_ID environment variable");
		return var;
	}
}
