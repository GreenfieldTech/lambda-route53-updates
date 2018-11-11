package net.gftc.aws.route53;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.*;

import tech.greenfield.aws.route53.Metadata;

public class TestMetadata {

	static private ObjectMapper s_mapper = new ObjectMapper();

	static {
		s_mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true);
		s_mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
	}
	
	@Test
	public void testSingleRR() throws JsonParseException, JsonMappingException, IOException {
		String text = "{\"DNSRR_RECORD\":\"subscribers.cloudonix.io\"}";
		Metadata metadata = s_mapper.readValue(text, Metadata.class);
		assertEquals(0,metadata.getSRV_RECORD().size());
		List<String> records = metadata.getDNSRR_RECORD();
		assertEquals(1, records.size());
		assertEquals("subscribers.cloudonix.io", records.get(0));
	}

	@Test
	public void testMultipleRR() throws JsonParseException, JsonMappingException, IOException {
		String text = "{\"DNSRR_RECORD\":[\"subscribers.cloudonix.io\",\"subscribers.us-east-2.cloudonix.io\"]}";
		Metadata metadata = s_mapper.readValue(text, Metadata.class);
		assertEquals(0,metadata.getSRV_RECORD().size());
		List<String> records = metadata.getDNSRR_RECORD();
		assertEquals(2, records.size());
		assertEquals("subscribers.cloudonix.io", records.get(0));
		assertEquals("subscribers.us-east-2.cloudonix.io", records.get(1));
	}

	@Test
	public void testSingleSRV() throws JsonParseException, JsonMappingException, IOException {
		String text = "{\"SRV_RECORD\":\"1:1:5060:_sip._udp.subscribers.cloudonix.io\"}";
		Metadata metadata = s_mapper.readValue(text, Metadata.class);
		assertEquals(0,metadata.getDNSRR_RECORD().size());
		List<String> records = metadata.getSRV_RECORD();
		assertEquals(1, records.size());
		assertEquals("1:1:5060:_sip._udp.subscribers.cloudonix.io", records.get(0));
	}

	@Test
	public void testMultipleSRV() throws JsonParseException, JsonMappingException, IOException {
		String text = "{\"SRV_RECORD\":[\"1:1:5060:_sip._udp.subscribers.cloudonix.io\","
				+ "\"1:1:5060:_sip._udp.subscribers.us-east-2.cloudonix.io\","
				+ "\"1:1:5061:_sip._udp.subscribers.cloudonix.io\","
				+ "\"1:1:5061:_sip._udp.subscribers.us-east-2.cloudonix.io\"]}";
		Metadata metadata = s_mapper.readValue(text, Metadata.class);
		assertEquals(0,metadata.getDNSRR_RECORD().size());
		List<String> records = metadata.getSRV_RECORD();
		assertEquals(4, records.size());
		assertEquals("1:1:5060:_sip._udp.subscribers.cloudonix.io", records.get(0));
		assertEquals("1:1:5061:_sip._udp.subscribers.us-east-2.cloudonix.io", records.get(3));
	}

}
