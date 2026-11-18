# Protocolo Requisição-Resposta sobre UDP (Java)

Atividade de **Sistemas Distribuídos – Invocação Remota de Métodos**.
Implementação do protocolo de requisição-resposta (Coulouris) com as três primitivas
`doOperation`, `getRequest` e `sendReply`, usando **sockets de datagrama (UDP)**.

## Estrutura

```
src/main/java/rr/
  RemoteRef.java       referência remota (IP + porta)
  Message.java         mensagem + marshal/unmarshal (formato do slide 25)
  Client.java          doOperation(RemoteRef, operationId, byte[] args)
  Server.java          getRequest() e sendReply(...)
  EchoServerApp.java   servidor de exemplo (ECHO, UPPER, SUM)
  ClientApp.java       cliente de exemplo (linha de comando)
src/test/java/rr/
  TestRunner.java      testes automatizados (sem dependências)
build.sh / run-tests.sh
```

## Requisitos

- JDK 11 ou superior (testado com JDK 21). Não há dependências externas.

## Como compilar

```bash
./build.sh
```

(equivale a `javac -encoding UTF-8 -d out src/main/java/rr/*.java src/test/java/rr/*.java`)

## Como instanciar a aplicação

**Terminal 1 – servidor** (porta padrão 5000):

```bash
java -cp out rr.EchoServerApp 5000
```

**Terminal 2 – cliente** (`host porta operationId argumentos...`):

```bash
java -cp out rr.ClientApp localhost 5000 2 "ola sistemas distribuidos"   # UPPER -> OLA SISTEMAS DISTRIBUIDOS
java -cp out rr.ClientApp localhost 5000 3 "40 2"                        # SUM   -> 42
java -cp out rr.ClientApp localhost 5000 1 "echo"                        # ECHO  -> echo
```

| operationId | Operação | Argumentos | Resposta |
|---|---|---|---|
| 1 | ECHO  | bytes quaisquer | os mesmos bytes |
| 2 | UPPER | texto UTF-8 | texto em maiúsculas |
| 3 | SUM   | 2 × int (big-endian) | 1 × int (soma) |

## Como executar os testes

```bash
./run-tests.sh
```

Testes automatizados (todos rodam em `localhost`):

| Teste | O que valida |
|---|---|
| marshal/unmarshal | todos os campos sobrevivem à serialização |
| mensagem corrompida | `unmarshal` rejeita dados mal formados |
| ECHO / UPPER / SUM | as 3 operações de ponta a ponta |
| args vazios e grandes (60 KB) | limites do datagrama |
| datagrama lixo | servidor descarta e continua operando |
| 10 clientes concorrentes | cada cliente recebe a *sua* resposta (casamento por `requestId`) |
| requisição perdida | cliente retransmite após timeout e a operação executa 1 vez |
| resposta perdida | retransmissão é respondida via cache, **sem reexecutar** a operação |
| servidor inexistente | `doOperation` lança `SocketTimeoutException` após as tentativas |

A perda de pacotes é simulada por um proxy UDP que descarta datagramas.

## Decisões de projeto

- **Garantias de entrega (slide 18):** como UDP não garante entrega, o protocolo faz isso:
  `doOperation` usa *timeout* (padrão 1 s) e *retransmissão* (padrão 3 tentativas) com o mesmo `requestId`.
- **Semântica "no máximo uma vez":** o servidor guarda a resposta por `(IP, porta, requestId)`;
  uma retransmissão recebe a resposta guardada sem reexecutar a operação.
- `doOperation` **bloqueia** até a resposta chegar (ou esgotar as tentativas); `getRequest` bloqueia até chegar uma requisição válida.
- Respostas com `requestId` diferente ou mensagens corrompidas são ignoradas.
- `getRequest()` devolve um objeto `Message` (e não só `byte[]`) porque o servidor precisa saber
  quem chamou e qual `requestId` responder. Também existe `sendReply(byte[], InetAddress, int, int)`
  próxima à assinatura do slide.

## Formato da mensagem no fio (para testar com a implementação de um colega)

Campos do slide 25, em **big-endian** (`DataOutputStream`), nesta ordem:

| Campo | Tipo | Observação |
|---|---|---|
| messageType | int (4 B) | 0 = Request, 1 = Reply |
| requestId | int (4 B) | identifica a requisição; a resposta repete o mesmo valor |
| remoteReference | int ipLen (4 ou 16) + ipLen bytes + int porta | |
| operationId | int (4 B) | |
| arguments | int tamanho + bytes | |

Para interoperar, o outro lado só precisa falar esse formato por UDP: se o seu servidor receber
uma requisição de outro cliente, responda para o IP/porta de **origem do datagrama** com
`messageType = 1` e o mesmo `requestId`.
