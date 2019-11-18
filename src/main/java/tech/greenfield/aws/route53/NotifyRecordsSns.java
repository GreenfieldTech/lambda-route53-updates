package tech.greenfield.aws.route53;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

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
public class NotifyRecordsSns extends BaseNotifyRecords implements RequestHandler<SNSEvent, Route53UpdateResponse>{

	/**
	 * Main entry point
	 */
	public Route53UpdateResponse handleRequest(SNSEvent input, Context context) {
		setupLogger(context);
		if (Objects.isNull(input)) {
			logger.warning("Invalid SNS input object");
			return Response.error("no SNS event input");
		}
		List<SNSRecord> records = input.getRecords();
		if (Objects.isNull(records)) {
			logger.warning("No SNS events in input");
			return Response.error("no SNS events");
		}
		CompletableFuture<Void> res = CompletableFuture.completedFuture(null);
		for (SNSRecord r : records) {
			res = res.thenCompose(v -> {
				try {
					return new Route53Message(r).createEventHandler(context).handle();
				} catch (ParsingException e) {
					Tools.logException(logger, "Error parsing incoming message", e);
					logger.severe("Original message: " + r.getSNS().getMessage());
					return CompletableFuture.completedFuture(null);
				}
			})
			.exceptionally(t -> {
				if (Objects.nonNull(t))
					Tools.logException(logger, "Unexpected error during handling message", t);
				return null;
			});
		}
		try {
			res.get();
			logger.info("Done updating Route53");
			return Response.ok();
		} catch (InterruptedException | ExecutionException e) {
			logger.severe("Unexpected exception in SNS request handler: " + e);
			return Response.error(e.getMessage());
		}
	}
	
}
