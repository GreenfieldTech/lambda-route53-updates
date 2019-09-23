package tech.greenfield.aws.route53;

import java.util.*;
import java.util.function.*;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.amazonaws.services.route53.model.Change;
import com.amazonaws.services.route53.model.ChangeAction;
import com.amazonaws.services.route53.model.ResourceRecordSet;

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
				.collect(Collectors.groupingBy(ResourceRecordSet::getName))
				.values().stream().filter(l -> !l.isEmpty()).map(l -> {
					ResourceRecordSet rr = l.get(0);
					rr.setResourceRecords(l.stream().flatMap(rrs -> rrs.getResourceRecords().stream())
							.collect(Collectors.toList()));
					return new Change(ChangeAction.UPSERT, rr);
				}).collect(Collectors.toList());
	}

	@Override
	public Set<Characteristics> characteristics() {
		return Collections.unmodifiableSet(EnumSet.of(Collector.Characteristics.UNORDERED));
	}

}
