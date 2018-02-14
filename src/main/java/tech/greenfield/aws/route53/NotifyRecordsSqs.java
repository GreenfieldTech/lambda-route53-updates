package tech.greenfield.aws.route53;

import java.io.*;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;


public class NotifyRecordsSqs extends NotifyRecords{

	private final static Logger logger = Logger.getLogger(NotifyRecordsSqs.class.getName());
	private static String queueUrl = null;
	private SqsMessage sqsMessage;
	
	public NotifyRecordsSqs() {

	}

	@Override
	public Route53UpdateResponse handleRequest(SNSEvent input, Context context) {
		logger.info("Handling sqs request");
		try {
			List<Message> messages = new ArrayList<>();
			while(true) {
				messages = getMessages();
				if(!messages.isEmpty())
					break;
				Thread.sleep(50);
			}
			for (Message message : messages) {
				sqsMessage = new SqsMessage(message); 
				logger.info("Handling message: " + sqsMessage.getBody());
				JSONObject body = new JSONObject(sqsMessage.getBody());
				deleteMessage(sqsMessage.getMessage());
				logger.info("Deleted message: " + body);
			}
		} catch (JSONException | InterruptedException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ok();
	}
	
	public SqsMessage getSqsMessage() {
		return sqsMessage;
	}
	
	public List<Message> getMessages() throws IOException {
		return AmazonSQSClientBuilder.defaultClient().receiveMessage(new ReceiveMessageRequest(getQueueUrl())).getMessages();
	}

	public DeleteMessageResult deleteMessage(Message message) throws IOException {
		return AmazonSQSClientBuilder.defaultClient().deleteMessage(new DeleteMessageRequest(getQueueUrl(), message.getReceiptHandle()));
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
					e.printStackTrace();
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
				e.printStackTrace();
			} catch (IOException e1) {
				e1.printStackTrace();
			}
		}
		return obj.toString();
	}

	private String getIP() throws IOException {
		if(Objects.nonNull(System.getenv("MY_IP")))
			return System.getenv("MY_IP");
		try {
			return getResult(new URL("http://169.254.169.254/latest/meta-data/local-ipv4").getContent());
		} catch (MalformedURLException e) {
			e.printStackTrace();
			throw new IOException(e);
		}
	}

}
