package tech.greenfield.aws.route53;

import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Metadata {
	
	private List<String> SRV_RECORD = Collections.emptyList();
	private List<String> DNSRR_RECORD = Collections.emptyList();
	private List<String> SRV4_RECORD = Collections.emptyList();
	private List<String> DNSRR4_RECORD = Collections.emptyList();
	private List<String> SRV6_RECORD = Collections.emptyList();
	private List<String> DNSRR6_RECORD = Collections.emptyList();
	
	public static Metadata fromEnvironment() {
		Metadata metadata = new Metadata();
		metadata.setSRV_RECORD(getEnvByPrefix("SRV_RECORD"));
		metadata.setSRV4_RECORD(getEnvByPrefix("SRV4_RECORD"));
		metadata.setSRV6_RECORD(getEnvByPrefix("SRV6_RECORD"));
		metadata.setDNSRR_RECORD(getEnvByPrefix("DNSRR_RECORD"));
		metadata.setDNSRR4_RECORD(getEnvByPrefix("DNSRR4_RECORD"));
		metadata.setDNSRR6_RECORD(getEnvByPrefix("DNSRR6_RECORD"));
		return metadata;
	}

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

	public List<SRVTemplate> getSRVSpec() {
		return SRVTemplate.parse(getSRV_RECORD());
	}

	public List<SRVTemplate> getSRV4Spec() {
		return SRVTemplate.parse(getSRV4_RECORD());
	}

	public List<SRVTemplate> getSRV6Spec() {
		return SRVTemplate.parse(getSRV6_RECORD());
	}
	
	public List<String> getRRSpec() {
		return getDNSRR_RECORD().stream().map(addr -> 
			addr.endsWith(".") ? addr : (addr + ".")).collect(Collectors.toList());
	}
	
	public List<String> getRR4Spec() {
		return getDNSRR4_RECORD().stream().map(addr -> 
			addr.endsWith(".") ? addr : (addr + ".")).collect(Collectors.toList());
	}
	
	public List<String> getRR6Spec() {
		return getDNSRR6_RECORD().stream().map(addr -> 
			addr.endsWith(".") ? addr : (addr + ".")).collect(Collectors.toList());
	}
	
	/**
	 * Retrieve all environment values whose keys start with the specified prefix
	 * @param prefix environment variable name prefix
	 * @return List of environment values
	 */
	private static List<String> getEnvByPrefix(String prefix) {
		return System.getenv().entrySet().stream().filter(e -> e.getKey().startsWith(prefix))
				.map(e -> e.getValue()).collect(Collectors.toList());
	}

	public boolean hasSRV() {
		return !(SRV_RECORD.isEmpty() && SRV4_RECORD.isEmpty() && SRV6_RECORD.isEmpty());
	}

	public boolean hasDNSRR() {
		return !(DNSRR_RECORD.isEmpty() && DNSRR4_RECORD.isEmpty() && DNSRR6_RECORD.isEmpty());
	}
	
}
