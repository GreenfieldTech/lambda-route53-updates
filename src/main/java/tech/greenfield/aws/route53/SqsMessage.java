package tech.greenfield.aws.route53;

import com.amazonaws.services.sqs.model.Message;

public class SqsMessage{
	
	private Message message;
	private Integer TTL;
	private String DNSRR_PRIVATE;
	private String SRV_RECORD;
	private String DNSRR_RECORD;
	private String HOSTED_ZONE_ID;
	private String DEBUG;
	
	public SqsMessage(Message message) {
		this.message = message;
		//fill in all the rest of the data
	}
	
	public String getBody(){
		return this.message.getBody();
	}
	
	public Integer getTTL() {
		return TTL;
	}
	
	public String getDNSRR_PRIVATE() {
		return DNSRR_PRIVATE;
	}
	
	public String getSRV_RECORD() {
		return SRV_RECORD;
	}
	
	public String getDNSRR_RECORD() {
		return DNSRR_RECORD;
	}
	
	public String getHOSTED_ZONE_ID() {
		return HOSTED_ZONE_ID;
	}
	
	public String getDEBUG() {
		return DEBUG;
	}

	public Message getMessage() {
		return message;
	}
	
	
}
