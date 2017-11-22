package tech.greenfield.aws.route53;

import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;

/**
 * Main entry point from the AWS Lambda engine, that takes an SNS event
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
public class NotifyRecords implements RequestHandler<SNSEvent, Route53UpdateResponse>{

	private static final long DEFAULT_TTL = 300;

	/**
	 * Main entry point
	 */
	public Route53UpdateResponse handleRequest(SNSEvent input, Context context) {
		try {
			if (Objects.isNull(input)) {
				context.getLogger().log("Invalid SNS input object");
				return error("no SNS event input");
			}
			List<SNSRecord> records = input.getRecords();
			if (Objects.isNull(records)) {
				context.getLogger().log("No SNS events in input");
				return error("no SNS events");
			}
			records.parallelStream()
				.map(e -> EventHandler.create(context, e))
				.forEach(EventHandler::handle);
			context.getLogger().log("Done updating Route53");
			return ok();
		} catch (Throwable t) {
			context.getLogger().log("Unexpected error while updating Route53: " + t);
			return error(t.toString()); 
		}
	}
	
	/* ==- Helper Utilities -== */
	
	private Route53UpdateResponse ok() {
		return new Route53UpdateResponse(true, "OK");
	}

	private Route53UpdateResponse error(String message) {
		return new Route53UpdateResponse(false, "Error: " + message);
	}

	/**
	 * Check if we need to enable debug mode
	 * @return true if debug mode was requested by setting the DEBUG environment variable
	 */
	public static boolean isDebug() {
		String debug = System.getenv("DEBUG");
		return Objects.nonNull(debug) && !debug.isEmpty();
	}
	
	public static long getTTL() {
		String ttl = System.getenv("TTL");
		if (Objects.nonNull(ttl))
			try {
				return Long.parseLong(ttl);
			} catch (NumberFormatException e) {
				return DEFAULT_TTL;
			}
		return DEFAULT_TTL;
	}
	
	public static boolean isPrivate() {
		String privateIP = System.getenv("DNSRR_PRIVATE");
		return Objects.nonNull(privateIP) && !privateIP.isEmpty();
	}

	/**
	 * Retrieve all environment values whose keys start with the specified prefix
	 * @param prefix environment variable name prefix
	 * @return List of environment values
	 */
	private static List<String> getEnvByPrefix(String prefix) {
		return System.getenv().entrySet().stream().filter(e -> e.getKey().startsWith(prefix))
				.map(e -> e.getValue()).collect(Collectors.toList());
	}
	
	/**
	 * Retrieve all SRV_RECORD configurations
	 * @return list of SRV_RECORD configurations
	 */
	public static List<String> getSRVConfiguration() {
		return getEnvByPrefix("SRV_RECORD");
	}
	
	/**
	 * Retrieve all DNSRR_RECORD configurations
	 * @return list of DNSRR_RECORD configurations
	 */
	public static List<String> getDNSRRConfiguration() {
		return getEnvByPrefix("DNSRR_RECORD");
	}
	
	/**
	 * Check if SRV record update was requested by specifying the SRV_RECORD environemtn variable
	 * This setting is mandatory if DNSRR_RECORD is not set
	 * @return true if SRV record update is requested
	 */
	public static boolean useSRV() {
		return !getSRVConfiguration().isEmpty();
	}
	
	/**
	 * Format and retrieve the SRV record and FQDN that need to be updated
	 * This setting is mandatory if DNSRR_RECORD is not set
	 * @param hostname FQDN for which to generate an SRV record using the schema
	 * 	specified by the SRV_RECORD environment variable
	 * @return A pair where the key is the FQDN to update and the value is the
	 * 	record set to update (add or remove a record)
	 */
	public static Map<String,String> getSRVEntries(String hostname) {
		return getSRVConfiguration().stream().map(var -> {
			if (Objects.isNull(var) || var.isEmpty())
				throw new UnsupportedOperationException(
						"Cannot construct SRV record without SRV_RECORD environment variable");
			String[] parts = var.split(":");
			if (parts.length != 4)
				throw new UnsupportedOperationException("Invalid SRV_RECORD format - " +
						"must conform to format '<priority>:<weight>:<port>:<name>'");
			return new AbstractMap.SimpleEntry<String,String>(
					parts[3],
					Stream.of(parts[0], parts[1],parts[2],hostname)
						.collect(Collectors.joining(" ")));
		}).collect(Collectors.toMap(SimpleEntry::getKey, SimpleEntry::getValue));
	}
	
	/**
	 * Check if a DNS round-robin record update was requested by specifing the 
	 * 	DNSRR_RECORD environment variable
	 * This setting is mandatory if SRV_RECORD is not set
	 * @return true if DNS round-robin record update is requested
	 */
	public static boolean useDNSRR() {
		return !getDNSRRConfiguration().isEmpty();
	}

	/**
	 * Get the Route53 hosted zone ID to update, as specified in the HOSTED_ZONE_ID environment variable.
	 * This setting is mandatory.
	 * @return the Route53 hosted zone ID to update
	 */
	public static String getHostedZoneId() {
		String var = System.getenv("HOSTED_ZONE_ID");
		if (Objects.isNull(var) || var.isEmpty())
			throw new UnsupportedOperationException(
					"Please specify Route53 zone ID using HOSTED_ZONE_ID environment variable");
		return var;
	}
}
