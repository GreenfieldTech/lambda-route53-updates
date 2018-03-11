package tech.greenfield.aws.route53;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Metadata {
	
	private Object SRV_RECORD;
	private Object DNSRR_RECORD;

	public List<String> getSRV_RECORD() {
		if(Objects.isNull(SRV_RECORD))
			return Collections.emptyList();
		return getListFromString(SRV_RECORD.toString());
	}
	
	public void setSRV_RECORD(Object sRV_RECORD) {
		SRV_RECORD = sRV_RECORD;
	}
	
	public List<String> getDNSRR_RECORD() {
		if(Objects.isNull(DNSRR_RECORD))
			return Collections.emptyList();
		return getListFromString(DNSRR_RECORD.toString());
	}
	public void setDNSRR_RECORD(Object dNSRR_RECORD) {
		DNSRR_RECORD = dNSRR_RECORD;
	}
	
	private static List<String> getListFromString(String str) {
		List<String> list = new ArrayList<>();
		if(str.startsWith("[")) {
			for(String item : str.substring(1, str.length()-1).split(",")) 
				list.add(item.replace(" ", ""));
		}
		else
			list.add(str);
		return list;
	}
	
}
