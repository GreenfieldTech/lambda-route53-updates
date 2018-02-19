package tech.greenfield.aws.route53;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Metadata {

	private String SRV_RECORD;
	private String DNSRR_RECORD;

	public String getSRV_RECORD() {
		return SRV_RECORD;
	}
	public void setSRV_RECORD(String sRV_RECORD) {
		SRV_RECORD = sRV_RECORD;
	}
	public String getDNSRR_RECORD() {
		return DNSRR_RECORD;
	}
	public void setDNSRR_RECORD(String dNSRR_RECORD) {
		DNSRR_RECORD = dNSRR_RECORD;
	}
	
}
