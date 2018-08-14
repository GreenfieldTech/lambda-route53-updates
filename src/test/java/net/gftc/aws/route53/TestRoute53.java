package net.gftc.aws.route53;

import static org.junit.Assert.*;
import static tech.greenfield.aws.Clients.route53;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.AbstractMap.SimpleEntry;

import org.junit.Test;

import com.amazonaws.services.route53.model.ChangeBatch;
import com.amazonaws.services.route53.model.ChangeResourceRecordSetsRequest;
import com.amazonaws.services.route53.model.RRType;

import tech.greenfield.aws.route53.Route53Message;
import tech.greenfield.aws.route53.Tools;

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

}
