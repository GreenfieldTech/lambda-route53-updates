# lambda-route53-updates

Java AWS Lambda package that receives notifications to update Route53 Hosted Zone DNS records.

This configurable implementation allows AWS Auto Scaling Groups to send events to SNS topics that will cause Route53 DNS
resource records to be updated with the IP addresses of launched instances.

Currently the implementation supports only scaling notifications, but life-cycle hook support is planned.

The following DNS resource types are supported:

1. A records - the function will add or remove the public IP address of the launched instance to a DNS round-robin style A record
1. SRV records - the function will add or remove the public host name of the launched instance, along with the specified
   weight, priority and port to an SRV record.

## Building

Use Maven to build the project with the `shade` goal to create a self-contained JAR that you can upload the AWS Lambda:

```
mvn package shade:shade
```

## Installation

Create an AWS Lambda and upload the generated JAR, for example - using the AWS Console. Make sure to set up the execution
role with the correct permissions for accessing the SNS topic you created. [See here for a tutorial on how to create
AWS Lambda to work with SNS][1].

The following additional configuration must be done for the AWS Lambda function created:

### Permissions

Make sure the role you create for the AWS Lambda has permissions to update your Route53 DNS hosted zone. Specifically we'll need
the following permissions:

 * `route53:ChangeResourceRecordSets`
 * `route53:GetChange`
 * `route53:GetGeoLocation`
 * `route53:ListGeoLocations`
 * `route53:ListResourceRecordSets`

[1]: http://docs.aws.amazon.com/lambda/latest/dg/with-sns-example.html

### Environment Variables

The java implementation includes no hard coded configuration values and all configuration is done using AWS Lambda's support for "Environment Variables".

The lambda function reads the following environment variables:

 * `HOSTED_ZONE_ID` - specify the Route53 Hosted Zone identifier, as can be found from the Route53 console. Required.
 * `DNSRR_RECORD` - specify the fully qualified domain name (with or without a terminating dot) that you want the lambda to update.
   Optional - either this setting or the `SRV_RECORD` setting must be specified.
 * `SRV_RECORD` - specify the fully qualified domain name (with or without a terminating dot) that you want the lambda to update,
   including the required priority, weight and port (currently we assume these all to be set identically for all records). This
   setting requires a specific format to be used - a colon delimited list in the following format: 
```
<priority>:<weight>:<port>:<fqdn>
```
 * `DEBUG` - enable debug logging. This mostly logs the raw SNS message that was received, to debug the parser. Optional.
 * `AWS_PROFILE` - support local testing (outside AWS Lambda). Normally the code assumes an IAM profile will be used to provide the
   required authorization, but when testing the code locally, one may use an AWS CLI credentials file. This setting allows a local
   user to run the code with full authorization, even when choosing to use a non-default AWS CLI credentials profile, by specifying
   the profile name. Optional.

## Testing

To test:

1. Create an auto scaling group.
2. Create an SNS topic.
3. Create a Route53 Hosted Zone.
4. Create a lambda function as described above and configure it to receive events from the SNS topic created in step 2. Also
   make sure to set up the environment variables to the hosted zone you created in step 3 and the setting for DNS A round-robin or
   SRV record.
5. Set up scaling notifications from the auto scaling group to the SNS topic created in step 2.
6. Set the auto scaling group "desired" field to a new value to cause an instance to be launched or terminated.
7. Wait a bit.
8. Check the AWS CloudWatch log for the lambda function.
9. Check the Route53 hosted zone to see if the required changes were made.
