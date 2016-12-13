package net.gftc.aws.route53;

import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.model.Change;
import com.amazonaws.services.route53.model.ChangeAction;
import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeInfo;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsResult;
import com.amazonaws.services.route53.model.GetChangeRequest;
import com.amazonaws.services.route53.model.ListResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.RRType;
import com.amazonaws.services.route53.model.ResourceRecord;
import com.amazonaws.services.route53.model.ResourceRecordSet;

public class Tools {
	private static final long WAIT_PULSE = 250;
	static private AmazonRoute53Client r53 = new AmazonRoute53Client(net.gftc.aws.Tools.getCreds());

	public static void waitFor(ChangeResourceRecordSetsResult reqRes) {
		ChangeInfo ci = reqRes.getChangeInfo();
		while (ci.getStatus().equals("PENDING")) {
			synchronized (ci) {
				try {
					ci.wait(WAIT_PULSE);
				} catch (InterruptedException e) { }
			}
			ci = r53.getChange(new GetChangeRequest(ci.getId())).getChangeInfo();
		}
	}

	public static ResourceRecordSet getRecordSet(String hostname, RRType type) {
		if (!hostname.endsWith("."))
			hostname = hostname + ".";
		final String domainname = hostname;
		ListResourceRecordSetsRequest req = new ListResourceRecordSetsRequest()
				.withHostedZoneId(NotifyRecords.getHostedZoneId())
				.withStartRecordName(hostname)
				.withStartRecordType(type)
				.withMaxItems("1");
		return r53.listResourceRecordSets(req).getResourceRecordSets().stream()
				.filter(rr -> rr.getName().equals(domainname))
				.findAny().orElse(new ResourceRecordSet(domainname, type)
						.withTTL(300L));
	}

	public static ResourceRecordSet removeRecord(ResourceRecordSet recordSet,
			Predicate<ResourceRecord> predicate) {
		ResourceRecordSet rr = recordSet.clone();
		rr.setResourceRecords(
				recordSet.getResourceRecords().stream()
					.filter(predicate.negate())
					.collect(Collectors.toList()));
		return rr;
	}
	
	public static ChangeResourceRecordSetsRequest getAndAddRecord(String hostname, RRType rtype, String value) {
		ResourceRecordSet rr = Tools.getRecordSet(hostname, rtype);
		rr.getResourceRecords().add(new ResourceRecord(value));
		return rrsetToChange(rr);
	}

	public static ChangeResourceRecordSetsRequest getAndRemoveRecord(String hostname, RRType rtype, String value) {
		return rrsetToChange(
				removeRecord(
						getRecordSet(hostname, rtype),
						r -> Objects.equals(r.getValue(), value)));
	}

	private static ChangeResourceRecordSetsRequest rrsetToChange(ResourceRecordSet rrset) {
		return new ChangeResourceRecordSetsRequest(
				NotifyRecords.getHostedZoneId(),
				new ChangeBatch(Stream.of(new Change(ChangeAction.UPSERT, rrset))
						.collect(Collectors.toList())));
	}


}
