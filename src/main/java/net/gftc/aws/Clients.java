package net.gftc.aws;

import java.util.Objects;

import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.route53.AmazonRoute53Client;

public class Clients {

	static private AmazonRoute53Client r53;
	static private AmazonEC2Client ec2;

	synchronized public static AmazonRoute53Client route53() {
		if (Objects.isNull(r53)) {
			System.err.println("Initializing Route53 client using " + net.gftc.aws.Tools.getCreds());
			r53 =new AmazonRoute53Client(net.gftc.aws.Tools.getCreds());
		}
		return r53;
	}

	synchronized public static AmazonEC2Client ec2() {
		if (Objects.isNull(ec2)) {
			System.err.println("Initializing EC2 client using " + net.gftc.aws.Tools.getCreds());
			ec2 = new AmazonEC2Client(net.gftc.aws.Tools.getCreds());
		}
		return ec2;
	}
}
