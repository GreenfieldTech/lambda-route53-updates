package net.gftc.aws.route53;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * POJO representing an AutoScaling notification received through SNS
 * 
 * A launch notification looks like this:
 * 
 * {
 *     "Progress": 50,
 *     "AccountId": "XXXXXXXX",
 *     "Description": "Launching a new EC2 instance: i-0a76a1e9afa9f4d16",
 *     "RequestId": "4ce999d2-11a9-4fc9-b5b1-16057b35d120",
 *     "EndTime": "2016-12-12T18:39:28.378Z",
 *     "AutoScalingGroupARN": "arn:aws:autoscaling:ap-southeast-1:XXXXXX:autoScalingGroup:d1c963cb-e0dc-4946-8d0d-7be4d36ef7f2:autoScalingGroupName/this-is-the-asg-name",
 *     "ActivityId": "4ce999d2-11a9-4fc9-b5b1-16057b35d120",
 *     "StartTime": "2016-12-12T18:38:55.071Z",
 *     "Service": "AWS Auto Scaling",
 *     "Time": "2016-12-12T18:39:28.378Z",
 *     "EC2InstanceId": "i-0a76a1e9afa9f4d16",
 *     "StatusCode": "InProgress",
 *     "StatusMessage": "",
 *     "Details": {
 *         "Subnet ID": "subnet-7382e517",
 *         "Availability Zone": "ap-southeast-1a"
 *     },
 *     "AutoScalingGroupName": "switch-core-aps-AutoscaleRouteServer-1PRDZE4MVOW3G",
 *     "Cause": "At 2016-12-12T18:38:22Z a user request update of AutoScalingGroup constraints to min: 1, max: 10, desired: 3 changing the desired capacity from 2 to 3.  At 2016-12-12T18:38:53Z an instance was started in response to a difference between desired and actual capacity, increasing the capacity from 2 to 3.",
 *     "Event": "autoscaling:EC2_INSTANCE_LAUNCH"
 * }
 * 
 * A termination notofication looks like this:
 * 
 * {
 *     "Progress": 50,
 *     "AccountId": "568507439762",
 *     "Description": "Terminating EC2 instance: i-004d13a7d5294ca7e",
 *     "RequestId": "aded827d-1827-426a-9a6b-1db450c253e5",
 *     "EndTime": "2016-12-12T18:54:28.063Z",
 *     "AutoScalingGroupARN": "arn:aws:autoscaling:ap-southeast-1:568507439762:autoScalingGroup:d1c963cb-e0dc-4946-8d0d-7be4d36ef7f2:autoScalingGroupName/switch-core-aps-AutoscaleRouteServer-1PRDZE4MVOW3G",
 *     "ActivityId": "aded827d-1827-426a-9a6b-1db450c253e5",
 *     "StartTime": "2016-12-12T18:53:42.980Z",
 *     "Service": "AWS Auto Scaling",
 *     "Time": "2016-12-12T18:54:28.063Z",
 *     "EC2InstanceId": "i-004d13a7d5294ca7e",
 *     "StatusCode": "InProgress",
 *     "StatusMessage": "",
 *     "Details": {
 *         "Subnet ID": "subnet-7382e517",
 *         "Availability Zone": "ap-southeast-1a"
 *     },
 *     "AutoScalingGroupName": "switch-core-aps-AutoscaleRouteServer-1PRDZE4MVOW3G",
 *     "Cause": "At 2016-12-12T18:53:28Z a user request update of AutoScalingGroup constraints to min: 1, max: 10, desired: 2 changing the desired capacity from 3 to 2.  At 2016-12-12T18:53:42Z an instance was taken out of service in response to a difference between desired and actual capacity, shrinking the capacity from 3 to 2.  At 2016-12-12T18:53:42Z instance i-004d13a7d5294ca7e was selected for termination.",
 *     "Event": "autoscaling:EC2_INSTANCE_TERMINATE"
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
public class AutoScalingNotification {
	
	private String progress;
	private String accountId;
	private String description;
	private String requestId;
	private String endTime;
	private String autoScalingGroupARN;
	private String activityId;
	private String startTime;
	private String service;
	private String time;
	private String EC2InstanceId;
	private String statusCode;
	private String statusMessage;
	private Map<String,String> details;
	private String autoScalingGroupName;
	private String cause;
	private EventType event;
	
	public String getProgress() {
		return progress;
	}
	public void setProgress(String progress) {
		this.progress = progress;
	}
	public String getAccountId() {
		return accountId;
	}
	public void setAccountId(String accountId) {
		this.accountId = accountId;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public String getRequestId() {
		return requestId;
	}
	public void setRequestId(String requestId) {
		this.requestId = requestId;
	}
	public String getEndTime() {
		return endTime;
	}
	public void setEndTime(String endTime) {
		this.endTime = endTime;
	}
	public String getAutoScalingGroupARN() {
		return autoScalingGroupARN;
	}
	public void setAutoScalingGroupARN(String autoScalingGroupARN) {
		this.autoScalingGroupARN = autoScalingGroupARN;
	}
	public String getActivityId() {
		return activityId;
	}
	public void setActivityId(String activityId) {
		this.activityId = activityId;
	}
	public String getStartTime() {
		return startTime;
	}
	public void setStartTime(String startTime) {
		this.startTime = startTime;
	}
	public String getService() {
		return service;
	}
	public void setService(String service) {
		this.service = service;
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
	public String getStatusCode() {
		return statusCode;
	}
	public void setStatusCode(String statusCode) {
		this.statusCode = statusCode;
	}
	public String getStatusMessage() {
		return statusMessage;
	}
	public void setStatusMessage(String statusMessage) {
		this.statusMessage = statusMessage;
	}
	public Map<String, String> getDetails() {
		return details;
	}
	public void setDetails(Map<String, String> details) {
		this.details = details;
	}
	public String getAutoScalingGroupName() {
		return autoScalingGroupName;
	}
	public void setAutoScalingGroupName(String autoScalingGroupName) {
		this.autoScalingGroupName = autoScalingGroupName;
	}
	public String getCause() {
		return cause;
	}
	public void setCause(String cause) {
		this.cause = cause;
	}
	public String getEvent() {
		return event.toString();
	}
	public void setEvent(String event) {
		this.event = EventType.valueOf(event.replace("autoscaling:", ""));
	}
	
	@JsonIgnore
	public EventType getType() {
		return event;
	}
}
