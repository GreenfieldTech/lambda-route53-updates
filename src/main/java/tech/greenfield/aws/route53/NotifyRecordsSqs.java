package tech.greenfield.aws.route53;

import java.io.*;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;


public class NotifyRecordsSqs extends BaseNotifyRecords implements RequestHandler<SNSEvent, Route53UpdateResponse>{

	private static String queueUrl = null;
	
	@Override
	public Route53UpdateResponse handleRequest(SNSEvent input, Context context) {
		setupLogger(context);
		logger.info("Handling sqs request");
		try {
			List<Message> messages = new ArrayList<>();
			while(true) {
				messages = getMessages();
				if(Route53Message.isDebug())
					logger.info("Handling " + messages.size() + " messages from queue.");
				if(!messages.isEmpty())
					break;
				Thread.sleep(300);
			}
			for (Message message : messages) {
				try {
					Route53Message sqsMessage = new Route53Message(message);
					handleMessage(sqsMessage, context);
				} catch (ParsingException e) {
					Tools.logException(logger, "Failed to parse notification",e);
					logger.severe("Original message: " + message.getBody());
				} 
				deleteMessage(message.getReceiptHandle());
				if(Route53Message.isDebug())
					logger.info("Deleted message");
			}
		} catch (InterruptedException | IOException e) {
			Tools.logException(logger, "Couldn't get/handle sqs messages", e);
			return Response.error(e.getMessage()); 
		}
		return Response.ok();
	}
	
	public Route53UpdateResponse handleMessage(Route53Message input, Context context) {
		try {
			input.createEventHandler(context).handle();
			context.getLogger().log("Done updating Route53");
			return Response.ok();
		} catch (Throwable t) {
			Tools.logException(logger, "Unexpected error while updating Route53", t);
			return Response.error(t.toString()); 
		}
	}
	
	public List<Message> getMessages() throws IOException {
		return AmazonSQSClientBuilder.defaultClient().receiveMessage(new ReceiveMessageRequest(getQueueUrl()).withMaxNumberOfMessages(10)).getMessages();
	}

	public DeleteMessageResult deleteMessage(String receipt) throws IOException {
		return AmazonSQSClientBuilder.defaultClient().deleteMessage(new DeleteMessageRequest(getQueueUrl(), receipt));
	}

	private String getQueueUrl() throws IOException {
		try {
			if(Objects.isNull(queueUrl)) {
				logger.entering(this.getClass().getName(), "getQueueUrl");
				if(Objects.nonNull(System.getenv("QUEUE_URL")))
					return System.getenv("QUEUE_URL");
				queueUrl = AmazonEC2ClientBuilder.defaultClient()
						.describeTags(new DescribeTagsRequest()
								.withFilters(new Filter("resource-id", Arrays.asList(getInstanceId()))))
						.getTags().stream().filter(tag -> tag.getKey().equals("QueueUrl")).findFirst()
						.orElseThrow(() -> new IOException("Queue URL tag missing")).getValue();
			}
			return queueUrl;
		} finally {
			logger.exiting(this.getClass().getName(), "getQueueUrl");
		}
	}

	private String getInstanceId() throws IOException {
		try {
			logger.entering(this.getClass().getName(), "getInstanceId");
			while(true) {
				try {
					return getResult(new URL("http://169.254.169.254/latest/meta-data/instance-id").getContent());
				} catch (MalformedURLException e) {
					throw new IOException(e);
				} catch (ConnectException e) {
					logger.warning("Retrying getInstanceId because of: " + e.getMessage());
				}
			}
		} finally {
			logger.exiting(this.getClass().getName(), "getInstanceId");
		}
	}
	
	private String getResult(Object obj) {
		if(obj instanceof InputStream) {
			try(BufferedReader br = new BufferedReader(new InputStreamReader((InputStream) obj, "UTF-8"))) {
				return br.lines().collect(Collectors.joining());
			} catch (UnsupportedEncodingException e) {
				Tools.logException(logger, "Unexpected encoding error", e);
			} catch (IOException e1) {
				Tools.logException(logger, "Unexpected IO error reading response", e1);
			}
		}
		return obj.toString();
	}

}
