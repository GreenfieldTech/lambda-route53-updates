package tech.greenfield.aws.route53;

import java.util.List;
import java.util.stream.Collectors;

import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.route53.model.InvalidInputException;
import software.amazon.awssdk.services.route53.model.ResourceRecord;

public class SRVTemplate {
	String prio;
	String weight;
	String port;
	String addr;
	
	public SRVTemplate(String rec) {
		String[] fields = rec.split(":");
		if (fields.length != 4)
			throw InvalidInputException.builder().message("SRV template '" + rec + "' is invalid").build();
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
		return ResourceRecord.builder().value(String.join(" ", new String[]{
				prio, weight, port, ip
			})).build();
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