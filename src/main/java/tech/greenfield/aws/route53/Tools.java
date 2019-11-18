package tech.greenfield.aws.route53;

import static tech.greenfield.aws.Clients.autoscaling;
import static tech.greenfield.aws.Clients.ec2;
import static tech.greenfield.aws.Clients.route53;

import java.io.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.route53.model.*;

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
	private static final long WAIT_PULSE = 1500;
	
	private static Logger log = Logger.getLogger(Tools.class.getName());

	/**
	 * Wait until the specified change request has been applied on Route53 servers
	 * @param reqRes the result of submitting a change request
	 */
	public static CompletableFuture<Void> waitFor(ChangeInfo ci) {
		if (ci.status() != ChangeStatus.PENDING)
			return CompletableFuture.completedFuture(null);
		log.fine("Still waiting for " + ci.id());
		return CompletableFuture.runAsync(() -> {
			synchronized (ci) {
				try {
					ci.wait(WAIT_PULSE);
				} catch (InterruptedException e) { }
			}})
				.thenCompose(v -> route53().getChange(b -> b.id(ci.id())))
				.thenCompose(r -> waitFor(r.changeInfo())); 
	}

	/**
	 * Retrieve a single record set with the specified name and type.
	 * This method relies on {@link Response#getHostedZoneId()} which
	 * requires setting the environment variable HOSTED_ZONE_ID
	 * @param hostname FQDN of record set to retrieve
	 * @param type RR type of record to retrieve
	 * @return The record set retrieved from Route53 or an empty record set 
	 */
	public static CompletableFuture<ResourceRecordSet> getRecordSet(String hostname, RRType type) {
		if (!hostname.endsWith("."))
			hostname = hostname + ".";
		final String domainname = hostname;
		return route53().listResourceRecordSets(b -> b
				.hostedZoneId(Route53Message.getHostedZoneId())
				.startRecordName(domainname)
				.startRecordType(type)
				.maxItems("1"))
				.whenComplete((res,t) -> {
					log.fine("Got recordset for " + domainname + ":" + type +" - " + res);
				})
				.thenApply(res -> res.resourceRecordSets().stream()
				.filter(rr -> rr.name().equals(domainname))
				.filter(rr -> rr.type().equals(type))
				.findAny().orElse(null));
	}

	static CompletableFuture<List<software.amazon.awssdk.services.autoscaling.model.Instance>> getASGInstances(String asgName) {
		return autoscaling()
				.describeAutoScalingGroups(b -> b.autoScalingGroupNames(asgName))
				.thenApply(res -> res.autoScalingGroups().get(0).instances());
	}
	
	static CompletableFuture<Instance> asInstanceToEC2(software.amazon.awssdk.services.autoscaling.model.Instance instance) {
		return ec2().describeInstances(b -> b.instanceIds(instance.instanceId()))
				.thenApply(res -> res.reservations().stream()
						.flatMap(r -> r.instances().stream()).findFirst().orElse(null));
	}

	public static String getHostAddress(Instance i) throws NoIpException {
		String addr = Route53Message.isPrivate() ? i.privateDnsName() : i.publicDnsName();
		if (Objects.nonNull(addr) && !addr.isEmpty())
			return addr;
		return getIPAddress(i);
	}

	public static String getIPAddress(Instance i) throws NoIpException {
		try {
			return Objects.requireNonNull(Route53Message.isPrivate() ? i.privateIpAddress() : i.publicIpAddress());
		} catch (NullPointerException e) {
			throw new NoIpException("Cowardly refusing to add an instance " + i + " with no IP address");
		}
	}
	
	public static String getIPv6Address(Instance i) {
		return i.networkInterfaces().stream().flatMap(in -> in.ipv6Addresses().stream()).findFirst()
				.map(addr -> addr.ipv6Address()).orElse(null);
	}

	public static void logException(Logger logger, String message, Throwable t) {
		StringWriter sw = new StringWriter();
		t.printStackTrace(new PrintWriter(sw));
		logger.severe(message + ": " + t.toString() + "\n" + sw.toString());
	}
	
	public static String getVersion() {
		try (BufferedReader version = new BufferedReader(new InputStreamReader(Tools.class.getClassLoader().getResourceAsStream("version.txt")))) {
			return version.readLine().trim();
		} catch (IOException e) {
			Logger.getAnonymousLogger().severe("Version file was not found");
		}
		return "unknown";
	}

	
	public static Runnable delay(long delay) {
		return () -> {
			try {
				Thread.sleep(delay);
			} catch (InterruptedException e) {
			}
		};
	}

}
