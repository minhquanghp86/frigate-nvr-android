package com.minhquanghp86.frigatenvr.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SecurityTest {

    @Test
    fun private_hosts_allow_http() {
        assertTrue(FrigateNet.isPrivateHost("192.168.10.2"))
        assertTrue(FrigateNet.isPrivateHost("10.0.0.5"))
        assertTrue(FrigateNet.isPrivateHost("172.16.0.1"))
        assertTrue(FrigateNet.isPrivateHost("172.31.255.255"))
        assertTrue(FrigateNet.isPrivateHost("localhost"))
        assertTrue(FrigateNet.isPrivateHost("frigate.local"))
        // Tailscale CGNAT 100.64.0.0/10
        assertTrue(FrigateNet.isPrivateHost("100.64.0.1"))
        assertTrue(FrigateNet.isPrivateHost("100.127.255.254"))
        // IPv6 ULA fc00::/7
        assertTrue(FrigateNet.isPrivateHost("fd12:3456::1"))
        assertTrue(FrigateNet.isPrivateHost("fc00::1"))
    }

    @Test
    fun public_hosts_reject_http() {
        assertFalse(FrigateNet.isPrivateHost("8.8.8.8"))
        assertFalse(FrigateNet.isPrivateHost("example.com"))
        assertFalse(FrigateNet.isPrivateHost("172.32.0.1"))
        assertFalse(FrigateNet.isPrivateHost("100.63.255.255"))
        assertFalse(FrigateNet.isPrivateHost("100.128.0.1"))
        assertFalse(FrigateNet.isPrivateHost("192.169.1.1"))
        assertThrows(IllegalArgumentException::class.java) {
            FrigateNet.requireHttpsForPublicHost("http://example.com:8971")
        }
        assertThrows(IllegalArgumentException::class.java) {
            FrigateNet.requireHttpsForPublicHost("http://8.8.8.8:8971")
        }
    }

    @Test
    fun private_http_and_public_https_pass() {
        // Không ném exception
        FrigateNet.requireHttpsForPublicHost("http://192.168.10.2:8971")
        FrigateNet.requireHttpsForPublicHost("https://example.com:8971")
        FrigateNet.requireHttpsForPublicHost("https://demo.frigate.video")
    }
}
