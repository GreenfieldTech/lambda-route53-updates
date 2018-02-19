package tech.greenfield.aws.route53;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

import com.amazonaws.services.sqs.model.Message;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

public class SqsMessage{
	
	private Message message;
	private Map<String, Object> body;
	private Metadata metadata;
	private String SRV_RECORD;
	private String DNSRR_RECORD;
	static private ObjectMapper s_mapper = new ObjectMapper();
	private final static Logger logger = Logger.getLogger(NotifyRecordsSqs.class.getName());
	
	static {
		s_mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
	}
	
	public SqsMessage(Message message) {
		this.message = message;
		this.body = retreiveBody();
		logger.info("SQS message body: " + body);
		try {
			String metadataStr = body.get("NotificationMetadata").toString();
			metadata = s_mapper.readValue(metadataStr, Metadata.class);
			this.SRV_RECORD = metadata.getSRV_RECORD();
			this.DNSRR_RECORD = metadata.getDNSRR_RECORD();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public Map<String, Object> retreiveBody(){
		String sqsMessageText = this.message.getBody();
		Map<String, Object> obj = null;
		try {
			obj = s_mapper.readValue(sqsMessageText, Map.class);
			obj.putAll(s_mapper.readValue(obj.get("Message").toString(), Map.class));
//			obj.remove("Message");
		} catch (IOException e) {
			e.printStackTrace();
		}
		logger.info("Parsed body: " + obj);
		return obj;
	}
	
	public Map<String, Object> getBody(){
		return body;
	}
	
	public String getSRV_RECORD() {
		return SRV_RECORD;
	}
	
	public String getDNSRR_RECORD() {
		return DNSRR_RECORD;
	}

	public Message getMessage() {
		return message;
	}
	
	
}
