package tech.greenfield.aws.route53;

import java.util.List;
import java.util.stream.Collectors;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.route53.model.InvalidInputException;
import com.amazonaws.services.route53.model.ResourceRecord;

public class SRVTemplate {
	String prio;
	String weight;
	String port;
	String addr;
	
	public SRVTemplate(String rec) {
		String[] fields = rec.split(":");
		if (fields.length != 4)
			throw new InvalidInputException("SRV template '" + rec + "' is invalid");
		prio = fields[0];
		weight = fields[1];
		port = fields[2];
		addr = fields[3];
		if (!addr.endsWith("."))
			addr += ".";
	}
	public static List<SRVTemplate> parse(List<String> record) {
		return record.stream().map(SRVTemplate::new).collect(Collectors.toList());
	}
	public ResourceRecord getResourceRecord(Instance i) throws NoIpException {
		return getResourceRecord(Tools.getHostAddress(i));
	}
	public ResourceRecord getResourceRecord(String ip) {
		return new ResourceRecord(String.join(" ", new String[]{
				prio, weight, port, ip
			}));
	}
	public String toString() {
		return String.join(":", new String[] {
				prio, weight, port, addr
		});
	}
	public String getAddr() {
		return addr;
	}
}