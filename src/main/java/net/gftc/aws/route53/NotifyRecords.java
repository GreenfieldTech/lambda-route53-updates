package net.gftc.aws.route53;

import java.util.List;
import java.util.Objects;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.lambda.runtime.events.SNSEvent.SNSRecord;

public class NotifyRecords implements RequestHandler<SNSEvent, String>{
	
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
			.map(e -> new EventHandler(context, e))
			.forEach(EventHandler::handle);
		return "OK";
	}
	
}
