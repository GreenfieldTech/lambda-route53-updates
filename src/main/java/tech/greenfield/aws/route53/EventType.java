package tech.greenfield.aws.route53;

public enum EventType {
	EC2_INSTANCE_LAUNCH,
	EC2_INSTANCE_LAUNCH_ERROR,
	EC2_INSTANCE_TERMINATE,
	EC2_INSTANCE_TERMINATE_ERROR,
	EC2_INSTANCE_LAUNCHING,
	EC2_INSTANCE_TERMINATING,
	TEST_NOTIFICATION // support AWS testing us by sending a test notification
}