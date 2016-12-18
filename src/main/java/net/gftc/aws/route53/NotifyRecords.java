package net.gftc.aws.route53;

import java.util.AbstractMap;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;
import com.amazonaws.services.sqs.model.UnsupportedOperationException;

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
public class NotifyRecords implements RequestHandler<SNSEvent, String>{

	/**
	 * Main entry point
	 */
	public String handleRequest(SNSEvent input, Context context) {
		if (Objects.isNull(input)) {
			context.getLogger().log("Invalid SNS input object");
			return "Error: no SNS event input";
		}
		List<SNSRecord> records = input.getRecords();
		if (Objects.isNull(records)) {
			context.getLogger().log("No SNS events in input");
			return "Error: no SNS events";
		}
		records.parallelStream()
			.map(e -> EventHandler.create(context, e))
			.forEach(EventHandler::handle);
		return "OK";
	}
	
	/* ==- Helper Utilities -== */
	
	/**
	 * Check if we need to enable debug mode
	 * @return true if debug mode was requested by setting the DEBUG environment variable
	 */
	public static boolean isDebug() {
		String debug = System.getenv("DEBUG");
		return Objects.nonNull(debug) && !debug.isEmpty();
	}
	
	/**
	 * Check if SRV record update was requested by specifying the SRV_RECORD environemtn variable
	 * This setting is mandatory if DNSRR_RECORD is not set
	 * @return true if SRV record update is requested
	 */
	public static boolean useSRV() {
		String var = System.getenv("SRV_RECORD");
		return Objects.nonNull(var) && !var.isEmpty();
	}
	
	/**
	 * Format and retrieve the SRV record and FQDN that need to be updated
	 * This setting is mandatory if DNSRR_RECORD is not set
	 * @param hostname FQDN for which to generate an SRV record using the schema
	 * 	specified by the SRV_RECORD environment variable
	 * @return A pair where the key is the FQDN to update and the value is the
	 * 	record set to update (add or remove a record)
	 */
	public static SimpleEntry<String,String> getSRV(String hostname) {
		String var = System.getenv("SRV_RECORD");
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
	}
	
	/**
	 * Check if a DNS round-robin record update was requested by specifing the 
	 * 	DNSRR_RECORD environment variable
	 * This setting is mandatory if SRV_RECORD is not set
	 * @return true if DNS round-robin record update is requested
	 */
	public static boolean useDNSRR() {
		String var = System.getenv("DNSRR_RECORD");
		return Objects.nonNull(var) && !var.isEmpty();
	}
	
	/**
	 * Return the FQDN for a DNS round-robin record that needs to be updated
	 * This setting is mandatory if SRV_RECORD is not set
	 * @return the FQDN of the DNS round robin record set to update (add or remove a record)
	 */
	public static String getDNSRR() {
		String var = System.getenv("DNSRR_RECORD");
		if (Objects.isNull(var) || var.isEmpty())
			throw new UnsupportedOperationException(
					"Cannot construct DNS-RR record without DNSRR_RECORD environment variable");
		return var;
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
