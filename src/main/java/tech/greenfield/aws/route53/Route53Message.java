package tech.greenfield.aws.route53;

import java.io.IOException;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;
import com.amazonaws.services.route53.model.*;
import com.amazonaws.services.sqs.model.Message;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Route53Message {
	
	public static class SRVTemplate {
		private String prio;
		private String weight;
		private String port;
		private String addr;
		
		public SRVTemplate(String rec) {
			String[] fields = rec.split(":");
			if (fields.length != 4)
				throw new InvalidInputException("SRV template '" + rec + "' is invalid");
			prio = fields[0];
			weight = fields[1];
			port = fields[2];
			addr = fields[3];
			if (!addr.endsWith("."))
				addr += ".";
		}
		public static List<SRVTemplate> parse(List<String> record) {
			return record.stream().map(SRVTemplate::new).collect(Collectors.toList());
		}
		public ResourceRecord getResourceRecord(Instance i) throws NoIpException {
			return getResourceRecord(Tools.getHostAddress(i));
		}
		public ResourceRecord getResourceRecord(String ip) {
			return new ResourceRecord(String.join(" ", new String[]{
					prio, weight, port, ip
				}));
		}
		public String toString() {
			return String.join(":", new String[] {
					prio, weight, port, addr
			});
		}
		public String getAddr() {
			return addr;
		}
	}
	
	private Message message;
	private Map<String, Object> body;
	private Metadata metadata;
	private List<SRVTemplate> SRV_RECORD;
	private List<String> DNSRR_RECORD;
	static private ObjectMapper s_mapper = new ObjectMapper();
	private final static Logger logger = Logger.getLogger(NotifyRecordsSqs.class.getName());
	private static final long DEFAULT_TTL = 300;

	static {
		s_mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
		s_mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
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
			this.SRV_RECORD = SRVTemplate.parse(metadata.getSRV_RECORD());
			this.DNSRR_RECORD = metadata.getDNSRR_RECORD().stream().map(addr -> 
					addr.endsWith(".") ? addr : (addr + ".")).collect(Collectors.toList());
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
		this.SRV_RECORD = SRVTemplate.parse(getEnvByPrefix("SRV_RECORD"));
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
	
	public List<SRVTemplate> getSRV_RECORD() {
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
		return (Objects.nonNull(getSRV_RECORD()) && !getSRV_RECORD().isEmpty());
	}
	
	/**
	 * Format and retrieve the SRV record and FQDN that need to be updated
	 * This setting is mandatory if DNSRR_RECORD is not set
	 * @param hostname FQDN for which to generate an SRV record using the schema
	 * 	specified by the SRV_RECORD environment variable
	 * @return A pair where the key is the FQDN to update and the value is the
	 * 	record set to update (add or remove a record)
	 */
	public Map<String,List<String>> getSRVEntries(String hostname) {
		return getSRV_RECORD().stream().map(var -> {
			return new AbstractMap.SimpleEntry<String,String>(var.addr, Stream.of(var.prio, var.weight,var.port,hostname).collect(Collectors.joining(" ")));
		}).collect(Collectors.groupingBy(SimpleEntry::getKey, Collectors.mapping(SimpleEntry::getValue, Collectors.toList())));
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

	public ChangeBatch getDeleteChanges() {
		ChangeBatch out = new ChangeBatch();
		if (useDNSRR())
			out.getChanges().addAll(getDNSRRDeleteChanges());
		if (useSRV())
			out.getChanges().addAll(getSRVDeleteChanges());
		return out;
	}

	private List<Change> getDNSRRDeleteChanges() {
		return DNSRR_RECORD.stream()
				.flatMap(addr -> Stream.of(RRType.A, RRType.AAAA).map(r -> new ResourceRecordSet(addr, r)))
				.map(rr -> new Change(ChangeAction.DELETE, rr))
				.collect(Collectors.toList());
	}
	
	private List<Change> getSRVDeleteChanges() {
		return SRV_RECORD.stream()
				.map(s -> new ResourceRecordSet(s.addr, RRType.SRV))
				.map(rr -> new Change(ChangeAction.DELETE, rr))
				.collect(Collectors.toList());
	}

	public ChangeBatch getUpsertChanges(List<Instance> instances) throws NoIpException {
		return getUpsertChanges(instances.toArray(new Instance[] {}));
	}
	
	public ChangeBatch getUpsertChanges(Instance... instances) throws NoIpException {
		ChangeBatch out = new ChangeBatch();
		if (useDNSRR())
			for (Instance i : instances)
				out.getChanges().addAll(getDNSRRUpsertChanges(i));
		if (useSRV())
			for (Instance i : instances)
				out.getChanges().addAll(getSRVUpsertChanges(i));
		return out;
	}

	private List<Change> getDNSRRUpsertChanges(Instance i) throws NoIpException {
		String ipv4ip = Tools.getIPAddress(i);
		String ipv6ip = Tools.getIPv6Address(i);
		return DNSRR_RECORD.stream()
				.flatMap(addr -> {
					ResourceRecordSet ipv4 = new ResourceRecordSet().withType(RRType.A).withName(addr)
							.withTTL(getTTL()).withResourceRecords(new ResourceRecord(ipv4ip));
					return Objects.isNull(ipv6ip) ? Stream.of(ipv4) : 
						Stream.of(ipv4, new ResourceRecordSet().withType(RRType.AAAA).withName(addr)
								.withTTL(getTTL()).withResourceRecords(new ResourceRecord(ipv6ip)));
				})
				.map(rr -> new Change(ChangeAction.UPSERT, rr))
				.collect(Collectors.toList());
	}
	
	private List<Change> getSRVUpsertChanges(Instance i) throws NoIpException {
		HashMap<String, List<SRVTemplate>> map = new HashMap<String, List<SRVTemplate>>() {
			private static final long serialVersionUID = 1L;

			public List<SRVTemplate> get(Object key) {
				if (containsKey(key))
					return super.get(key);
				ArrayList<SRVTemplate> list = new ArrayList<SRVTemplate>();
				put(key.toString(),list);
				return list;
			}
		};
		for (SRVTemplate s : SRV_RECORD)
			map.get(s.addr).add(s);
		
		return map.entrySet().stream()
				.map(ent -> 
					new ResourceRecordSet().withType(RRType.SRV).withName(ent.getKey())
						.withTTL(getTTL()).withResourceRecords(
								ent.getValue().stream().map(s -> s.getResourceRecord(i)).collect(Collectors.toList())
								)
				)
				.map(rr -> new Change(ChangeAction.UPSERT, rr))
				.collect(Collectors.toList());
	}

	public ChangeBatch getRemoveChanges(Instance i) throws NoIpException {
		ChangeBatch out = new ChangeBatch();
		if (useDNSRR()) {
			out.getChanges().addAll(getDNSRR4RemoveChanges(i));
			out.getChanges().addAll(getDNSRR6RemoveChanges(i));
		}
		if (useSRV())
			out.getChanges().addAll(getSRVRemoveChanges(i));
		return out;
	}
	
	private List<Change> getDNSRR4RemoveChanges(Instance i) throws NoIpException {
		String ip = Tools.getIPAddress(i);
		return DNSRR_RECORD.stream()
				.map(s -> Tools.getRecordSet(s, RRType.A.toString()))
				.filter(Objects::nonNull)
				.map(rr -> {
					if (Objects.isNull(rr))
						return null;
					if (rr.getResourceRecords().size() == 1 && rr.getResourceRecords().get(0).getValue().equals(ip))
						return new Change(ChangeAction.DELETE, rr);
					rr.getResourceRecords().removeIf(r -> r.getValue().equals(ip));
					return new Change(ChangeAction.UPSERT, rr);
				})
				.collect(Collectors.toList());
	}
	
	private List<Change> getDNSRR6RemoveChanges(Instance i) throws NoIpException {
		String ip = Tools.getIPv6Address(i);
		return DNSRR_RECORD.stream()
				.map(s -> Tools.getRecordSet(s, RRType.AAAA.toString()))
				.filter(Objects::nonNull)
				.map(rr -> {
					if (rr.getResourceRecords().size() == 1 && rr.getResourceRecords().get(0).getValue().equals(ip))
						return new Change(ChangeAction.DELETE, rr);
					rr.getResourceRecords().removeIf(r -> r.getValue().equals(ip));
					return new Change(ChangeAction.UPSERT, rr);
				})
				.collect(Collectors.toList());
	}
	
	private List<Change> getSRVRemoveChanges(Instance i) throws NoIpException {
		String host = Tools.getHostAddress(i);
		HashMap<String, List<SRVTemplate>> map = new HashMap<String, List<SRVTemplate>>() {
			private static final long serialVersionUID = 1L;

			public List<SRVTemplate> get(Object key) {
				if (containsKey(key))
					return super.get(key);
				ArrayList<SRVTemplate> list = new ArrayList<SRVTemplate>();
				put(key.toString(),list);
				return list;
			}
		};
		for (SRVTemplate s : SRV_RECORD)
			map.get(s.addr).add(s);
		return map.entrySet().stream()
				.map(ent -> {
					ResourceRecordSet rr = Tools.getRecordSet(ent.getKey(), RRType.SRV.toString());
					if (Objects.isNull(rr))
						return null;
					ArrayList<ResourceRecord> newRRs = new ArrayList<>(rr.getResourceRecords());
					for (SRVTemplate s : ent.getValue())
						newRRs.remove(s.getResourceRecord(host));
					if (newRRs.isEmpty())
						return new Change(ChangeAction.DELETE, rr);
					rr.setResourceRecords(newRRs);
					return new Change(ChangeAction.UPSERT, rr);
				})
				.filter(Objects::nonNull)
				.collect(Collectors.toList());
	}
	
}
