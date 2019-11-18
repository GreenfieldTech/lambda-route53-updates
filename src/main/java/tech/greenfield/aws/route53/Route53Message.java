package tech.greenfield.aws.route53;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;

import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.route53.model.*;
import software.amazon.awssdk.services.sqs.model.Message;
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
		body = retreiveBody(sqs.body());
		logger.fine("SQS message body: " + json(body));
		logger.fine("Request: " + String.valueOf(body.get("Message")));
		readMetadata();
	}

	private void readMetadata() throws ParsingException {
		try {
			if (body.containsKey("NotificationMetadata"))
				metadata = s_mapper.readValue(String.valueOf(body.get("NotificationMetadata")), Metadata.class);
			else
				metadata = Metadata.fromEnvironment();
			dumpConfiguration();
		} catch (IOException e) {
			throw new ParsingException(e);
		}
	}
	
	public Route53Message(SNSRecord sns) throws ParsingException {
		body = retreiveBody(sns.getSNS().getMessage());
		logger.fine("SNS message body: " + body);
		readMetadata();
	}
	
	@SuppressWarnings("serial")
	private void dumpConfiguration() {
		logger.fine(json(new HashMap<String,Object>() {{
			put("SRV_RECORD",  metadata.getSRVSpec());
			put("SRV4_RECORD", metadata.getSRV4Spec());
			put("SRV6_RECORD", metadata.getSRV6Spec());
			put("DNSRR_RECORD", metadata.getRRSpec());
			put("DNSRR4_RECORD", metadata.getRR4Spec());
			put("DNSRR6_RECORD", metadata.getRR6Spec());
		}}));
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
		ArrayList<Change> changes = new ArrayList<>();
		if (useDNSRR())
			changes.addAll(getDNSRRDeleteChanges());
		if (useSRV())
			changes.addAll(getSRVDeleteChanges());
		return ChangeBatch.builder().changes(changes).build();
	}

	private List<Change> getDNSRRDeleteChanges() {
		return Stream.concat(metadata.getRRSpec().stream()
				.flatMap(addr -> Stream.of(RRType.A, RRType.AAAA)
						.map(r -> ResourceRecordSet.builder().name(addr).type(r).build())),
				Stream.concat(metadata.getRR4Spec().stream()
						.flatMap(addr -> Stream.of(RRType.A)
								.map(r -> ResourceRecordSet.builder().name(addr).type(r).build())),
						metadata.getRR6Spec().stream()
						.flatMap(addr -> Stream.of(RRType.AAAA)
								.map(r -> ResourceRecordSet.builder().name(addr).type(r).build()))))
				.map(rr -> Change.builder().action(ChangeAction.DELETE).resourceRecordSet(rr).build())
				.collect(Collectors.toList());
	}
	
	private List<Change> getSRVDeleteChanges() {
		return Stream.concat(metadata.getSRVSpec().stream(),
				Stream.concat(metadata.getSRV6Spec().stream(), metadata.getSRV4Spec().stream()))
				.map(s -> ResourceRecordSet.builder().name(s.addr).type(RRType.SRV).build())
				.map(rr -> Change.builder().action(ChangeAction.DELETE).resourceRecordSet(rr).build())
				.collect(Collectors.toList());
	}

	public CompletableFuture<ChangeBatch> getUpsertChanges(List<Instance> instances) throws NoIpException {
		return getUpsertChanges(instances.toArray(new Instance[instances.size()]));
	}
	
	public CompletableFuture<ChangeBatch> getUpsertChanges(Instance... instances) throws NoIpException {
		ArrayList<Change> changes = new ArrayList<>();
		if (useDNSRR())
			for (Instance i : instances)
				changes.addAll(getDNSRRUpsertChanges(i).stream().collect(new BatchChangesByName()));
		if (useSRV())
			for (Instance i : instances)
				changes.addAll(getSRVUpsertChanges(i).stream().collect(new BatchChangesByName()));
		// sync adds with existing records
		return changes.stream()
				.map(c -> { // resolve each "change" to a *promise* for new change that includes all existing records
					ResourceRecordSet rr = c.resourceRecordSet();
					return Tools.getRecordSet(rr.name(), rr.type())
							.thenApply(oldrr -> {
								if (Objects.isNull(oldrr)) // this is a new record, just use the generated change
									return c;
								else
									return mergeChangeRRs(c, oldrr);
							});
				})
				.collect(new CompletableFutureListCollector<>())
				// compose a change batch including all changes
				.thenApply(newchanges -> ChangeBatch.builder().changes(newchanges).build());
	}

	private Change mergeChangeRRs(Change c, ResourceRecordSet oldrr) {
		HashSet<ResourceRecord> rrs = new HashSet<>(c.resourceRecordSet().resourceRecords());
		rrs.addAll(oldrr.resourceRecords());
		return c.toBuilder()
				.resourceRecordSet(c.resourceRecordSet().toBuilder().resourceRecords(rrs).build())
				.build();
	}

	private List<Change> getDNSRRUpsertChanges(Instance i) throws NoIpException {
		String ipv4ip = Tools.getIPAddress(i);
		String ipv6ip = Tools.getIPv6Address(i);
		Stream.Builder<ResourceRecordSet> addrs = Stream.builder();
		if (Objects.nonNull(ipv4ip))
			Stream.concat(metadata.getRRSpec().stream(), metadata.getRR4Spec().stream())
					.map(addr -> ResourceRecordSet.builder().type(RRType.A).name(addr)
							.ttl(getTTL()).resourceRecords(ResourceRecord.builder().value(ipv4ip).build()).build())
					.forEach(addrs::add);
		if (Objects.nonNull(ipv6ip))
			Stream.concat(metadata.getRRSpec().stream(), metadata.getRR6Spec().stream())
					.map(addr -> ResourceRecordSet.builder().type(RRType.AAAA).name(addr)
							.ttl(getTTL()).resourceRecords(ResourceRecord.builder().value(ipv6ip).build()).build())
					.forEach(addrs::add);
		return addrs.build()
				.map(rr -> Change.builder().action(ChangeAction.UPSERT).resourceRecordSet(rr).build())
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
					ResourceRecordSet.builder().type(RRType.SRV).name(ent.getKey())
						.ttl(getTTL()).resourceRecords(
								ent.getValue().stream().map(s -> s.getResourceRecord(i)).collect(Collectors.toList())
								).build()
				)
				.map(rr -> Change.builder().action(ChangeAction.UPSERT).resourceRecordSet(rr).build())
				.collect(Collectors.toList());
	}

	public CompletableFuture<ChangeBatch> getRemoveChanges(Instance i) throws NoIpException {
		Stream.Builder<CompletableFuture<List<Change>>> changes = Stream.builder();
		if (useDNSRR()) {
			changes.add(getDNSRR4RemoveChanges(i));
			changes.add(getDNSRR6RemoveChanges(i));
		}
		if (useSRV())
			changes.add(getSRVRemoveChanges(i));
		return changes.build().collect(new CompletableFutureListCollector<>())
				.thenApply(l -> l.stream().flatMap(l2 -> l2.stream()))
				.thenApply(s -> s.collect(Collectors.toList()))
				.thenApply(l -> ChangeBatch.builder().changes(l).build());
	}
	
	private CompletableFuture<List<Change>> getDNSRR4RemoveChanges(Instance i) throws NoIpException {
		String ip = Tools.getIPAddress(i);
		return Stream.concat(metadata.getRRSpec().stream(), metadata.getRR4Spec().stream())
				.map(s -> Tools.getRecordSet(s, RRType.A))
				.collect(new CompletableFutureListCollector<>())
				.thenApply(l -> l.stream()
						.map(rr -> {
							if (Objects.isNull(rr))
								return null;
							if (rr.resourceRecords().size() == 1 && rr.resourceRecords().get(0).value().equals(ip))
								return Change.builder().action(ChangeAction.DELETE).resourceRecordSet(rr).build();
							HashSet<ResourceRecord> rrs = new HashSet<>(rr.resourceRecords());
							if (rrs.removeIf(r -> r.value().equals(ip)))
								return Change.builder().action(ChangeAction.UPSERT)
										.resourceRecordSet(rr.toBuilder().resourceRecords(rrs).build()).build();
							return null;
						})
						.filter(Objects::nonNull)
						.collect(Collectors.toList()));
	}
	
	private CompletableFuture<List<Change>> getDNSRR6RemoveChanges(Instance i) throws NoIpException {
		String ip = Tools.getIPv6Address(i);
		return Stream.concat(metadata.getRRSpec().stream(), metadata.getRR6Spec().stream())
				.map(s -> Tools.getRecordSet(s, RRType.AAAA))
				.collect(new CompletableFutureListCollector<>())
				.thenApply(l -> l.stream()
						.map(rr -> {
							if (Objects.isNull(rr))
								return null;
							if (rr.resourceRecords().size() == 1 && rr.resourceRecords().get(0).value().equals(ip))
								return Change.builder().action(ChangeAction.DELETE).resourceRecordSet(rr).build();
							HashSet<ResourceRecord> rrs = new HashSet<>(rr.resourceRecords());
							if (rrs.removeIf(r -> r.value().equals(ip)))
								return Change.builder().action(ChangeAction.UPSERT)
										.resourceRecordSet(rr.toBuilder().resourceRecords(rrs).build()).build();
							return null;
						})
						.filter(Objects::nonNull)
						.collect(Collectors.toList()));
	}
	
	private CompletableFuture<List<Change>> getSRVRemoveChanges(Instance i) throws NoIpException {
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
				.map(ent -> Tools.getRecordSet(ent.getKey(), RRType.SRV)
						.thenApply(rr -> {
							if (Objects.isNull(rr))
								return null;
							ArrayList<ResourceRecord> newRRs = new ArrayList<>(rr.resourceRecords());
							for (SRVTemplate s : ent.getValue())
								newRRs.remove(s.getResourceRecord(host));
							if (newRRs.isEmpty())
								return Change.builder().action(ChangeAction.DELETE).resourceRecordSet(rr).build();
							return Change.builder().action(ChangeAction.UPSERT)
									.resourceRecordSet(rr.toBuilder().resourceRecords(newRRs).build()).build();
						}))
				.collect(new CompletableFutureListCollector<>())
				.thenApply(l -> l.stream()
						.filter(Objects::nonNull)
						.collect(Collectors.toList()));
	}
	
	public static String json(Object data) {
		try {
			return s_mapper.writeValueAsString(data);
		} catch (JsonProcessingException e) {
			return "Error JSON mapping a value: " + e;
		}
	}
}
