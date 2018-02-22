package tech.greenfield.aws.route53;

import java.io.IOException;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;
import com.amazonaws.services.sqs.model.Message;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Route53Message{
	
	private Message message;
	private Map<String, Object> body;
	private Metadata metadata;
	private List<String> SRV_RECORD;
	private List<String> DNSRR_RECORD;
	static private ObjectMapper s_mapper = new ObjectMapper();
	private final static Logger logger = Logger.getLogger(NotifyRecordsSqs.class.getName());
	private static final long DEFAULT_TTL = 300;

	static {
		s_mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
	}
	
	public Route53Message(Message sqs) {
		this.message = sqs;
		this.body = retreiveBody();
		if(Route53Message.isDebug())
			logger.info("SQS message body: " + body);
		try {
			if(Objects.isNull(body.get("NotificationMetadata")))
				throw new IOException("No metadata was sent");
			String metadataStr = body.get("NotificationMetadata").toString();
			metadata = s_mapper.readValue(metadataStr, Metadata.class);
			this.SRV_RECORD = metadata.getSRV_RECORD();
			this.DNSRR_RECORD = metadata.getDNSRR_RECORD();
		} catch (IOException e) {
			e.printStackTrace();
		}
		if(Route53Message.isDebug())
			logger.info("SRV_RECORD: " + this.SRV_RECORD + ", DNSRR_RECORD: " + this.DNSRR_RECORD);
	}
	
	@SuppressWarnings("unchecked")
	public Route53Message(SNSRecord sns) {
		try {
			this.body = s_mapper.readValue(sns.getSNS().getMessage(), Map.class);
		} catch (IOException e) {
			e.printStackTrace();
		}
		this.SRV_RECORD = getEnvByPrefix("SRV_RECORD");
		this.DNSRR_RECORD = getEnvByPrefix("DNSRR_RECORD");
		if(Route53Message.isDebug())
			logger.info("SRV_RECORD: " + this.SRV_RECORD + " DNSRR_RECORD: " + this.DNSRR_RECORD);
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
	
	@SuppressWarnings("unchecked")
	public Map<String, Object> retreiveBody(){
		String sqsMessageText = this.message.getBody();
		Map<String, Object> obj = null;
		try {
			obj = s_mapper.readValue(sqsMessageText, Map.class);
			obj.putAll(s_mapper.readValue(obj.get("Message").toString(), Map.class));
		} catch (IOException e) {
			e.printStackTrace();
		}
		return obj;
	}
	
	public Map<String, Object> getBody(){
		return body;
	}
	
	public List<String> getSRV_RECORD() {
		return SRV_RECORD;
	}
	
	public List<String> getDNSRR_RECORD() {
		return DNSRR_RECORD;
	}

	public Message getMessage() {
		return message;
	}
	
	/**
	 * Check if SRV record update was requested by specifying the SRV_RECORD environemtn variable
	 * This setting is mandatory if DNSRR_RECORD is not set
	 * @return true if SRV record update is requested
	 */
	public boolean useSRV() {
		return !getSRV_RECORD().isEmpty();
	}
	
	/**
	 * Format and retrieve the SRV record and FQDN that need to be updated
	 * This setting is mandatory if DNSRR_RECORD is not set
	 * @param hostname FQDN for which to generate an SRV record using the schema
	 * 	specified by the SRV_RECORD environment variable
	 * @return A pair where the key is the FQDN to update and the value is the
	 * 	record set to update (add or remove a record)
	 */
	public Map<String,String> getSRVEntries(String hostname) {
		return getDNSRR_RECORD().stream().map(var -> {
			if (Objects.isNull(var) || var.isEmpty())
				throw new UnsupportedOperationException("Cannot construct SRV record without SRV_RECORD environment variable");
			String[] parts = var.split(":");
			if (parts.length != 4)
				throw new UnsupportedOperationException("Invalid SRV_RECORD format - " + "must conform to format '<priority>:<weight>:<port>:<name>'. currently is: " + var + " of length: " + parts.length);
			return new AbstractMap.SimpleEntry<String,String>(parts[3], Stream.of(parts[0], parts[1],parts[2],hostname).collect(Collectors.joining(" ")));
		}).collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
	}
	
	/**
	 * Check if a DNS round-robin record update was requested by specifing the 
	 * 	DNSRR_RECORD environment variable
	 * This setting is mandatory if SRV_RECORD is not set
	 * @return true if DNS round-robin record update is requested
	 */
	public boolean useDNSRR() {
		return !getDNSRR_RECORD().isEmpty();
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
	
	public static boolean isPrivate() {
		String privateIP = System.getenv("DNSRR_PRIVATE");
		return Objects.nonNull(privateIP) && !privateIP.isEmpty();
	}
	
	/**
	 * Check if we need to enable debug mode
	 * @return true if debug mode was requested by setting the DEBUG environment variable
	 */
	public static boolean isDebug() {
		return Objects.nonNull(System.getenv("DEBUG")) && !System.getenv("DEBUG").isEmpty();
	}
	
	
}
