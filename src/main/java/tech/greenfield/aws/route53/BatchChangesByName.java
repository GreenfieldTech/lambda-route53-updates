package tech.greenfield.aws.route53;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import software.amazon.awssdk.services.route53.model.Change;
import software.amazon.awssdk.services.route53.model.ChangeAction;
import software.amazon.awssdk.services.route53.model.ResourceRecordSet;

public class BatchChangesByName implements Collector<Change, List<ResourceRecordSet>, List<Change>> {

	@Override
	public Supplier<List<ResourceRecordSet>> supplier() {
		return LinkedList::new;
	}

	@Override
	public BiConsumer<List<ResourceRecordSet>, Change> accumulator() {
		return (list, change) -> { list.add(change.resourceRecordSet()); };
	}

	@Override
	public BinaryOperator<List<ResourceRecordSet>> combiner() {
		return (a,b) -> { a.addAll(b); return a; };
	}

	@Override
	public Function<List<ResourceRecordSet>, List<Change>> finisher() {
		return list -> list.stream()
				.collect(Collectors.groupingBy(this::groupingKey))
				.values().stream()
				.filter(l -> !l.isEmpty())
				.map(this::mergeDupResourceRecordSets)
				.map(rrs -> Change.builder().action(ChangeAction.UPSERT).resourceRecordSet(rrs).build())
				.collect(Collectors.toList());
	}

	@Override
	public Set<Characteristics> characteristics() {
		return Collections.unmodifiableSet(EnumSet.of(Collector.Characteristics.UNORDERED));
	}
	
	private String groupingKey(ResourceRecordSet rr) {
		return rr.name() + ":" + rr.type();
	}
	
	private ResourceRecordSet mergeDupResourceRecordSets(List<ResourceRecordSet> lRRSets) {
		return lRRSets.get(0).toBuilder()
				.resourceRecords(lRRSets.stream().flatMap(rrs -> rrs.resourceRecords().stream())
					.filter(rr -> Objects.nonNull(rr.value()))
					.distinct()
					.collect(Collectors.toList())).build();
	}
}
