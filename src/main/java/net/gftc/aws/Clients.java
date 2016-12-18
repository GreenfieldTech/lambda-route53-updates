package net.gftc.aws;

import java.util.Objects;

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

	synchronized public static AmazonRoute53Client route53() {
		if (Objects.isNull(r53)) {
			System.err.println("Initializing Route53 client using " + net.gftc.aws.Tools.getCreds());
			r53 = new AmazonRoute53Client(net.gftc.aws.Tools.getCreds());
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
