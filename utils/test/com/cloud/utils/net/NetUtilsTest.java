// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.utils.net;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import junit.framework.TestCase;

import org.junit.Test;

public class NetUtilsTest extends TestCase {

    @Test
    public void testGetRandomIpFromCidr() {
        String cidr = "192.168.124.1";
        long ip = NetUtils.getRandomIpFromCidr(cidr, 24, new TreeSet<Long>());
        assertEquals("The ip " + NetUtils.long2Ip(ip) + " retrieved must be within the cidr " + cidr + "/24", cidr.substring(0, 12), NetUtils.long2Ip(ip).substring(0, 12));

        ip = NetUtils.getRandomIpFromCidr(cidr, 16, new TreeSet<Long>());
        assertEquals("The ip " + NetUtils.long2Ip(ip) + " retrieved must be within the cidr " + cidr + "/16", cidr.substring(0, 8), NetUtils.long2Ip(ip).substring(0, 8));

        ip = NetUtils.getRandomIpFromCidr(cidr, 8, new TreeSet<Long>());
        assertEquals("The ip " + NetUtils.long2Ip(ip) + " retrieved must be within the cidr " + cidr + "/8", cidr.substring(0, 4), NetUtils.long2Ip(ip).substring(0, 4));

        SortedSet<Long> avoid = new TreeSet<Long>();
        ip = NetUtils.getRandomIpFromCidr(cidr, 30, avoid);
        assertTrue("We should be able to retrieve an ip on the first call.", ip != -1);
        avoid.add(ip);
        ip = NetUtils.getRandomIpFromCidr(cidr, 30, avoid);
        assertTrue("We should be able to retrieve an ip on the second call.", ip != -1);
        assertTrue("ip returned is not in the avoid list", !avoid.contains(ip));
        avoid.add(ip);
        ip = NetUtils.getRandomIpFromCidr(cidr, 30, avoid);
        assertTrue("We should be able to retrieve an ip on the third call.", ip != -1);
        assertTrue("ip returned is not in the avoid list", !avoid.contains(ip));
        avoid.add(ip);
       
        ip = NetUtils.getRandomIpFromCidr(cidr, 30, avoid);
        assertEquals("This should be -1 because we ran out of ip addresses: " + ip, ip, -1);
    }

    public void testVpnPolicy() {
        assertTrue(NetUtils.isValidS2SVpnPolicy("aes128-sha1"));
        assertTrue(NetUtils.isValidS2SVpnPolicy("3des-sha1"));
        assertTrue(NetUtils.isValidS2SVpnPolicy("3des-sha1,aes256-sha1"));
        assertTrue(NetUtils.isValidS2SVpnPolicy("3des-md5;modp1024"));
        assertTrue(NetUtils.isValidS2SVpnPolicy("3des-sha1,aes128-sha1;modp1536"));
        assertFalse(NetUtils.isValidS2SVpnPolicy("des-md5;modp1024,aes128-sha1;modp1536"));
        assertFalse(NetUtils.isValidS2SVpnPolicy("des-sha1"));
        assertFalse(NetUtils.isValidS2SVpnPolicy("abc-123,ase-sha1"));
        assertFalse(NetUtils.isValidS2SVpnPolicy("de-sh,aes-sha1"));
        assertFalse(NetUtils.isValidS2SVpnPolicy(""));
        assertFalse(NetUtils.isValidS2SVpnPolicy(";modp1536"));
        assertFalse(NetUtils.isValidS2SVpnPolicy(",aes;modp1536,,,"));
    }
}
