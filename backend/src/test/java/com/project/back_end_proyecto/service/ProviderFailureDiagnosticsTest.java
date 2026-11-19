package com.project.back_end_proyecto.service;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLHandshakeException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProviderFailureDiagnosticsTest {
    @Test
    void classifiesNetworkStagesWithoutInspectingSensitiveExceptionMessages() {
        assertEquals("dns", ProviderFailureDiagnostics.stage(new UnknownHostException("private host detail")));
        assertEquals("dns", ProviderFailureDiagnostics.stage(new UnresolvedAddressException()));
        assertEquals("connect_timeout", ProviderFailureDiagnostics.stage(new HttpConnectTimeoutException("timeout")));
        assertEquals("read_timeout", ProviderFailureDiagnostics.stage(new HttpTimeoutException("timeout")));
        assertEquals("tls", ProviderFailureDiagnostics.stage(new SSLHandshakeException("handshake")));
        assertEquals("tcp_connect", ProviderFailureDiagnostics.stage(new ConnectException("refused")));
    }

    @Test
    void reportsExceptionTypesButNeverExceptionMessages() {
        String diagnostics = ProviderFailureDiagnostics.exceptionTypes(
                new org.springframework.web.client.ResourceAccessException("secret-like message", new UnknownHostException("private")));
        assertEquals("ResourceAccessException -> UnknownHostException", diagnostics);
        org.junit.jupiter.api.Assertions.assertFalse(diagnostics.contains("private"));
        org.junit.jupiter.api.Assertions.assertFalse(diagnostics.contains("secret"));
    }
}
