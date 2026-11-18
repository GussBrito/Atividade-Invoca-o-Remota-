package rr;

import java.nio.charset.StandardCharsets;

/**
 * Servidor de exemplo. Operações:
 *   1 = ECHO     (devolve os argumentos)
 *   2 = UPPER    (texto em maiúsculas)
 *   3 = SUM      (soma dois ints; args = 2 x int big-endian)
 * Uso: java rr.EchoServerApp [porta=5000]
 */
public class EchoServerApp {
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5000;
        try (Server server = new Server(port)) {
            System.out.println("Servidor escutando na porta UDP " + server.getPort());
            while (true) {
                Message req = server.getRequest();
                System.out.printf("[req %d] op=%d de %s:%d (%d bytes)%n",
                        req.requestId, req.operationId, req.senderHost.getHostAddress(),
                        req.senderPort, req.arguments.length);
                server.sendReply(req, execute(req.operationId, req.arguments));
            }
        }
    }

    public static byte[] execute(int op, byte[] a) {
        switch (op) {
            case 1: return a;
            case 2: return new String(a, StandardCharsets.UTF_8).toUpperCase().getBytes(StandardCharsets.UTF_8);
            case 3: {
                java.nio.ByteBuffer b = java.nio.ByteBuffer.wrap(a);
                return java.nio.ByteBuffer.allocate(4).putInt(b.getInt() + b.getInt()).array();
            }
            default: return "ERRO: operação desconhecida".getBytes(StandardCharsets.UTF_8);
        }
    }
}
