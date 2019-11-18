package tech.greenfield.aws.route53;

import static tech.greenfield.aws.Clients.*;

import java.io.*;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;

import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.sqs.model.Message;


public class NotifyRecordsSqs extends BaseNotifyRecords implements RequestHandler<SNSEvent, Route53UpdateResponse>{

	private static String queueUrl = null;
	
	@Override
	public Route53UpdateResponse handleRequest(SNSEvent input, Context context) {
		setupLogger(context);
		logger.info("Handling sqs request for " + input);
		try {
			return findMessages(10, 300)
			.thenCompose(messages -> {
				logger.fine("Handling " + messages.size() + " messages from queue.");
				CompletableFuture<Route53UpdateResponse> res = CompletableFuture.completedFuture(null);
				for (Message message : messages) {
					res = res.thenCompose(v -> {
						try {
							return handleMessage(new Route53Message(message), context);
						} catch (ParsingException e) {
							Tools.logException(logger, "Failed to parse notification",e);
							logger.severe("Original message: " + message.body());
							return CompletableFuture.completedFuture(null);
						}
					})
					.exceptionally(t -> {
						if (Objects.nonNull(t))
							Tools.logException(logger, "Unexpected error during handling message", t);
						return null;
					});
					// assuming we don't want to retry, and completion will take a while
					// (to wait for the change to complete) so start deleting the message early to not block
					// the end of the invocation
					deleteMessage(message); 
				}
				return res;
			})
			.thenApply(v -> Response.ok())
			.exceptionally(e -> {
				Tools.logException(logger, "Couldn't get/handle sqs messages", e);
				return Response.error(e.getMessage());
			}).get();
		} catch (InterruptedException | ExecutionException e) {
			logger.severe("Unexpected exception in SQS request handler: " + e);
			return Response.error(e.getMessage());
		}
	}
	
	private CompletableFuture<List<Message>> findMessages(int iterations, long delay) {
		return getMessages()
				.thenCompose(l -> {
					if (l.size() > 0)
						return CompletableFuture.completedFuture(l);
					if (iterations <= 1)
						return CompletableFuture.completedFuture(Collections.emptyList());
					return CompletableFuture.runAsync(Tools.delay(delay))
							.thenCompose(v -> findMessages(iterations - 1, delay));
				});
	}
	
	public CompletableFuture<Route53UpdateResponse> handleMessage(Route53Message input, Context context) {
		return input.createEventHandler(context).handle()
				.thenApply(v -> {
					context.getLogger().log("Done updating Route53");
					return Response.ok();
				})
				.exceptionally(t -> {
					Tools.logException(logger, "Unexpected error while updating Route53", t);
					return Response.error(t.toString()); 
				});
	}
	
	public CompletableFuture<List<Message>> getMessages() {
		return getQueueUrl()
				.thenCompose(queue -> sqs().receiveMessage(b -> b.queueUrl(queue).maxNumberOfMessages(10)))
				.thenApply(res -> res.messages());
	}

	/**
	 * Delete the SQS message, asynchronously
	 * i.e. we don't wait nor do we care about the completion status of the deletion
	 * @param message SQS message to delete
	 */
	public void deleteMessage(Message message) {
		getQueueUrl()
		.thenCompose(queue -> sqs().deleteMessage(b -> b.queueUrl(queue).receiptHandle(message.receiptHandle())))
		.whenComplete((d, t) -> {
			if (Objects.nonNull(t) || !d.sdkHttpResponse().isSuccessful())
				logger.severe("Failed to delete message: " + t);
			else
				logger.fine("Deleted message " + message.messageId());
		});
	}

	private CompletableFuture<String> getQueueUrl() {
		if(Objects.nonNull(queueUrl))
			return CompletableFuture.completedFuture(queueUrl);
		logger.entering(this.getClass().getName(), "getQueueUrl");
		if(Objects.nonNull(System.getenv("QUEUE_URL")))
			return CompletableFuture.completedFuture(queueUrl = System.getenv("QUEUE_URL"));
		try {
			Filter f = Filter.builder().name("resource-id").values(getInstanceId()).build();
			return ec2().describeTags(b -> b.filters(f))
					.thenApply(res -> res.tags().stream()
							.filter(tag -> tag.key().equals("QueueUrl")).findFirst()
							.orElseThrow(() -> new CompletionException(new IOException("Queue URL tag missing")))
							.value())
					.whenComplete((url, t) -> queueUrl = url);
		} catch (IOException e) { // only catches what getInstanceId() throws, the filter thing gets a failed future
			CompletableFuture<String> fail = new CompletableFuture<>();
			fail.completeExceptionally(e);
			return fail;
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
