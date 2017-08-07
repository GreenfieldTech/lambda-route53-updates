package tech.greenfield.aws;

import static tech.greenfield.aws.Tools.getCreds;

import java.util.Objects;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.route53.AmazonRoute53Client;

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

	static private AmazonRoute53Client r53;
	static private AmazonEC2Client ec2;
	private static AmazonAutoScaling autoscaling;
	
	private static Region region = Regions.getCurrentRegion();
	
	static {
		if (Objects.isNull(region)) {
			String defaultRegion = System.getenv("AWS_DEFAULT_REGION");
			if (Objects.isNull(defaultRegion))
				throw new RuntimeException("No default region find, hard coding ap-southeast-1");
			else
				region = Region.getRegion(Regions.fromName(defaultRegion));
		}
	}

	synchronized public static AmazonRoute53Client route53() {
		if (Objects.isNull(r53)) {
			System.err.println("Initializing Route53 client using " + getCreds());
			r53 = new AmazonRoute53Client(getCreds());
			r53.setRegion(region);
		}
		return r53;
	}

	synchronized public static AmazonEC2Client ec2() {
		if (Objects.isNull(ec2)) {
			System.err.println("Initializing EC2 client using " + getCreds());
			ec2 = new AmazonEC2Client(getCreds());
			ec2.setRegion(region);
		}
		return ec2;
	}
	
	synchronized public static AmazonAutoScaling autoscaling() {
		if (Objects.isNull(autoscaling)) {
			System.err.println("Initializing AutoScaling client using " + getCreds());
			autoscaling = new AmazonAutoScalingClient(getCreds());
			autoscaling.setRegion(region);
		}
		return autoscaling;
	}
	
}
