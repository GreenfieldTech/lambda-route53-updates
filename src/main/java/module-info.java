module tech.greenfield.route53 {
	requires transitive aws.lambda.java.core;
	requires transitive aws.lambda.java.events;
	requires com.fasterxml.jackson.databind;
	requires org.slf4j;
	requires software.amazon.awssdk.awscore;
	requires software.amazon.awssdk.core;
	requires software.amazon.awssdk.services.route53;
	requires software.amazon.awssdk.services.ec2;
	requires software.amazon.awssdk.services.autoscaling;
	requires transitive software.amazon.awssdk.services.sqs;
	requires software.amazon.awssdk.auth;
	requires software.amazon.awssdk.http;
	requires software.amazon.awssdk.identity.spi;
	requires software.amazon.awssdk.regions;
	requires software.amazon.awssdk.thirdparty.jackson.core;
	requires software.amazon.awssdk.utils;
	
	exports tech.greenfield.aws.route53;
	
	opens tech.greenfield.aws.route53 to com.fasterxml.jackson.databind;
}