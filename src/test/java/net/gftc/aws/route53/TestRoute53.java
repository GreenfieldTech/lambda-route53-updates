package net.gftc.aws.route53;

import static org.junit.Assert.*;

import java.util.Objects;

import org.junit.Test;

import com.amazonaws.services.route53.AmazonRoute53Client;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.RRType;

/**
 * Test tool for Route53 work
 * @author odeda
 *
 *     Copyright (C) 2016  GreenfieldTech
 * 
 *     This library is free software; you can redistribute it and/or
 *     modify it under the terms of the GNU Lesser General Public
 *     License as published by the Free Software Foundation; either
 *     version 2.1 of the License, or (at your option) any later version.
 * 
 *     This library is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *     Lesser General Public License for more details.
 * 
 *     You should have received a copy of the GNU Lesser General Public
 *     License along with this library; if not, write to the Free Software
 *     Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
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
