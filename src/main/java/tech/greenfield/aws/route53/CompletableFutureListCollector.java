package tech.greenfield.aws.route53;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.*;
import java.util.stream.Collector;

class CompletableFutureListCollector<T> implements
		Collector<CompletableFuture<T>, ArrayList<CompletableFuture<T>>, CompletableFuture<List<T>>> {
	@Override
	public Supplier<ArrayList<CompletableFuture<T>>> supplier() {
		return ArrayList::new;
	}

	@Override
	public BiConsumer<ArrayList<CompletableFuture<T>>, CompletableFuture<T>> accumulator() {
		return ArrayList::add;
	}

	@Override
	public BinaryOperator<ArrayList<CompletableFuture<T>>> combiner() {
		return (a,b) -> { a.addAll(b); return a; };
	}

	@Override
	public Function<ArrayList<CompletableFuture<T>>, CompletableFuture<List<T>>> finisher() {
		return a -> {
			ArrayList<T> res = new ArrayList<>(a.size());
			a.forEach(cf -> cf.thenAccept(res::add));
			return CompletableFuture.allOf(a.toArray(new CompletableFuture[a.size()])).thenApply(v -> res);
		};
	}

	@Override
	public Set<Characteristics> characteristics() {
		return Collections.emptySet();
	}
}