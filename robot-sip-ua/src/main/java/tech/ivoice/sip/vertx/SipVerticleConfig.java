package tech.ivoice.sip.vertx;

public class SipVerticleConfig {
    private final String host;
    private final int port;
    private final String transport;

    public SipVerticleConfig(String host, int port, String transport) {
        this.host = host;
        this.port = port;
        this.transport = transport;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getTransport() {
        return transport;
    }

    public String getHostPort() {
        return host + ":" + port;
    }

    @Override
    public String toString() {
        return "SipVerticleConfig{" +
            "host='" + host + '\'' +
            ", port=" + port +
            ", transport='" + transport + '\'' +
            '}';
    }
}
