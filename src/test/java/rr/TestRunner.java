package rr;

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Testes sem dependências externas. Uso: java rr.TestRunner (retorna exit code 1 se algo falhar). */
public class TestRunner {
    static int passed = 0, failed = 0;

    interface T { void run() throws Exception; }

    static void test(String name, T t) {
        try { t.run(); passed++; System.out.println("  OK    " + name); }
        catch (Throwable e) { failed++; System.out.println("  FALHA " + name + " -> " + e); }
    }

    static void check(boolean c, String msg) { if (!c) throw new AssertionError(msg); }

    static RemoteRef ref(int port) throws Exception { return new RemoteRef(InetAddress.getLoopbackAddress(), port); }

    /** Sobe um servidor em thread própria; conta quantas vezes cada operação foi executada. */
    static Thread serve(Server s, AtomicInteger executions) {
        Thread t = new Thread(() -> {
            try {
                while (true) {
                    Message m = s.getRequest();
                    executions.incrementAndGet();
                    s.sendReply(m, EchoServerApp.execute(m.operationId, m.arguments));
                }
            } catch (Exception e) { /* socket fechado */ }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** Proxy UDP que descarta as N primeiras requisições e/ou as N primeiras respostas. */
    static DatagramSocket lossyProxy(int serverPort, int dropRequests, int dropReplies) throws Exception {
        DatagramSocket p = new DatagramSocket(0, InetAddress.getLoopbackAddress());
        AtomicInteger dr = new AtomicInteger(dropRequests), dp = new AtomicInteger(dropReplies);
        Thread t = new Thread(() -> {
            byte[] buf = new byte[65507];
            SocketAddress client = null;
            try {
                while (true) {
                    DatagramPacket in = new DatagramPacket(buf, buf.length);
                    p.receive(in);
                    byte[] data = Arrays.copyOf(in.getData(), in.getLength());
                    if (in.getPort() == serverPort) { // resposta
                        if (dp.getAndDecrement() > 0) continue;
                        p.send(new DatagramPacket(data, data.length, client));
                    } else {                          // requisição
                        client = in.getSocketAddress();
                        if (dr.getAndDecrement() > 0) continue;
                        p.send(new DatagramPacket(data, data.length, InetAddress.getLoopbackAddress(), serverPort));
                    }
                }
            } catch (Exception e) { /* fechado */ }
        });
        t.setDaemon(true);
        t.start();
        return p;
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Executando testes...");

        test("marshal/unmarshal preserva todos os campos", () -> {
            Message m = new Message(Message.REQUEST, 42, ref(1234), 7, new byte[]{1, 2, 3});
            Message r = Message.unmarshal(m.marshal(), m.marshal().length);
            check(r.messageType == 0 && r.requestId == 42 && r.operationId == 7, "campos int");
            check(r.remoteReference.port == 1234, "porta");
            check(Arrays.equals(r.arguments, new byte[]{1, 2, 3}), "argumentos");
        });

        test("mensagem corrompida lança IOException", () -> {
            try { Message.unmarshal(new byte[]{0, 0, 0}, 3); check(false, "deveria falhar"); }
            catch (java.io.IOException expected) { }
        });

        try (Server s = new Server(0); Client c = new Client()) {
            AtomicInteger ex = new AtomicInteger();
            serve(s, ex);
            RemoteRef r = ref(s.getPort());

            test("ECHO devolve os mesmos bytes", () -> {
                byte[] reply = c.doOperation(r, 1, "abc".getBytes());
                check(new String(reply).equals("abc"), "echo");
            });
            test("UPPER converte texto (UTF-8)", () -> {
                byte[] reply = c.doOperation(r, 2, "olá mundo".getBytes(StandardCharsets.UTF_8));
                check(new String(reply, StandardCharsets.UTF_8).equals("OLÁ MUNDO"), "upper");
            });
            test("SUM soma dois inteiros", () -> {
                byte[] a = ByteBuffer.allocate(8).putInt(40).putInt(2).array();
                check(ByteBuffer.wrap(c.doOperation(r, 3, a)).getInt() == 42, "sum");
            });
            test("argumentos vazios e grandes (60 KB)", () -> {
                check(c.doOperation(r, 1, new byte[0]).length == 0, "vazio");
                byte[] big = new byte[60000]; new Random(1).nextBytes(big);
                check(Arrays.equals(c.doOperation(r, 1, big), big), "grande");
            });
            test("datagrama lixo é ignorado e servidor continua funcionando", () -> {
                try (DatagramSocket junk = new DatagramSocket()) {
                    byte[] g = {9, 9, 9};
                    junk.send(new DatagramPacket(g, g.length, InetAddress.getLoopbackAddress(), s.getPort()));
                }
                check(new String(c.doOperation(r, 1, "ok".getBytes())).equals("ok"), "após lixo");
            });
            test("10 clientes concorrentes recebem a resposta correta", () -> {
                ExecutorService pool = Executors.newFixedThreadPool(10);
                List<Future<Boolean>> fs = new ArrayList<>();
                for (int i = 0; i < 10; i++) {
                    final String msg = "cliente-" + i;
                    fs.add(pool.submit(() -> {
                        try (Client cc = new Client()) {
                            return new String(cc.doOperation(r, 1, msg.getBytes())).equals(msg);
                        }
                    }));
                }
                for (Future<Boolean> f : fs) check(f.get(10, TimeUnit.SECONDS), "resposta trocada");
                pool.shutdown();
            });
        }

        test("requisição perdida: cliente retransmite e recebe resposta", () -> {
            try (Server s = new Server(0); Client c = new Client(300, 4)) {
                AtomicInteger ex = new AtomicInteger(); serve(s, ex);
                DatagramSocket p = lossyProxy(s.getPort(), 2, 0);
                byte[] reply = c.doOperation(ref(p.getLocalPort()), 1, "x".getBytes());
                check(new String(reply).equals("x"), "resposta");
                check(ex.get() == 1, "executou " + ex.get() + "x");
                p.close();
            }
        });

        test("resposta perdida: duplicata NÃO reexecuta a operação (no máximo uma vez)", () -> {
            try (Server s = new Server(0); Client c = new Client(300, 4)) {
                AtomicInteger ex = new AtomicInteger(); serve(s, ex);
                DatagramSocket p = lossyProxy(s.getPort(), 0, 2);
                byte[] reply = c.doOperation(ref(p.getLocalPort()), 1, "y".getBytes());
                check(new String(reply).equals("y"), "resposta");
                check(ex.get() == 1, "executou " + ex.get() + "x (esperado 1)");
                p.close();
            }
        });

        test("servidor inexistente: doOperation lança SocketTimeoutException", () -> {
            try (Client c = new Client(150, 2)) {
                long t0 = System.currentTimeMillis();
                try { c.doOperation(ref(9), 1, new byte[0]); check(false, "deveria dar timeout"); }
                catch (SocketTimeoutException expected) { }
                check(System.currentTimeMillis() - t0 >= 250, "não esperou as tentativas");
            }
        });

        System.out.printf("%nResultado: %d passaram, %d falharam%n", passed, failed);
        System.exit(failed == 0 ? 0 : 1);
    }
}
