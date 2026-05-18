# Leilao Distribuido

Sistema de leilao distribuido em Java usando TCP sockets, terminal e mensagens de texto separadas por quebra de linha.

## Requisitos

- Java JDK 21 instalado.
- Terminal PowerShell, Prompt de Comando ou bash.
- Porta livre para o servidor. A porta padrao e `12345`.
- Maximo de clientes simultaneos: `5`.

Para conferir o Java:

```powershell
java -version
javac -version
```

O projeto tambem possui `pom.xml`, mas nesta maquina o comando `mvn` nao esta instalado no PATH. Se acontecer o erro `mvn : O termo 'mvn' nao e reconhecido`, use os comandos com `javac` abaixo.

## Compilar

No PowerShell, a partir da pasta raiz do projeto:

```powershell
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src/main/java -Filter *.java).FullName
```

Em bash:

```bash
find src/main/java -name "*.java" | xargs javac -encoding UTF-8 -d out
```

Se a compilacao terminar sem mensagens de erro, as classes ficam na pasta `out`.

## Rodar o servidor

Abra um terminal na raiz do projeto e execute:

```powershell
java -cp out server.AuctionServer
```

Para usar outra porta:

```powershell
java -cp out server.AuctionServer 23456
```

Deixe esse terminal aberto. Ele e o servidor.

Se aparecer:

```text
Leilao anterior encontrado. Continuar? (s/n):
```

Digite:

- `s` para restaurar o estado salvo em `auction_state.json`.
- `n` para iniciar um novo leilao.

Para forcar um leilao limpo, apague o arquivo antes de iniciar:

```powershell
Remove-Item auction_state.json -ErrorAction SilentlyContinue
```

## Rodar clientes

Abra outro terminal para cada cliente. Antes, compile o projeto se ainda nao compilou.

Cliente na porta padrao:

```powershell
java -cp out client.AuctionClient localhost 12345
```

Cliente em porta customizada:

```powershell
java -cp out client.AuctionClient localhost 23456
```

Fluxo esperado:

1. O cliente pede `Usuario:`.
2. Digite um nome alfanumerico com ate 20 caracteres, por exemplo `Alice`.
3. Depois do login, digite apenas o valor do lance, por exemplo `1600`.
4. Para sair do cliente, digite `sair`.

Use nomes diferentes para clientes conectados ao mesmo tempo. Se dois clientes ativos usarem o mesmo nome, o servidor responde `ERR_LOGIN nome_duplicado`.

## Exemplo rapido com 2 clientes

Terminal 1:

```powershell
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src/main/java -Filter *.java).FullName
java -cp out server.AuctionServer
```

Terminal 2:

```powershell
java -cp out client.AuctionClient localhost 12345
```

Digite:

```text
Usuario: Alice
1600
```

Terminal 3:

```powershell
java -cp out client.AuctionClient localhost 12345
```

Digite:

```text
Usuario: Bob
1700
```

Os clientes devem receber mensagens como `START`, `UPDATE`, `OK_BID`, `ERR_BID` e, ao final do tempo, `END`.

## Rodar os testes

Para testes manuais pelo terminal, siga o roteiro em [roteiro_testes_manuais.md](roteiro_testes_manuais.md).

### Com Maven

Se o Maven estiver instalado:

```powershell
mvn test
```

### Sem Maven

Baixe o JUnit standalone e rode os testes manualmente:

```powershell
$jar = "$env:TEMP\junit-platform-console-standalone-1.10.2.jar"
Invoke-WebRequest -Uri "https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.2/junit-platform-console-standalone-1.10.2.jar" -OutFile $jar

javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src/main/java -Filter *.java).FullName
New-Item -ItemType Directory -Force -Path test-out | Out-Null
javac -encoding UTF-8 -cp "out;$jar" -d test-out (Get-ChildItem -Recurse src/test/java -Filter *.java).FullName
java -jar $jar --class-path "out;test-out" --scan-class-path
```

Resultado esperado atualmente:

```text
49 tests successful
0 tests failed
```

## Protocolo

Cliente para servidor:

```text
LOGIN <nome_do_usuario>
BID <valor_float>
```

Servidor para cliente:

```text
OK_LOGIN
ERR_LOGIN <mensagem_de_erro>
OK_BID
ERR_BID <motivo>
```

Broadcast do servidor:

```text
START <nome_do_item> <valor_minimo_float> <tempo_em_segundos>
UPDATE <nome_do_usuario_vencedor> <novo_valor_float>
END <nome_do_usuario_vencedor> <valor_final>
END NINGUEM 0.00
```

## Funcionamento interno

- `server.AuctionServer` abre a porta TCP e aceita clientes.
- `server.ClientHandler` trata uma conexao de cliente por thread.
- `server.AuctionManager` guarda o estado do leilao, valida lances, controla concorrencia e envia broadcasts.
- `server.PersistenceManager` salva e restaura `auction_state.json`.
- `protocol.MessageParser` valida comandos `LOGIN` e `BID`.
- `client.AuctionClient` conecta no servidor e inicia o cliente de terminal.
- `client.ServerListener` escuta mensagens do servidor em uma thread separada.
- `client.ConsoleUI` le valores digitados e envia comandos `BID`.

## Configuracao padrao

- Item: `Notebook_Dell`
- Lance minimo inicial: `1500.00`
- Duracao: `300` segundos, ou 5 minutos
- Porta padrao: `12345`
- Arquivo de persistencia: `auction_state.json`

## Problemas comuns

Erro `mvn nao e reconhecido`:

Use os comandos com `javac`, ou instale o Maven e adicione ao PATH.

Erro `Could not find or load main class server.AuctionServer`:

Compile antes com `javac -encoding UTF-8 -d out ...` e execute a partir da raiz do projeto.

Erro de porta em uso:

Use outra porta:

```powershell
java -cp out server.AuctionServer 23456
java -cp out client.AuctionClient localhost 23456
```

Cliente nao consegue dar lance:

Confira se o servidor esta aberto, se o cliente fez login com nome valido e se o lance e maior que o valor atual.

Lance `1500` e rejeitado:

Isso e esperado. O primeiro lance precisa ser maior que `1500.00`, por exemplo `1500.01` ou `1600`.
