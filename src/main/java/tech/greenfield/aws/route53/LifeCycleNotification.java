package tech.greenfield.aws.route53;

import com.amazonaws.services.kinesisfirehose.model.InvalidArgumentException;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * POJO representing an AutoScaling life cycle event notification received through SNS
 *
 * A launch notification looks like this:
 * 
 * {
 *     "LifecycleHookName": "app-name-LifeCycleSubscriptionLogicalName-XXXXXXXX",
 *     "AccountId": "1234567890",
 *     "RequestId": "12345678-1234-1234-abcd-1234567890ab",
 *     "LifecycleTransition": "autoscaling:EC2_INSTANCE_LAUNCHING",
 *     "AutoScalingGroupName": "app-name-AutoScalingLogicalGroupName-XXXXXXXX",
 *     "Service": "AWS Auto Scaling",
 *     "Time": "2016-12-18T16:19:23.580Z",
 *     "EC2InstanceId": "i-0e27e0409ced0da85",
 *     "LifecycleActionToken": "77777777-22cc-4444-abcd-1234567890ab"
 * }
 * 
 * A termination notification looks like this:
 * 
 * {
 *     "LifecycleHookName": "app-name-LifeCycleSubscriptionLogicalName-XXXXXXXX",
 *     "AccountId": "568507439762",
 *     "RequestId": "8b340391-11d4-4303-b1ce-4707fd609d8b",
 *     "LifecycleTransition": "autoscaling:EC2_INSTANCE_TERMINATING",
 *     "AutoScalingGroupName": "app-name-AutoScalingLogicalGroupName-XXXXXXXX",
 *     "Service": "AWS Auto Scaling",
 *     "Time": "2016-12-18T16:19:23.580Z",
 *     "EC2InstanceId": "i-0e27e0409ced0da85",
 *     "LifecycleActionToken": "77777777-22cc-4444-abcd-1234567890ab"
 * }
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
public class LifeCycleNotification {

	private String lifecycleHookName;
	private String accountId;
	private String requestId;
	private EventType lifecycleTransition;
	private String autoScalingGroupName;
	private String service;
	private String time;
	private String EC2InstanceId;
	private String lifecycleActionToken;
	
	public String getLifecycleHookName() {
		return lifecycleHookName;
	}
	public void setLifecycleHookName(String lifecycleHookName) {
		this.lifecycleHookName = lifecycleHookName;
	}
	public String getAccountId() {
		return accountId;
	}
	public void setAccountId(String accountId) {
		this.accountId = accountId;
	}
	public String getRequestId() {
		return requestId;
	}
	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}
	
	@JsonIgnore
	public EventType getType() {
		switch (lifecycleTransition) {
		case EC2_INSTANCE_LAUNCHING: return EventType.EC2_INSTANCE_LAUNCH;
		case EC2_INSTANCE_TERMINATING: return EventType.EC2_INSTANCE_TERMINATE;
		default: throw new InvalidArgumentException("Unsupported lifecycle event type: " + lifecycleTransition);
		}
	}
	
	public String getLifecycleTransition() {
		return lifecycleTransition.toString();
	}
	public void setLifecycleTransition(String lifecycleTransition) {
		this.lifecycleTransition = EventType.valueOf(
				lifecycleTransition.replace("autoscaling:", ""));;
	}
	public String getAutoScalingGroupName() {
		return autoScalingGroupName;
	}
	public void setAutoScalingGroupName(String autoScalingGroupName) {
		this.autoScalingGroupName = autoScalingGroupName;
	}
	public String getService() {
		return service;
	}
	public void setService(String activityservice) {
		this.service = activityservice;
	}
	public String getTime() {
		return time;
	}
	public void setTime(String time) {
		this.time = time;
	}
	public String getEC2InstanceId() {
		return EC2InstanceId;
	}
	public void setEC2InstanceId(String eC2InstanceId) {
		EC2InstanceId = eC2InstanceId;
	}
	public String getLifecycleActionToken() {
		return lifecycleActionToken;
	}
	public void setLifecycleActionToken(String lifecycleActionToken) {
		this.lifecycleActionToken = lifecycleActionToken;
	}

}
