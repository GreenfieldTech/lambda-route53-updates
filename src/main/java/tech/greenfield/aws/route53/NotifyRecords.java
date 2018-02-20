package tech.greenfield.aws.route53;

import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.logging.Logger;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;

public class NotifyRecords implements RequestHandler<SNSEvent, Route53UpdateResponse>{
	
	private static final long DEFAULT_TTL = 300;
	private static final String MODE = System.getenv("MODE");
	private static NotifyRecordsSqs notifyRecordsSqs= null;
	private final static Logger logger = Logger.getLogger(NotifyRecordsSqs.class.getName());
	
	@Override
	public Route53UpdateResponse handleRequest(SNSEvent input, Context context) {
		LambdaLogger logger = context.getLogger();
		logger.log("Got SNS event: " + input);
		if(MODE.equals("SNS"))
			return new NotifyRecordsSns().handleRequest(input, context);
		if(MODE.equals("SQS")) {
			logger.log("enter SQS mode");
			notifyRecordsSqs = new NotifyRecordsSqs();
			
			return notifyRecordsSqs.handleRequest(input, context);
		}
		return null;
	}

	/**
	 * Check if we need to enable debug mode
	 * @return true if debug mode was requested by setting the DEBUG environment variable
	 */
	public static boolean isDebug() {
		return Objects.nonNull(System.getenv("DEBUG")) && !System.getenv("DEBUG").isEmpty();
	}
	
	public static long getTTL() {
		String ttl = System.getenv("TTL");
		if (Objects.nonNull(ttl))
			try {
				return Long.parseLong(ttl);
			} catch (NumberFormatException e) {
				return DEFAULT_TTL;
			}
		return DEFAULT_TTL;
	}
	
	public static boolean isPrivate() {
		String privateIP = System.getenv("DNSRR_PRIVATE");
		return Objects.nonNull(privateIP) && !privateIP.isEmpty();
	}

	/**
	 * Retrieve all environment values whose keys start with the specified prefix
	 * @param prefix environment variable name prefix
	 * @return List of environment values
	 */
	private static List<String> getEnvByPrefix(String prefix) {
		return System.getenv().entrySet().stream().filter(e -> e.getKey().startsWith(prefix))
				.map(e -> e.getValue()).collect(Collectors.toList());
	}
	
	/**
	 * Retrieve all SRV_RECORD configurations
	 * @return list of SRV_RECORD configurations
	 */
	public static List<String> getSRVConfiguration() {
		List<String> SRVConfiguration = new ArrayList<>();
		if(MODE.equals("SQS"))
			SRVConfiguration.add(notifyRecordsSqs.getSqsMessage().getSRV_RECORD());
		if(Objects.isNull(SRVConfiguration))
			SRVConfiguration = getEnvByPrefix("SRV_RECORD");
		logger.info("SRVConfiguration: " + SRVConfiguration + " (with mode " + MODE + ")");
		return SRVConfiguration;
	}
	
	/**
	 * Retrieve all DNSRR_RECORD configurations
	 * @return list of DNSRR_RECORD configurations
	 */
	public static List<String> getDNSRRConfiguration() {
		List<String> DNSRRConfiguration = new ArrayList<>();
		if(MODE.equals("SQS"))
			DNSRRConfiguration.add(notifyRecordsSqs.getSqsMessage().getDNSRR_RECORD());
		if(Objects.isNull(DNSRRConfiguration))
			DNSRRConfiguration = getEnvByPrefix("DNSRR_RECORD");
		logger.info("DNSRRConfiguration: " + DNSRRConfiguration + " (with mode " + MODE + ")");
		return DNSRRConfiguration;
	}
	
	/**
	 * Check if SRV record update was requested by specifying the SRV_RECORD environemtn variable
	 * This setting is mandatory if DNSRR_RECORD is not set
	 * @return true if SRV record update is requested
	 */
	public static boolean useSRV() {
		return !getSRVConfiguration().isEmpty();
	}
	
	/**
	 * Format and retrieve the SRV record and FQDN that need to be updated
	 * This setting is mandatory if DNSRR_RECORD is not set
	 * @param hostname FQDN for which to generate an SRV record using the schema
	 * 	specified by the SRV_RECORD environment variable
	 * @return A pair where the key is the FQDN to update and the value is the
	 * 	record set to update (add or remove a record)
	 */
	public static Map<String,String> getSRVEntries(String hostname) {
		return getSRVConfiguration().stream().map(var -> {
			if (Objects.isNull(var) || var.isEmpty())
				throw new UnsupportedOperationException("Cannot construct SRV record without SRV_RECORD environment variable");
			String[] parts = var.split(":");
			if (parts.length != 4)
				throw new UnsupportedOperationException("Invalid SRV_RECORD format - " + "must conform to format '<priority>:<weight>:<port>:<name>'");
			return new AbstractMap.SimpleEntry<String,String>(parts[3], Stream.of(parts[0], parts[1],parts[2],hostname).collect(Collectors.joining(" ")));
		}).collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
	}
	
	/**
	 * Check if a DNS round-robin record update was requested by specifing the 
	 * 	DNSRR_RECORD environment variable
	 * This setting is mandatory if SRV_RECORD is not set
	 * @return true if DNS round-robin record update is requested
	 */
	public static boolean useDNSRR() {
		return !getDNSRRConfiguration().isEmpty();
	}

	/**
	 * Get the Route53 hosted zone ID to update, as specified in the HOSTED_ZONE_ID environment variable.
	 * This setting is mandatory.
	 * @return the Route53 hosted zone ID to update
	 */
	public static String getHostedZoneId() {
		String var = System.getenv("HOSTED_ZONE_ID");
		if (Objects.isNull(var) || var.isEmpty())
			throw new UnsupportedOperationException(
					"Please specify Route53 zone ID using HOSTED_ZONE_ID environment variable");
		return var;
	}
	
	protected Route53UpdateResponse ok() {
		return new Route53UpdateResponse(true, "OK");
	}

	protected Route53UpdateResponse error(String message) {
		return new Route53UpdateResponse(false, "Error: " + message);
	}

}
