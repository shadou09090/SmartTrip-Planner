package com.project.back_end_proyecto.service;

import org.springframework.web.client.RestClientResponseException;

import javax.net.ssl.SSLException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.nio.channels.UnresolvedAddressException;
import java.util.ArrayList;
import java.util.List;

/** Safe provider diagnostics: exception classes and stages only, never exception messages or request headers. */
public final class ProviderFailureDiagnostics {
    private ProviderFailureDiagnostics() { }

    public static String stage(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof UnknownHostException || cause instanceof UnresolvedAddressException) return "dns";
            if (cause instanceof HttpConnectTimeoutException) return "connect_timeout";
            if (cause instanceof HttpTimeoutException || cause instanceof SocketTimeoutException) return "read_timeout";
            if (cause instanceof SSLException) return "tls";
            if (cause instanceof ConnectException) return "tcp_connect";
            if (cause == cause.getCause()) break;
        }
        if (failure instanceof RestClientResponseException response) return "http_" + response.getStatusCode().value();
        return "transport_or_client_error";
    }

    public static String exceptionTypes(Throwable failure) {
        List<String> types = new ArrayList<>();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            String name = cause.getClass().getSimpleName();
            if (types.contains(name)) break;
            types.add(name);
            if (cause == cause.getCause()) break;
        }
        return String.join(" -> ", types);
    }

    public static long elapsedMillis(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000L);
    }
}
