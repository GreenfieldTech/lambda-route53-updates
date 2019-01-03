package tech.greenfield.aws;

import java.util.Objects;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

/**
 * AWS integration utilities.
 * 
 * Specifically this currently implements credential resolving with support for
 * specifying an AWS profile to be loaded from an AWS credentials file (for testing)
 * using the AWS_PROFILE environment variable
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
public class Tools {

	/**
	 * Retrieve the credentials to access AWS using the standard AWS credentials
	 * resolution process, with the addition of the option to select a non-default
	 * AWS CLI profile from the standard AWS credentials file (used for local testing)
	 * by specifying the AWS_PROFILE environment variable
	 * @return AWS credentials to use for accessing the AWS API
	 */
	public static AWSCredentials getCreds() {
		return getCredsProvider().getCredentials();
	}
	
	public static AWSCredentialsProvider getCredsProvider() {
		String forceProfile = System.getenv("AWS_PROFILE");
		if (Objects.nonNull(forceProfile) && !forceProfile.isEmpty())
			return new ProfileCredentialsProvider(forceProfile);
		return DefaultAWSCredentialsProviderChain.getInstance();
	}
	
}
