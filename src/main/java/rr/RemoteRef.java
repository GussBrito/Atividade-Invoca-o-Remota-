package rr;

import java.net.InetAddress;

/** Referência para um servidor remoto: endereço IP + porta UDP. */
public final class RemoteRef {
    public final InetAddress host;
    public final int port;

    public RemoteRef(InetAddress host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String toString() {
        return host.getHostAddress() + ":" + port;
    }
}
