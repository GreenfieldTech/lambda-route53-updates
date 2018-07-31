package tech.greenfield.aws;

import static tech.greenfield.aws.Clients.ec2;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.model.*;

/**
 * AWS integration utilities.
 * 
 * Specifically this currently implements credential resolving with support for
 * specifying an AWS profile to be loaded from an AWS credentials file (for testing)
 * using the AWS_PROFILE environment variable
 * 
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

	/**
	 * Retrieve the credentials to access AWS using the standard AWS credentials
	 * resolution process, with the addition of the option to select a non-default
	 * AWS CLI profile from the standard AWS credentials file (used for local testing)
	 * by specifying the AWS_PROFILE environment variable
	 * @return AWS credentials to use for accessing the AWS API
	 */
	public static AWSCredentials getCreds() {
		String forceProfile = System.getenv("AWS_PROFILE");
		if (Objects.nonNull(forceProfile) && !forceProfile.isEmpty())
			return new ProfileCredentialsProvider(forceProfile).getCredentials();
		return DefaultAWSCredentialsProviderChain.getInstance().getCredentials();
	}
	
	public static boolean isPrivate() {
		String privateIP = System.getenv("DNSRR_PRIVATE");
		return Objects.nonNull(privateIP) && !privateIP.isEmpty();
	}
	
	public static SimpleEntry<String,List<InetAddress>> getEc2InstancesFromAsgInstances(List<com.amazonaws.services.autoscaling.model.Instance> instances) {
		List<InetAddress> instancesToUpdate = new ArrayList<>();
		String host = null;
		for(com.amazonaws.services.autoscaling.model.Instance ins : instances) {
			if(!ins.getHealthStatus().equals("Healthy"))
				continue;
			DescribeInstancesRequest requestEc2 = new DescribeInstancesRequest().withInstanceIds(ins.getInstanceId());
			if(ec2().describeInstances(requestEc2).getReservations().isEmpty() || ec2().describeInstances(requestEc2).getReservations().get(0).getInstances().isEmpty())
				continue;
			com.amazonaws.services.ec2.model.Instance ec2Instance = ec2().describeInstances(requestEc2).getReservations().get(0).getInstances().get(0);
			try {
				String ipv4 = getIPAddress(ec2Instance);
				if (Objects.nonNull(ipv4))
					instancesToUpdate.add(InetAddress.getByName(ipv4));
				String ipv6 = getIPv6Address(ec2Instance);
				if (Objects.nonNull(ipv6))
					instancesToUpdate.add(InetAddress.getByName(ipv6));
			} catch (UnknownHostException e) {} // shouldn't happen because we provide IPs and Java promises not to DNS
			if(instancesToUpdate.size()<2)
				host = getHostAddress(ec2Instance);
		}
		return new AbstractMap.SimpleEntry<String,List<InetAddress>>(host, instancesToUpdate);
	}

	public static String getHostAddress(Instance i) {
		String addr = isPrivate() ? i.getPrivateDnsName() : i.getPublicDnsName();
		if (Objects.nonNull(addr) && !addr.isEmpty())
			return addr;
		return getIPAddress(i);
	}

	public static String getIPAddress(Instance i) {
		return isPrivate() ? i.getPrivateIpAddress() : i.getPublicIpAddress();
	}

	public static String getIPv6Address(Instance i) {
		return i.getNetworkInterfaces().stream().flatMap(in -> in.getIpv6Addresses().stream())
				.findFirst().map(InstanceIpv6Address::getIpv6Address).orElse(null);
	}

}
