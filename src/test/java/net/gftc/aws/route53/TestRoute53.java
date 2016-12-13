package net.gftc.aws.route53;

import static org.junit.Assert.*;

import java.util.Objects;

import org.junit.Test;

import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.RRType;

public class TestRoute53 {
	
	static String testDomain = System.getenv("TEST_DOMAIN");
	static boolean disableLiveTests = Objects.nonNull(System.getenv("DISABLE_LIVE_TESTS"));

	@Test
	public void testAddA() {
		if (disableLiveTests) return;
		AmazonRoute53Client r53 = new AmazonRoute53Client(net.gftc.aws.Tools.getCreds());
		ChangeResourceRecordSetsRequest cr = Tools.getAndAddRecord(
				"test." + testDomain, 
				RRType.A, "5.6.7.8");
		Tools.waitFor(r53.changeResourceRecordSets(cr));
		assertTrue(true);
	}

	@Test
	public void testRemoveA() {
		if (disableLiveTests) return;
		AmazonRoute53Client r53 = new AmazonRoute53Client(net.gftc.aws.Tools.getCreds());
		ChangeResourceRecordSetsRequest cr = Tools.getAndRemoveRecord(
				"test." + testDomain, 
				RRType.A, "1.2.3.4");
		Tools.waitFor(r53.changeResourceRecordSets(cr));
		assertTrue(true);
	}

	@Test
	public void testAddSRV() {
		if (disableLiveTests) return;
		AmazonRoute53Client r53 = new AmazonRoute53Client(net.gftc.aws.Tools.getCreds());
		ChangeResourceRecordSetsRequest cr = Tools.getAndAddRecord(
				"_sip._udp.test." + testDomain, 
				RRType.SRV, "1 1 5060 test1." + testDomain);
		Tools.waitFor(r53.changeResourceRecordSets(cr));
		ChangeResourceRecordSetsRequest cr2 = Tools.getAndAddRecord(
				"_sip._udp.test." + testDomain, 
				RRType.SRV, "1 1 5060 test2." + testDomain);
		Tools.waitFor(r53.changeResourceRecordSets(cr2));
		assertTrue(true);
	}

	@Test
	public void testRemoveSRV() {
		if (disableLiveTests) return;
		AmazonRoute53Client r53 = new AmazonRoute53Client(net.gftc.aws.Tools.getCreds());
		ChangeResourceRecordSetsRequest cr = Tools.getAndRemoveRecord(
				"_sip._udp.test." + testDomain, 
				RRType.SRV, "1 1 5060 test1." + testDomain);
		Tools.waitFor(r53.changeResourceRecordSets(cr));
		assertTrue(true);
	}

}
