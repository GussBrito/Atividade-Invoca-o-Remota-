package rr;

import java.io.IOException;
import java.net.*;
import java.util.*;

/** Lado servidor do protocolo: implementa getRequest() e sendReply(). */
public class Server implements AutoCloseable {
    private final DatagramSocket socket;

    // Semântica "no máximo uma vez": guarda a última resposta por (cliente, requestId)
    // para reenviar sem reexecutar a operação quando chega uma retransmissão.
    private final Map<String, byte[]> replyCache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, byte[]> e) { return size() > 1000; }
    };

    public Server(int port) throws SocketException {
        this.socket = new DatagramSocket(port);
    }

    public int getPort() { return socket.getLocalPort(); }

    /**
     * Bloqueia até chegar uma requisição válida e a devolve (com origem preenchida).
     * Retransmissões já respondidas são atendidas pelo cache e não são devolvidas.
     */
    public Message getRequest() throws IOException {
        byte[] buf = new byte[Client.MAX_PACKET];
        while (true) {
            DatagramPacket in = new DatagramPacket(buf, buf.length);
            socket.receive(in);
            Message m;
            try {
                m = Message.unmarshal(in.getData(), in.getLength());
            } catch (IOException malformed) {
                continue; // descarta mensagem corrompida
            }
            if (m.messageType != Message.REQUEST) continue;
            m.senderHost = in.getAddress();
            m.senderPort = in.getPort();

            byte[] cached;
            synchronized (replyCache) { cached = replyCache.get(key(m)); }
            if (cached != null) { // duplicata: reenvia resposta guardada
                socket.send(new DatagramPacket(cached, cached.length, m.senderHost, m.senderPort));
                continue;
            }
            return m;
        }
    }

    /** Responde a uma requisição obtida por getRequest(). */
    public void sendReply(Message request, byte[] reply) throws IOException {
        Message r = new Message(Message.REPLY, request.requestId, request.remoteReference,
                request.operationId, reply);
        byte[] out = r.marshal();
        synchronized (replyCache) { replyCache.put(key(request), out); }
        socket.send(new DatagramPacket(out, out.length, request.senderHost, request.senderPort));
    }

    /** Forma da assinatura do slide: sendReply(byte[] reply, InetAddress clientHost, int clientPort). */
    public void sendReply(byte[] reply, InetAddress clientHost, int clientPort, int requestId) throws IOException {
        Message r = new Message(Message.REPLY, requestId, new RemoteRef(clientHost, clientPort), 0, reply);
        byte[] out = r.marshal();
        socket.send(new DatagramPacket(out, out.length, clientHost, clientPort));
    }

    private static String key(Message m) { return m.senderHost.getHostAddress() + ":" + m.senderPort + ":" + m.requestId; }

    @Override
    public void close() { socket.close(); }
}
