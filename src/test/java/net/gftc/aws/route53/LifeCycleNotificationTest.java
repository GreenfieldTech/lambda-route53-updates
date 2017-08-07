package net.gftc.aws.route53;

import java.io.IOException;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import tech.greenfield.aws.route53.LifeCycleNotification;

public class LifeCycleNotificationTest {

	static private ObjectMapper s_mapper = new ObjectMapper();
	
	@Before
	public void setup()  {
		s_mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
	}
	
	@Test
	public void parse() throws JsonParseException, JsonMappingException, IOException {
		String text = "{\n" + 
				"    \"LifecycleHookName\": \"app-name-LifeCycleSubscriptionLogicalName-XXXXXXXX\",\n" + 
				"    \"AccountId\": \"1234567890\",\n" + 
				"    \"RequestId\": \"12345678-1234-1234-abcd-1234567890ab\",\n" + 
				"    \"LifecycleTransition\": \"autoscaling:EC2_INSTANCE_LAUNCHING\",\n" + 
				"    \"AutoScalingGroupName\": \"app-name-AutoScalingLogicalGroupName-XXXXXXXX\",\n" + 
				"    \"Service\": \"AWS Auto Scaling\",\n" + 
				"    \"Time\": \"2016-12-18T16:19:23.580Z\",\n" + 
				"    \"EC2InstanceId\": \"i-0e27e0409ced0da85\",\n" + 
				"    \"LifecycleActionToken\": \"77777777-22cc-4444-abcd-1234567890ab\"\n" + 
				"}";
		s_mapper.readValue(text, LifeCycleNotification.class);

	}

}
