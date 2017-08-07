package tech.greenfield.aws.route53;

import static tech.greenfield.aws.Clients.route53;

import java.util.HashSet;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

/**
 * Route53 integration utilities
 * @author odeda
 *
 *     Copyright (C) 2016  GreenfieldTech
 * 
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 * 
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 * 
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
public class Tools {
	private static final long WAIT_PULSE = 250;

	/**
	 * Wait until the specified change request has been applied on Route53 servers
	 * @param reqRes the result of submitting a change request
	 */
	public static void waitFor(ChangeResourceRecordSetsResult reqRes) {
		ChangeInfo ci = reqRes.getChangeInfo();
		while (ci.getStatus().equals("PENDING")) {
			synchronized (ci) {
				try {
					ci.wait(WAIT_PULSE);
				} catch (InterruptedException e) { }
			}
			ci = route53().getChange(new GetChangeRequest(ci.getId())).getChangeInfo();
		}
	}

	/**
	 * Retrieve a single record set with the specified name and type.
	 * This method relies on {@link NotifyRecords#getHostedZoneId()} which
	 * requires setting the environment variable HOSTED_ZONE_ID
	 * @param hostname FQDN of record set to retrieve
	 * @param type RR type of record to retrieve
	 * @return The record set retrieved from Route53 or an empty record set 
	 * 	(with a default 300 seconds TTL)
	 */
	public static ResourceRecordSet getRecordSet(String hostname, RRType type) {
		if (!hostname.endsWith("."))
			hostname = hostname + ".";
		final String domainname = hostname;
		ListResourceRecordSetsRequest req = new ListResourceRecordSetsRequest()
				.withHostedZoneId(NotifyRecords.getHostedZoneId())
				.withStartRecordName(hostname)
				.withStartRecordType(type)
				.withMaxItems("1");
		return route53().listResourceRecordSets(req).getResourceRecordSets().stream()
				.filter(rr -> rr.getName().equals(domainname))
				.findAny().orElse(new ResourceRecordSet(domainname, type)
						.withTTL(300L));
	}

	/**
	 * Remove a set of records from a record set according to the specified
	 * predicate.
	 * @param recordSet Record set to review
	 * @param predicate predicate to test which records <strong>to remove</strong>
	 * @return a copy of the original record set with the matching records removed
	 */
	public static ResourceRecordSet removeRecord(ResourceRecordSet recordSet,
			Predicate<ResourceRecord> predicate) {
		ResourceRecordSet rr = recordSet.clone();
		rr.setResourceRecords(
				recordSet.getResourceRecords().stream()
					.filter(predicate.negate())
					.collect(Collectors.toList()));
		return rr;
	}
	
	/**
	 * Create a Route53 change request that adds the specified value to the specified
	 * existing resource record set
	 * @param hostname FQDN of resource record set to update
	 * @param rtype RR type of resource record set to update
	 * @param value record to add to the resource record set
	 * @return Change request that can be submitted to Route53
	 */
	public static ChangeResourceRecordSetsRequest getAndAddRecord(String hostname, RRType rtype, String value) {
		ResourceRecordSet rr = Tools.getRecordSet(hostname, rtype);
		rr.getResourceRecords().add(new ResourceRecord(value));
		HashSet<ResourceRecord> uniqRRs = new HashSet<>(rr.getResourceRecords());
		rr.setResourceRecords(uniqRRs);
		return rrsetToChange(rr);
	}

	/**
	 * Create a Route53 change request that removes the specified value to the specified
	 * existing resource record set
	 * @param hostname FQDN of resource record set to update
	 * @param rtype RR type of resource record set to update
	 * @param value record to match and remove from the resource record set
	 * @return Change request that can be submitted to Route53
	 */
	public static ChangeResourceRecordSetsRequest getAndRemoveRecord(String hostname, RRType rtype, String value) {
		return rrsetToChange(
				removeRecord(
						getRecordSet(hostname, rtype),
						r -> Objects.equals(r.getValue(), value)));
	}

	/**
	 * Create an UPSERT {@link ChangeResourceRecordSetsRequest} from a resource record set
	 * This method relies on {@link NotifyRecords#getHostedZoneId()} which
	 * requires setting the environment variable HOSTED_ZONE_ID
	 * @param rrset resource record set to "upsert"
	 * @return Change resource record set request to submit to Route53
	 */
	private static ChangeResourceRecordSetsRequest rrsetToChange(ResourceRecordSet rrset) {
		return new ChangeResourceRecordSetsRequest(
				NotifyRecords.getHostedZoneId(),
				new ChangeBatch(Stream.of(
						new Change(
								rrset.getResourceRecords().isEmpty() ? 
										// if the record set is empty, we should delete the record
										ChangeAction.DELETE :
										// otherwise we upsert
										ChangeAction.UPSERT,
								rrset)
							).collect(Collectors.toList()))
					);
	}


}
