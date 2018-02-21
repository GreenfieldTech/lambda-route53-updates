package tech.greenfield.aws.route53;

import java.util.List;
import java.util.Objects;

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
public class NotifyRecordsSns implements RequestHandler<SNSEvent, Route53UpdateResponse>{

	public NotifyRecordsSns() {
	}

	/**
	 * Main entry point
	 */
	public Route53UpdateResponse handleRequest(SNSEvent input, Context context) {
		try {
			if (Objects.isNull(input)) {
				context.getLogger().log("Invalid SNS input object");
				return Response.error("no SNS event input");
			}
			List<SNSRecord> records = input.getRecords();
			if (Objects.isNull(records)) {
				context.getLogger().log("No SNS events in input");
				return Response.error("no SNS events");
			}
			records.parallelStream()
				.map(e -> EventHandler.create(context, new Route53Message(e)))
				.forEach(EventHandler::handle);
			context.getLogger().log("Done updating Route53");
			return Response.ok();
		} catch (Throwable t) {
			context.getLogger().log("Unexpected error while updating Route53: " + t);
			return Response.error(t.toString()); 
		}
	}
	
	/* ==- Helper Utilities -== */

}
