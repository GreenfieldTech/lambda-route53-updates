package tech.greenfield.aws.route53;

import static tech.greenfield.aws.Clients.autoscaling;
import static tech.greenfield.aws.Clients.ec2;
import static tech.greenfield.aws.Clients.route53;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import com.amazonaws.services.autoscaling.model.DescribeAutoScalingGroupsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.route53.model.*;

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
	 * This method relies on {@link Response#getHostedZoneId()} which
	 * requires setting the environment variable HOSTED_ZONE_ID
	 * @param hostname FQDN of record set to retrieve
	 * @param type RR type of record to retrieve
	 * @return The record set retrieved from Route53 or an empty record set 
	 */
	public static ResourceRecordSet getRecordSet(String hostname, String type) {
		if (!hostname.endsWith("."))
			hostname = hostname + ".";
		final String domainname = hostname;
		ListResourceRecordSetsRequest req = new ListResourceRecordSetsRequest()
				.withHostedZoneId(Route53Message.getHostedZoneId())
				.withStartRecordName(hostname)
				.withStartRecordType(type)
				.withMaxItems("1");
		ListResourceRecordSetsResult res = route53().listResourceRecordSets(req);
		if (Route53Message.isDebug()) 
			System.err.println("Got recordset for " + hostname + ":" + type +" - " + res);
		return res.getResourceRecordSets().stream()
				.filter(rr -> rr.getName().equals(domainname))
				.filter(rr -> rr.getType().equals(type))
				.findAny().orElse(null);
	}

	static List<com.amazonaws.services.autoscaling.model.Instance> getASGInstances(String asgName) {
		return autoscaling()
				.describeAutoScalingGroups(new DescribeAutoScalingGroupsRequest().withAutoScalingGroupNames(asgName))
				.getAutoScalingGroups().get(0).getInstances();
	}
	
	static com.amazonaws.services.ec2.model.Instance asInstanceToEC2(
			com.amazonaws.services.autoscaling.model.Instance instance) {
		return ec2().describeInstances(new DescribeInstancesRequest().withInstanceIds(instance.getInstanceId()))
				.getReservations().stream().flatMap(r -> r.getInstances().stream()).findFirst().orElse(null);
	}

	public static String getHostAddress(com.amazonaws.services.ec2.model.Instance i) throws NoIpException {
		String addr = Route53Message.isPrivate() ? i.getPrivateDnsName() : i.getPublicDnsName();
		if (Objects.nonNull(addr) && !addr.isEmpty())
			return addr;
		return getIPAddress(i);
	}

	public static String getIPAddress(com.amazonaws.services.ec2.model.Instance i) throws NoIpException {
		try {
			return Objects.requireNonNull(Route53Message.isPrivate() ? i.getPrivateIpAddress() : i.getPublicIpAddress());
		} catch (NullPointerException e) {
			throw new NoIpException("Cowardly refusing to add an instance " + i + " with no IP address");
		}
	}
	
	public static String getIPv6Address(com.amazonaws.services.ec2.model.Instance i) {
		return i.getNetworkInterfaces().stream().flatMap(in -> in.getIpv6Addresses().stream()).findFirst()
				.map(addr -> addr.getIpv6Address()).orElse(null);
	}

	public static void logException(Logger logger, String message, Throwable t) {
		StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		logger.severe(message + ": " + t.toString() + "\n" + sw.toString());
	}

}
