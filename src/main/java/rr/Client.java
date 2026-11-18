package rr;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Lado cliente do protocolo: implementa doOperation(). */
public class Client implements AutoCloseable {
    public static final int MAX_PACKET = 65507;

    private final DatagramSocket socket;
    private final AtomicInteger nextRequestId = new AtomicInteger(1);
    private final int timeoutMs;
    private final int maxRetries;

    public Client() throws SocketException { this(1000, 3); }

    public Client(int timeoutMs, int maxRetries) throws SocketException {
        this.socket = new DatagramSocket();
        this.timeoutMs = timeoutMs;
        this.maxRetries = maxRetries;
    }

    /**
     * Envia a requisição ao servidor e BLOQUEIA até receber a resposta.
     * Como UDP não garante entrega, o próprio protocolo usa timeout +
     * retransmissão (mesmo requestId, para o servidor filtrar duplicatas).
     */
    public byte[] doOperation(RemoteRef s, int operationId, byte[] arguments) throws IOException {
        int requestId = nextRequestId.getAndIncrement();
        Message req = new Message(Message.REQUEST, requestId, s, operationId, arguments);
        byte[] out = req.marshal();
        DatagramPacket packet = new DatagramPacket(out, out.length, s.host, s.port);
        byte[] buf = new byte[MAX_PACKET];
        socket.setSoTimeout(timeoutMs);

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            socket.send(packet);
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (true) {
                int remaining = (int) (deadline - System.currentTimeMillis());
                if (remaining <= 0) break;
                socket.setSoTimeout(remaining);
                DatagramPacket in = new DatagramPacket(buf, buf.length);
                try {
                    socket.receive(in);
                } catch (SocketTimeoutException e) {
                    break; // retransmite
                }
                try {
                    Message reply = Message.unmarshal(in.getData(), in.getLength());
                    if (reply.messageType == Message.REPLY && reply.requestId == requestId) {
                        return reply.arguments;
                    }
                    // resposta atrasada de outra requisição: ignora
                } catch (IOException malformed) {
                    // mensagem corrompida: ignora e continua esperando
                }
            }
        }
        throw new SocketTimeoutException("Sem resposta de " + s + " após " + maxRetries + " tentativas");
    }

    @Override
    public void close() { socket.close(); }
}
