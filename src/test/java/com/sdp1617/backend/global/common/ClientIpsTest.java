package com.sdp1617.backend.global.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientIpsTest {

    @Test
    void IPv4는_그대로_쓴다() {
        assertEquals("203.0.113.7", ClientIps.rateLimitKey("203.0.113.7"));
    }

    @Test
    void IPv6는_64비트_대역으로_묶는다() {
        String a = ClientIps.rateLimitKey("2001:db8:1234:5678:aaaa:bbbb:cccc:dddd");
        String b = ClientIps.rateLimitKey("2001:db8:1234:5678::1");
        String otherNetwork = ClientIps.rateLimitKey("2001:db8:1234:9999::1");

        assertEquals(a, b);
        assertTrue(a.endsWith("/64"));
        assertNotEquals(a, otherNetwork);
    }
}
