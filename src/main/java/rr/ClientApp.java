package rr;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/** Cliente de exemplo. Uso: java rr.ClientApp [host=localhost] [porta=5000] [operationId=2] [texto...] */
public class ClientApp {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 5000;
        int op = args.length > 2 ? Integer.parseInt(args[2]) : 2;
        String text = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "ola sistemas distribuidos";

        RemoteRef server = new RemoteRef(InetAddress.getByName(host), port);
        try (Client c = new Client()) {
            byte[] payload;
            if (op == 3) {
                String[] n = text.split("\\s+");
                payload = ByteBuffer.allocate(8).putInt(Integer.parseInt(n[0])).putInt(Integer.parseInt(n[1])).array();
            } else {
                payload = text.getBytes(StandardCharsets.UTF_8);
            }
            byte[] reply = c.doOperation(server, op, payload);
            System.out.println(op == 3 ? "Resposta: " + ByteBuffer.wrap(reply).getInt()
                                       : "Resposta: " + new String(reply, StandardCharsets.UTF_8));
        }
    }
}
