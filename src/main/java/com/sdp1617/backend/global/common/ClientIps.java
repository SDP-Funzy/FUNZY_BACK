package com.sdp1617.backend.global.common;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * IP 기준 제한(로그인 잠금, 메일/인증번호 rate limit)에 쓰는 키를 만든다.
 * IPv6는 한 사용자가 /64 대역 전체를 쓸 수 있어, 주소 하나하나로 세면 주소만 바꿔 제한을 우회할 수 있다.
 * 그래서 IPv6는 /64 대역 단위로 묶고, IPv4는 그대로 쓴다.
 */
public final class ClientIps {

    private ClientIps() {
    }

    /** remoteAddr는 항상 IP 리터럴이라 DNS 조회가 일어나지 않는다. 해석할 수 없는 값은 그대로 돌려준다. */
    public static String rateLimitKey(String clientIp) {
        if (clientIp == null || !clientIp.contains(":")) {
            return clientIp;
        }
        try {
            InetAddress address = InetAddress.getByName(clientIp);
            if (address instanceof Inet6Address) {
                byte[] network = Arrays.copyOf(Arrays.copyOf(address.getAddress(), 8), 16);
                return InetAddress.getByAddress(network).getHostAddress() + "/64";
            }
            return address.getHostAddress();
        } catch (UnknownHostException exception) {
            return clientIp;
        }
    }
}
