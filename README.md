# lambda-route53-updates

Java AWS Lambda package that receives notifications to update Route53 Hosted Zone DNS records.

This configurable implementation allows AWS Auto Scaling Groups to send events to SNS[1] topics that will cause Route53 DNS
resource records to be updated with the IP addresses of launched instances.

[![CircleCI Status](https://circleci.com/gh/GreenfieldTech/lambda-route53-updates.svg?style=svg)](https://circleci.com/gh/GreenfieldTech/lambda-route53-updates)
[![Travis Status](https://travis-ci.org/GreenfieldTech/lambda-route53-updates.svg?branch=master)](https://travis-ci.org/GreenfieldTech/lambda-route53-updates)


Features:
 * Support for both:
   * [auto scaling notifications][2] 
   * [life-cycle hooks][3]
 * Automatic detection of the type of notification and support life-cycle hook completion (if needed)
 * Support for DNS round-robin using multi-resource A records by adding and removing all of the auto-scaled instance pubilc
   IP addresses to a single Route53 A record.
 * Support for SRV records for client-side service discovery with configured weight, priority and port.

## Building

Use Maven to build the project with the `shade` goal to create a self-contained JAR that you can upload the AWS Lambda:

```
mvn package shade:shade
```

You can also just use the pre-built binaries in the [latest release that you can find here][4]

## Installation

Create an AWS Lambda and upload the generated JAR, for example - using the AWS Console. Make sure to set up the execution
role with the correct permissions for accessing the SNS topic you created. [See here for a tutorial on how to create
AWS Lambda to work with SNS][1].

 * Set the Lambda handler to: `tech.greenfield.aws.route53.NotifyRecords`
 * Set the maximum memory usage to 384MB - the implementation doesn't use anywhere close to that, but it appears that
   initialization of the SDK takes a large chunk of memory and the invocation will fail with the default 128MB setting.
 * Set the timeout to 60 seconds - because the lambda waits for the Route53 DNS servers to update before exiting,
   which can take a long while.

The following additional configuration must be done for the AWS Lambda function created:

### Permissions

Make sure the role you create for the AWS Lambda has permissions to update your Route53 DNS hosted zone as well as other APIs. Specifically we'll need
the following permissions:

 * `route53:ListResourceRecordSets`
 * `route53:ChangeResourceRecordSets`
 * `route53:GetChange`
 * `ec2:describeInstances`
 * `autoscaling:CompleteLifecycleAction` (if using life-cycle notifications)
 * `logs:CreateLogGroup` (to allow the lambda to create its own log group. Not needed if you are not interested in logs or will created the correct group yourself)
 * `logs:CreateLogStream` (to allow the lambda to create its own log group. Not needed if you are not interested in logs)
 * `logs:PutLogEvents` (to allow the lambda to create its own log group. Not needed if you are not interested in logs)

### Environment Variables

The java implementation includes no hard coded configuration values and all configuration is done using AWS Lambda's support
for "Environment Variables".

The lambda function reads the following environment variables:

 * `HOSTED_ZONE_ID` - specify the Route53 Hosted Zone identifier, as can be found from the Route53 console. Required.
 * `DNSRR_RECORD` - specify the fully qualified domain name (with or without a terminating dot) that you want the lambda to update.
   Optional - either one of the `DNSRR` setting and/or one of the `SRV` setting must be specified.
 * `DNSRR4_RECORD` - same as `DNSRR_RECORD`, but will only create an IPv4 `A` records (if the instance has an IPv4 address)
   Optional - either one of the `DNSRR` setting and/or one of the `SRV` setting must be specified.
 * `DNSRR_RECORD` - same as `DNSRR_RECORD`, but will only create an IPv6 `AAAA` records (if the instance has an IPv6 address)
   Optional - either one of the `DNSRR` setting and/or one of the `SRV` setting must be specified.
 * `SRV_RECORD` - specify the fully qualified domain name (with or without a terminating dot) that you want the lambda to update,
   including the required priority, weight and port (currently we assume these all to be set identically for all records). This
   setting requires a specific format to be used - a colon delimited list in the following format:
```
<priority>:<weight>:<port>:<fqdn>
```
   Optional - either this setting and/or one of the `DNSRR` settings must be specified.
 * `TTL` - specify a TTL in seconds to be set for new records created by lambda-route53-updates. If not specified defaults to 300.
 * `PRIVATE` - configure lambda-route53-updates to use the instances local (private) addresses when setting up DNS records, instead of
   public addresses. This is useful for implementing a split-horizon DNS setup, where you want to configure an additional lambda
   to update the private side of the zone. This setting affects both `DNSRR_RECORD` and `SRV_RECORD` configurations, if both are
   set on the same lambda. This is a boolean setting where any non-empty value means `true`. 
 * `DNSRR_PRIVATE` - configure lambda-route53-updates to use the instances local (private) addresses when setting up only DNSRR records. This is a boolean setting where any non-empty value means `true`.
 * `SRV_PRIVATE` - configure lambda-route53-updates to use the instances local (private) addresses when setting up only SRV records. This is a boolean setting where any non-empty value means `true`.
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

## Sample CloudFormation Template

### Using Life-Cycle Hooks With SRV Records

```
Resources:
  MyLambdaExecutionRole:
    Type: "AWS::IAM::Role"
    Properties:
      Path: /
      AssumeRolePolicyDocument:
        Statement:
          - Effect: Allow
            Principal: { Service: [ "lambda.amazonaws.com" ] }
            Action: [ "sts:AssumeRole" ]
      ManagedPolicyArns:
        - "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
      Policies:
        - PolicyName: MyRoute53LambdaUpdatePolicy
          PolicyDocument:
            Statement:
              - Effect: Allow
                Action:
                  - "logs:CreateLogGroup"
                  - "logs:CreateLogStream"
                  - "logs:PutLogEvents"
                Resource: "*"
              - Effect: Allow
                Action:
                  - "ec2:describeInstances"
                  - "autoscaling:CompleteLifecycleAction"
                  - "route53:ListResourceRecordSets"
                  - "route53:ChangeResourceRecordSets"
                  - "route53:GetChange"
                Resource: "*"

  MyLambdaFunction:
    Type: "AWS::Lambda::Function"
    Properties:
      FunctionName: my-route53-update-lambda
      Runtime: java8
      Code:
         S3Bucket: my-organization-ct-template
         S3Key: cf-resources/lambda-route53-updates-0.2.3.jar
      Description: Update DNS with auto-scaled instance public host names
      MemorySize: 256
      Timeout: 60
      Handler: tech.greenfield.aws.route53.NotifyRecords
      Environment:
        Variables:
          HOSTED_ZONE_ID: "Z1111LLLLN7777"
          SRV_RECORD: "1:1:5060:_sip._udp.example.com"
      Role: !GetAtt [ MyLambdaExecutionRole, Arn ]

  MyAutoscaleUpdateTopic:
    Type: "AWS::SNS::Topic"
    Properties: 
      DisplayName: autoscale-route53-updates
      TopicName: autoscale-route53-updates
      Subscription:
        - Protocol: lambda
          Endpoint: !GetAtt [ MyLambdaFunction, Arn ]

  MyLambdaInvokePermission:
    Type: "AWS::Lambda::Permission"
    Properties:
      FunctionName: !GetAtt [ MyLambdaFunction, Arn ]
      Action: "lambda:InvokeFunction"
      Principal: sns.amazonaws.com
      SourceArn: !Ref MyAutoscaleUpdateTopic
    DependsOn:
      - MyLambdaFunction

  MyAutoScaleGroupLifeCycleScaleOut:
    Type: "AWS::AutoScaling::LifecycleHook"
    Properties:
      AutoScalingGroupName: !Ref MyAutoScaleGroup
      DefaultResult: ABANDON
      LifecycleTransition: "autoscaling:EC2_INSTANCE_LAUNCHING"
      NotificationTargetARN: !Ref MyAutoscaleUpdateTopic
      RoleARN: !GetAtt [ MyLambdaExecutionRole, Arn ]

  MyAutoScaleGroupLifeCycleScaleIn:
    Type: "AWS::AutoScaling::LifecycleHook"
    Properties:
      AutoScalingGroupName: !Ref MyAutoScaleGroup
      DefaultResult: ABANDON
      LifecycleTransition: "autoscaling:EC2_INSTANCE_TERMINATING"
      NotificationTargetARN: !Ref MyAutoscaleUpdateTopic
      RoleARN: !GetAtt [ MyLambdaExecutionRole, Arn ]
```

## Using The Testing CloudFormation Templates

The projects comes with a few CloudFormation testing templates that can be found under `src/test/resources`. These
can be used to exercise the Lambda code, or as a basis to build your own deployemnts.

The following test templates are available:

 - `cf-asg-notification.yaml` : connect the autoscaling group to Lambda using autoscaling notifications. Only environment
   variable configuration is supported.
 - `cf-lifecycle-env.yaml` : connect the autoscaling group to Lambda using an SNS topic and configure it using
   environment variables.
 - `cf-lifecycle-md.yaml` : connect the autoscaling group to Lambda using an SNS topic and configure it using notification
   metadata. This deployment is appropriate for using the same Lambda deployment to manage multiple autoscaling groups.
 - `cf-lifecycle-sqs.yaml` : connect the autoscaling group to Lambda using an SQS queue with single concurrency to prevent
   Route53 edit conflicts. This deployment is appropriate when using the same Lambda for a lot of auto scaling groups with
   high amount of scaling changes.

To use these test templates:

1. Install the `cloudformation-tool` gem: 
`gem install --user cloudformation-tool`
1. Make sure you have a key pair that can be used (a key pair is not managed by the template): 
`aws ec2 describe-key-pairs`
1. Build the Lambda:
`mvn package`
1. Create a new CloudFormation stack:
`cftool create -p KeyName=<your-keypair> src/test/resources/cf-lifecycle-env.yaml r53-test`
1. Scale up the created stack:
`cftool scale r53-test ASGTest 1`
1. Wait until the scaling action has completed (i.e. the new server is "InService")
1. Check that the Route53 hosted zone has been updated with the correct record, using the Route53 console
1. Scale down the stack:
`cftool scale r53-test ASGTest 0`
1. Check that the Route53 hosted zone had the DNS record removed, using the Route53 console
1. Remove the stack when you finish testing:
`cftool delete r53-test`

[1]: http://docs.aws.amazon.com/lambda/latest/dg/with-sns-example.html
[2]: http://docs.aws.amazon.com/autoscaling/latest/userguide/ASGettingNotifications.html
[3]: http://docs.aws.amazon.com/autoscaling/latest/userguide/lifecycle-hooks.html
[4]: https://github.com/GreenfieldTech/lambda-route53-updates/releases/latest
