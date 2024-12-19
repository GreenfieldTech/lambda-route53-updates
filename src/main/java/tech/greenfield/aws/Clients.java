package tech.greenfield.aws;

import static tech.greenfield.aws.Tools.getCreds;
import static software.amazon.awssdk.regions.Region.AWS_GLOBAL;

import java.util.Objects;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import software.amazon.awssdk.services.autoscaling.AutoScalingAsyncClient;
import software.amazon.awssdk.services.ec2.Ec2AsyncClient;
import software.amazon.awssdk.services.route53.Route53AsyncClient;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;


/**
 * AWS SDK clients helper
 * 
 * Instantiate and cache client classes for the AWS API
 * 
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
public class Clients {

	private static Route53AsyncClient r53;
	private static Ec2AsyncClient ec2;
	private static AutoScalingAsyncClient autoscaling;
	private static SqsAsyncClient sqs;
	
	private static Logger log = LoggerFactory.getLogger(Clients.class.getName());
	
	synchronized public static Route53AsyncClient route53() {
		if (Objects.isNull(r53)) {
			log.info("Initializing Route53 client using " + Tools.getCredsProvider());
			r53 = Route53AsyncClient.builder().credentialsProvider(Tools.getCredsProvider())
					.region(AWS_GLOBAL).build();
		}
		return r53;
	}

	synchronized public static Ec2AsyncClient ec2() {
		if (Objects.isNull(ec2)) {
			log.info("Initializing EC2 client using " + getCreds());
			ec2 = Ec2AsyncClient.builder().credentialsProvider(Tools.getCredsProvider()).build();
		}
		return ec2;
	}
	
	synchronized public static AutoScalingAsyncClient autoscaling() {
		if (Objects.isNull(autoscaling)) {
			log.info("Initializing AutoScaling client using " + getCreds());
			autoscaling = AutoScalingAsyncClient.builder().credentialsProvider(Tools.getCredsProvider()).build();
		}
		return autoscaling;
	}
	
	synchronized public static SqsAsyncClient sqs() {
		if (Objects.isNull(sqs)) {
			log.info("Initializing SQS client using " + getCreds());
			sqs = SqsAsyncClient.builder().credentialsProvider(Tools.getCredsProvider()).build();
		}
		return sqs;
	}
	
}
