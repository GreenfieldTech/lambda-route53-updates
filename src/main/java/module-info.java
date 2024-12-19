module tech.greenfield.route53 {
	requires software.amazon.awssdk.services.route53;
	requires software.amazon.awssdk.services.ec2;
	requires software.amazon.awssdk.services.autoscaling;
	requires software.amazon.awssdk.services.sqs;
	requires software.amazon.awssdk.auth;
	requires software.amazon.awssdk.regions;
	requires org.slf4j;
	requires aws.lambda.java.core;
	requires aws.lambda.java.events;
	requires com.fasterxml.jackson.databind;
	requires software.amazon.awssdk.thirdparty.jackson.core;
	requires software.amazon.awssdk.http;
}