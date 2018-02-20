package tech.greenfield.aws.route53;

import static tech.greenfield.aws.Clients.route53;

import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Logger;
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
	private final static Logger logger = Logger.getLogger(Tools.class.getName());

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
	 * @param ttl TTL in seconds to use if generating an empty record
	 * @return The record set retrieved from Route53 or an empty record set 
	 */
	public static ResourceRecordSet getRecordSet(String hostname, RRType type, long ttl) {
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
						.withTTL(ttl));
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
	 * @param ttl TTL in seconds to use when creating a new record 
	 * @param value record to add to the resource record set
	 * @return Change request that can be submitted to Route53
	 */
	public static ChangeResourceRecordSetsRequest getAndAddRecord(Stream<Map.Entry<String, String>> mappings, RRType rtype, long ttl) {
		return rrsetsToChange(mappings.map(record -> {
			ResourceRecordSet rr = Tools.getRecordSet(record.getKey(), rtype, ttl);
			ResourceRecordSet origrr = rr.clone();
			rr.getResourceRecords().add(new ResourceRecord(record.getValue()));
			HashSet<ResourceRecord> uniqRRs = new HashSet<>(rr.getResourceRecords());
			rr.setResourceRecords(uniqRRs);
			return new ResourceRecordSetChange(origrr, rr);
		}));
	}
	
	public static ChangeResourceRecordSetsRequest createRecordSet(Stream<Map.Entry<String, List<String>>> mappings, RRType rtype, long ttl) {
		List<Change> changes = new ArrayList<Change>();
		mappings.forEach(entry -> {
			changes.add(new Change(ChangeAction.DELETE, getRecordSet(entry.getKey(), rtype, ttl)));
			logger.info("Name to update: " + entry.getKey());
			logger.info("Existing instances to update: " + Arrays.toString(entry.getValue().toArray()));
			ResourceRecordSet resourceRecordSet = new ResourceRecordSet(entry.getKey(), rtype);
			resourceRecordSet.setTTL(ttl);
			List<ResourceRecord> resourceRecords = new ArrayList<>();
			entry.getValue().forEach(ip -> {
				resourceRecords.add(new ResourceRecord(ip)); 
			});
			resourceRecordSet.setResourceRecords(resourceRecords);
			changes.add(new Change(ChangeAction.CREATE, resourceRecordSet));
//			new ResourceRecordSetChange(getRecordSet(entry.getKey(), rtype, ttl), resourceRecordSet);
		});
		return new ChangeResourceRecordSetsRequest(NotifyRecords.getHostedZoneId(), new ChangeBatch(changes));	
	}

	/**
	 * Create a Route53 change request that removes the specified value to the specified
	 * existing resource record set
	 * @param hostnames FQDNs of resource record set to update
	 * @param rtype RR type of resource record set to update
	 * @param ttl TTL in seconds to use when creating a new record
	 * @param value record to match and remove from the resource record set
	 * @return Change request that can be submitted to Route53
	 */
	public static ChangeResourceRecordSetsRequest getAndRemoveRecord(Stream<Map.Entry<String, String>> mappings, RRType rtype, long ttl) {
		ChangeResourceRecordSetsRequest request = rrsetsToChange(mappings.map(record -> {
			ResourceRecordSet origRecord = getRecordSet(record.getKey(), rtype, ttl);
			ResourceRecordSet update = removeRecord(origRecord, r -> Objects.equals(r.getValue(), record.getValue()));
			return new ResourceRecordSetChange(origRecord, update);
		}));
		return request;
	}

	/**
	 * Create an UPSERT {@link ChangeResourceRecordSetsRequest} from a resource record set
	 * This method relies on {@link NotifyRecords#getHostedZoneId()} which
	 * requires setting the environment variable HOSTED_ZONE_ID
	 * @param rrsets resource record set to "upsert"
	 * @return Change resource record set request to submit to Route53
	 */
	private static ChangeResourceRecordSetsRequest rrsetsToChange(Stream<ResourceRecordSetChange> rrsets) {
		return new ChangeResourceRecordSetsRequest(NotifyRecords.getHostedZoneId(),
				new ChangeBatch(rrsets.map(rr -> rr.removedAll() ? 
						new Change(ChangeAction.DELETE, rr.oldRRS()) : 
							new Change(ChangeAction.UPSERT, rr.newRRS())).collect(Collectors.toList())));
	}
	
	private static class ResourceRecordSetChange {
		private ResourceRecordSet oldset;
		private ResourceRecordSet newset;

		public ResourceRecordSetChange(ResourceRecordSet oldset, ResourceRecordSet newset) {
			this.oldset = oldset;
			this.newset = newset;
		}

		public ResourceRecordSet newRRS() {
			return newset;
		}

		public ResourceRecordSet oldRRS() {
			return oldset;
		}

		public boolean removedAll() {
			return newset.getResourceRecords().isEmpty();
		}
	}

}
