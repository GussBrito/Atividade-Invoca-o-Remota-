package rr;

import java.io.*;
import java.net.InetAddress;

/**
 * Mensagem de requisição/resposta (slide 25).
 *
 * Formato no fio (big-endian, via DataOutputStream):
 *   int    messageType      0 = Request, 1 = Reply
 *   int    requestId
 *   byte[4|16] + int  remoteReference (tamanho do IP, bytes do IP, porta)
 *   int    operationId
 *   int    argumentsLength
 *   byte[] arguments
 */
public final class Message {
    public static final int REQUEST = 0;
    public static final int REPLY = 1;

    public final int messageType;
    public final int requestId;
    public final RemoteRef remoteReference;
    public final int operationId;
    public final byte[] arguments;

    // Preenchidos apenas no recebimento (origem do datagrama).
    public InetAddress senderHost;
    public int senderPort;

    public Message(int messageType, int requestId, RemoteRef ref, int operationId, byte[] arguments) {
        this.messageType = messageType;
        this.requestId = requestId;
        this.remoteReference = ref;
        this.operationId = operationId;
        this.arguments = arguments == null ? new byte[0] : arguments;
    }

    public byte[] marshal() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bos);
        out.writeInt(messageType);
        out.writeInt(requestId);
        byte[] ip = remoteReference.host.getAddress();
        out.writeInt(ip.length);
        out.write(ip);
        out.writeInt(remoteReference.port);
        out.writeInt(operationId);
        out.writeInt(arguments.length);
        out.write(arguments);
        out.flush();
        return bos.toByteArray();
    }

    public static Message unmarshal(byte[] data, int length) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data, 0, length));
        int type = in.readInt();
        int reqId = in.readInt();
        int ipLen = in.readInt();
        if (ipLen != 4 && ipLen != 16) throw new IOException("Mensagem mal formada: IP inválido");
        byte[] ip = new byte[ipLen];
        in.readFully(ip);
        int port = in.readInt();
        int op = in.readInt();
        int argLen = in.readInt();
        if (argLen < 0 || argLen > length) throw new IOException("Mensagem mal formada: tamanho de argumentos");
        byte[] args = new byte[argLen];
        in.readFully(args);
        return new Message(type, reqId, new RemoteRef(InetAddress.getByAddress(ip), port), op, args);
    }
}
