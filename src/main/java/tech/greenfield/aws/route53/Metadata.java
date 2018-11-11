package tech.greenfield.aws.route53;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Metadata {
	
	private List<String> SRV_RECORD = Collections.emptyList();
	private List<String> DNSRR_RECORD = Collections.emptyList();
	private List<String> SRV4_RECORD = Collections.emptyList();
	private List<String> DNSRR4_RECORD = Collections.emptyList();
	private List<String> SRV6_RECORD = Collections.emptyList();
	private List<String> DNSRR6_RECORD = Collections.emptyList();

	public List<String> getSRV_RECORD() {
		return SRV_RECORD;
	}
	
	public void setSRV_RECORD(List<String> sRV_RECORD) {
		SRV_RECORD = sRV_RECORD;
	}
	
	public List<String> getDNSRR_RECORD() {
		return DNSRR_RECORD;
	}
	public void setDNSRR_RECORD(List<String> dNSRR_RECORD) {
		DNSRR_RECORD = dNSRR_RECORD;
	}

	public List<String> getSRV4_RECORD() {
		return SRV4_RECORD;
	}

	public void setSRV4_RECORD(List<String> sRV4_RECORD) {
		SRV4_RECORD = sRV4_RECORD;
	}

	public List<String> getDNSRR4_RECORD() {
		return DNSRR4_RECORD;
	}

	public void setDNSRR4_RECORD(List<String> dNSRR4_RECORD) {
		DNSRR4_RECORD = dNSRR4_RECORD;
	}

	public List<String> getSRV6_RECORD() {
		return SRV6_RECORD;
	}

	public void setSRV6_RECORD(List<String> sRV6_RECORD) {
		SRV6_RECORD = sRV6_RECORD;
	}

	public List<String> getDNSRR6_RECORD() {
		return DNSRR6_RECORD;
	}

	public void setDNSRR6_RECORD(List<String> dNSRR6_RECORD) {
		DNSRR6_RECORD = dNSRR6_RECORD;
	}
	
}
