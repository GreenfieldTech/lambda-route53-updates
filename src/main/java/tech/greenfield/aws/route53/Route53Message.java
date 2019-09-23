package tech.greenfield.aws.route53;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;
import com.amazonaws.services.route53.model.*;
import com.amazonaws.services.sqs.model.Message;
import com.fasterxml.jackson.databind.*;

import tech.greenfield.aws.route53.eventhandler.AutoScaling;
import tech.greenfield.aws.route53.eventhandler.LifeCycle;

public class Route53Message {
	
	private Map<String, Object> body;
	private Metadata metadata;
	static private ObjectMapper s_mapper = new ObjectMapper();
	private final Logger logger = Logger.getLogger(getClass().getName());
	private static final long DEFAULT_TTL = 300;

	static {
		s_mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
		s_mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
	}
	
	public Route53Message(Message sqs) throws ParsingException {
		body = retreiveBody(sqs.getBody());
		if(Route53Message.isDebug())
			logger.info("SQS message body: " + body);
		readMetadata();
	}

	private void readMetadata() throws ParsingException {
		try {
			if (body.containsKey("NotificationMetadata"))
				metadata = s_mapper.readValue(String.valueOf(body.get("NotificationMetadata")), Metadata.class);
			else
				metadata = Metadata.fromEnvironment();
			if(Route53Message.isDebug())
				dumpConfiguration();
		} catch (IOException e) {
			throw new ParsingException(e);
		}
	}
	
	public Route53Message(SNSRecord sns) throws ParsingException {
		body = retreiveBody(sns.getSNS().getMessage());
		if(Route53Message.isDebug())
			logger.info("SNS message body: " + body);
		readMetadata();
	}
	
	private void dumpConfiguration() {
		logger.info("SRV_RECORD: " + metadata.getSRVSpec());
		logger.info("SRV4_RECORD: " + metadata.getSRV4Spec());
		logger.info("SRV6_RECORD: " + metadata.getSRV6Spec());
		logger.info("DNSRR_RECORD: " + metadata.getRRSpec());
		logger.info("DNSRR4_RECORD: " + metadata.getRR4Spec());
		logger.info("DNSRR6_RECORD: " + metadata.getRR6Spec());
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> retreiveBody(String messageText) throws ParsingException{
		Map<String, Object> obj = null;
		try {
			obj = s_mapper.readValue(messageText, Map.class);
			if (obj.containsKey("Message")) // read SQS message content, if exists
				obj.putAll(s_mapper.readValue(obj.get("Message").toString(), Map.class));
			return obj;
		} catch (IOException e) {
			throw new ParsingException(e);
		}
	}
	
	public EventHandler createEventHandler(Context context) {
		if (body.containsKey("LifecycleTransition")) 
			return new LifeCycle(context, s_mapper.convertValue(body, LifeCycleNotification.class), this);
		else
			return new AutoScaling(context, s_mapper.convertValue(body, AutoScalingNotification.class), this);
	}
	
	/**
	 * Check if SRV record update was requested by specifying the SRV_RECORD environment variable
	 * This setting is mandatory if DNSRR_RECORD is not set
	 * @return true if SRV record update is requested
	 */
	public boolean useSRV() {
		return metadata.hasSRV();
	}
	
	/**
	 * Check if a DNS round-robin record update was requested by specifing the 
	 * 	DNSRR_RECORD environment variable
	 * This setting is mandatory if SRV_RECORD is not set
	 * @return true if DNS round-robin record update is requested
	 */
	public boolean useDNSRR() {
		return metadata.hasDNSRR();
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
		Stream<ResourceRecordSet> dnsrr = metadata.getRRSpec().stream()
				.flatMap(addr -> Stream.of(RRType.A, RRType.AAAA).map(r -> new ResourceRecordSet(addr, r)));
		Stream<ResourceRecordSet> dnsrr4 = metadata.getRR4Spec().stream()
				.flatMap(addr -> Stream.of(RRType.A).map(r -> new ResourceRecordSet(addr, r)));
		Stream<ResourceRecordSet> dnsrr6 = metadata.getRR6Spec().stream()
				.flatMap(addr -> Stream.of(RRType.AAAA).map(r -> new ResourceRecordSet(addr, r)));
		return Stream.concat(dnsrr, Stream.concat(dnsrr4, dnsrr6))
				.map(rr -> new Change(ChangeAction.DELETE, rr))
				.collect(Collectors.toList());
	}
	
	private List<Change> getSRVDeleteChanges() {
		Stream<ResourceRecordSet> srv = metadata.getSRVSpec().stream()
				.map(s -> new ResourceRecordSet(s.addr, RRType.SRV));
		Stream<ResourceRecordSet> srv4 = metadata.getSRV6Spec().stream()
				.map(s -> new ResourceRecordSet(s.addr, RRType.SRV));
		Stream<ResourceRecordSet> srv6 = metadata.getSRV4Spec().stream()
				.map(s -> new ResourceRecordSet(s.addr, RRType.SRV));
		return Stream.concat(srv, Stream.concat(srv4, srv6))
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
				out.getChanges().addAll(getDNSRRUpsertChanges(i).stream().collect(new BatchChangesByName()));
		if (useSRV())
			for (Instance i : instances)
				out.getChanges().addAll(getSRVUpsertChanges(i).stream().collect(new BatchChangesByName()));
		return out;
	}

	private List<Change> getDNSRRUpsertChanges(Instance i) throws NoIpException {
		String ipv4ip = Tools.getIPAddress(i);
		String ipv6ip = Tools.getIPv6Address(i);
		Stream<ResourceRecordSet> addrs = Stream.empty();
		if (Objects.nonNull(ipv4ip))
			addrs = Stream.concat(addrs, Stream.concat(metadata.getRRSpec().stream(), metadata.getRR4Spec().stream())
					.map(addr -> new ResourceRecordSet().withType(RRType.A).withName(addr)
							.withTTL(getTTL()).withResourceRecords(new ResourceRecord(ipv4ip))));
		if (Objects.nonNull(ipv6ip))
			addrs = Stream.concat(addrs, Stream.concat(metadata.getRRSpec().stream(), metadata.getRR6Spec().stream())
					.map(addr -> new ResourceRecordSet().withType(RRType.AAAA).withName(addr)
							.withTTL(getTTL()).withResourceRecords(new ResourceRecord(ipv6ip))));
		return addrs
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
		for (SRVTemplate s : metadata.getSRVSpec())
			map.get(s.addr).add(s);
		for (SRVTemplate s : metadata.getSRV4Spec())
			map.get(s.addr).add(s);
		for (SRVTemplate s : metadata.getSRV6Spec())
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
		return Stream.concat(metadata.getRRSpec().stream(), metadata.getRR4Spec().stream())
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
		return Stream.concat(metadata.getRRSpec().stream(), metadata.getRR6Spec().stream())
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
		for (SRVTemplate s : metadata.getSRVSpec())
			map.get(s.addr).add(s);
		for (SRVTemplate s : metadata.getSRV4Spec())
			map.get(s.addr).add(s);
		for (SRVTemplate s : metadata.getSRV6Spec())
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
