package tech.greenfield.aws.route53;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.amazonaws.services.route53.model.*;

public class BatchChangesByName implements Collector<Change, List<ResourceRecordSet>, List<Change>> {

	@Override
	public Supplier<List<ResourceRecordSet>> supplier() {
		return LinkedList::new;
	}

	@Override
	public BiConsumer<List<ResourceRecordSet>, Change> accumulator() {
		return (list, change) -> { list.add(change.getResourceRecordSet()); };
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
				.map(rrs -> new Change(ChangeAction.UPSERT, rrs))
				.collect(Collectors.toList());
	}

	@Override
	public Set<Characteristics> characteristics() {
		return Collections.unmodifiableSet(EnumSet.of(Collector.Characteristics.UNORDERED));
	}
	
	private String groupingKey(ResourceRecordSet rr) {
		return rr.getName() + ":" + rr.getType();
	}
	
	private ResourceRecordSet mergeDupResourceRecordSets(List<ResourceRecordSet> lRRSets) {
		ResourceRecordSet out = lRRSets.get(0).clone();
		out.setResourceRecords(lRRSets.stream().flatMap(rrs -> rrs.getResourceRecords().stream())
					.filter(rr -> Objects.nonNull(rr.getValue()))
					.distinct()
					.collect(Collectors.toList()));
		return out;
	}
}
