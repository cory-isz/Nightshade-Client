package com.nightshade.net;

import com.nightshade.util.Log;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Shared HTTPS setup for every web request the client makes (Modrinth, Microsoft sign-in, skins).
 *
 * <p>Java normally trusts only the certificate list shipped inside the JRE. Launcher-bundled runtimes can carry an
 * outdated list, and antivirus "HTTPS scanning" or company networks install their own root certificate into the
 * operating system only - both end in "PKIX path building failed". Browsers avoid this by using the OS trust store,
 * so this class does the same: a certificate is accepted when the JRE store <em>or</em> the operating system store
 * trusts it. Full chain validation and host-name checks still happen; nothing is ever trusted blindly.</p>
 */
public final class Http {
    private static volatile SSLContext context;
    private static volatile int trustSources;

    private Http() {
    }

    public static HttpClient.Builder client(Duration connectTimeout) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(connectTimeout);
        SSLContext ssl = sslContext();
        if (ssl != null) {
            builder.sslContext(ssl);
        }
        return builder;
    }

    /** Number of trust stores in use (JRE + OS stores that loaded); exposed for diagnostics and tests. */
    public static int trustSources() {
        sslContext();
        return trustSources;
    }

    private static SSLContext sslContext() {
        SSLContext ssl = context;
        if (ssl != null) {
            return ssl;
        }
        synchronized (Http.class) {
            if (context == null) {
                try {
                    List<X509ExtendedTrustManager> managers = new ArrayList<>();
                    addManager(managers, null); // the JRE's own cacerts
                    for (KeyStore store : osStores()) {
                        addManager(managers, store);
                    }
                    trustSources = managers.size();
                    SSLContext created = SSLContext.getInstance("TLS");
                    created.init(null, new TrustManager[]{new CompositeTrustManager(managers)}, null);
                    context = created;
                    Log.LOGGER.info("HTTPS trust: {} certificate store(s) in use", managers.size());
                } catch (Exception e) {
                    Log.LOGGER.warn("Could not set up OS certificate trust; using Java defaults", e);
                    return null;
                }
            }
            return context;
        }
    }

    private static void addManager(List<X509ExtendedTrustManager> out, KeyStore store) {
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(store);
            for (TrustManager manager : factory.getTrustManagers()) {
                // A store with no certificates cannot validate anything (and throws instead of rejecting).
                if (manager instanceof X509ExtendedTrustManager extended && extended.getAcceptedIssuers().length > 0) {
                    out.add(extended);
                }
            }
        } catch (Exception e) {
            Log.LOGGER.debug("Skipping certificate store {}", store == null ? "default" : store.getType(), e);
        }
    }

    private static List<KeyStore> osStores() {
        List<KeyStore> stores = new ArrayList<>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            loadNamed(stores, "Windows-ROOT");
        } else if (os.contains("mac")) {
            loadNamed(stores, "KeychainStore-ROOT"); // system roots (JDK 23+)
            loadNamed(stores, "KeychainStore");      // user-trusted certificates
        } else {
            KeyStore pem = loadPemBundles();
            if (pem != null) {
                stores.add(pem);
            }
        }
        return stores;
    }

    private static void loadNamed(List<KeyStore> out, String type) {
        try {
            KeyStore store = KeyStore.getInstance(type);
            store.load(null, null);
            if (store.size() > 0) {
                out.add(store);
            }
        } catch (Exception e) {
            Log.LOGGER.debug("Certificate store {} unavailable", type, e);
        }
    }

    private static KeyStore loadPemBundles() {
        String[] bundles = {"/etc/ssl/certs/ca-certificates.crt", "/etc/pki/tls/certs/ca-bundle.crt",
                "/etc/ssl/ca-bundle.pem", "/etc/pki/ca-trust/extracted/pem/tls-ca-bundle.pem", "/etc/ssl/cert.pem"};
        for (String bundle : bundles) {
            Path path = Path.of(bundle);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try (InputStream in = Files.newInputStream(path)) {
                KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
                store.load(null, null);
                int i = 0;
                for (var cert : CertificateFactory.getInstance("X.509").generateCertificates(in)) {
                    store.setCertificateEntry("os-" + (i++), cert);
                }
                if (i > 0) {
                    return store;
                }
            } catch (Exception e) {
                Log.LOGGER.debug("Could not read {}", bundle, e);
            }
        }
        return null;
    }

    /** A short, human-readable explanation of a network failure (shown in the GUI instead of raw exception text). */
    public static String describe(Throwable error) {
        Set<Throwable> seen = new LinkedHashSet<>();
        for (Throwable t = error; t != null && seen.add(t); t = t.getCause()) {
            if (t instanceof CertPathBuilderException || t instanceof CertPathValidatorException
                    || (t instanceof SSLHandshakeException && String.valueOf(t.getMessage()).contains("PKIX"))) {
                return "Secure connection blocked: the certificate is not trusted. Antivirus HTTPS scanning, a "
                        + "company network or an outdated Java runtime is usually the cause.";
            }
            if (t instanceof UnknownHostException) {
                return "No internet connection (could not resolve " + t.getMessage() + ")";
            }
            if (t instanceof HttpTimeoutException) {
                return "The server took too long to respond. Try again.";
            }
            if (t instanceof ConnectException) {
                return "Could not connect. Check your internet connection or firewall.";
            }
        }
        String message = error.getMessage();
        return message == null || message.isBlank() ? error.getClass().getSimpleName() : message;
    }

    /** Accepts a chain when any delegate trusts it; reports the first delegate's error otherwise. */
    private static final class CompositeTrustManager extends X509ExtendedTrustManager {
        private final List<X509ExtendedTrustManager> delegates;

        CompositeTrustManager(List<X509ExtendedTrustManager> delegates) {
            this.delegates = List.copyOf(delegates);
        }

        private interface Check {
            void run(X509ExtendedTrustManager manager) throws CertificateException;
        }

        private void any(Check check) throws CertificateException {
            CertificateException first = null;
            for (X509ExtendedTrustManager manager : delegates) {
                try {
                    check.run(manager);
                    return;
                } catch (CertificateException e) {
                    if (first == null) {
                        first = e;
                    }
                } catch (RuntimeException e) {
                    if (first == null) {
                        first = new CertificateException(e.getMessage(), e);
                    }
                }
            }
            throw first != null ? first : new CertificateException("No trust managers available");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            any(m -> m.checkServerTrusted(chain, authType, socket));
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            any(m -> m.checkServerTrusted(chain, authType, engine));
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            any(m -> m.checkServerTrusted(chain, authType));
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
            any(m -> m.checkClientTrusted(chain, authType, socket));
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) throws CertificateException {
            any(m -> m.checkClientTrusted(chain, authType, engine));
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            any(m -> m.checkClientTrusted(chain, authType));
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> all = new ArrayList<>();
            for (X509TrustManager manager : delegates) {
                all.addAll(List.of(manager.getAcceptedIssuers()));
            }
            return all.toArray(new X509Certificate[0]);
        }
    }
}
