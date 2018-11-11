package tech.greenfield.aws.route53;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Metadata {
	
	private List<String> SRV_RECORD;
	private List<String> DNSRR_RECORD;

	public List<String> getSRV_RECORD() {
		if(Objects.isNull(SRV_RECORD))
			return Collections.emptyList();
		return SRV_RECORD;
	}
	
	public void setSRV_RECORD(List<String> sRV_RECORD) {
		SRV_RECORD = sRV_RECORD;
	}
	
	public List<String> getDNSRR_RECORD() {
		if(Objects.isNull(DNSRR_RECORD))
			return Collections.emptyList();
		return DNSRR_RECORD;
	}
	public void setDNSRR_RECORD(List<String> dNSRR_RECORD) {
		DNSRR_RECORD = dNSRR_RECORD;
	}
	
}
