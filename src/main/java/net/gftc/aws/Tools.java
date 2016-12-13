package net.gftc.aws;

import java.util.Objects;

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;

public class Tools {

	public static AWSCredentials getCreds() {
		String forceProfile = System.getenv("AWS_PROFILE");
		if (Objects.nonNull(forceProfile) && !forceProfile.isEmpty())
			return new ProfileCredentialsProvider(forceProfile).getCredentials();
		return DefaultAWSCredentialsProviderChain.getInstance().getCredentials();
	}
	
}
