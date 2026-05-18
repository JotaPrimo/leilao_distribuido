# Leilao Distribuido

Sistema de leilao distribuido em Java usando TCP Sockets puros, terminal e mensagens delimitadas por `\n`.

## Requisitos

- Java 11 ou superior
- Porta padrao: `12345`
- Maximo de clientes simultaneos: `5`

## Como compilar

No PowerShell:

```powershell
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src/main/java -Filter *.java).FullName
```

Em bash:

```bash
find src/main/java -name "*.java" | xargs javac -encoding UTF-8 -d out
```

## Como rodar

Servidor:

```bash
java -cp out server.AuctionServer
```

Cliente:

```bash
java -cp out client.AuctionClient localhost 12345
```

No cliente, informe um nome alfanumerico com ate 20 caracteres. Depois do login, digite apenas o valor numerico do lance. O cliente envia `BID <valor>` ao servidor.

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

## Concorrencia

Os lances sao processados em `AuctionManager.placeBid()` com `ReentrantLock`. A secao critica valida se o leilao ainda esta ativo, compara o valor recebido com o maior lance atual, atualiza `currentBid` e `currentWinner`, envia o `UPDATE` para todos os clientes e persiste o estado em arquivo.

Esse lock evita que dois clientes validem lances simultaneamente sobre o mesmo valor anterior. O broadcast de `UPDATE` acontece dentro do lock, como pedido no plano, garantindo que os clientes vejam os lances na mesma ordem em que o estado foi gravado.

## Tolerancia a falhas

O arquivo `auction_state.json` e salvo apos cada lance aceito e ao encerrar o leilao. Ao iniciar o servidor, se esse arquivo existir, o terminal pergunta:

```text
Leilao anterior encontrado. Continuar? (s/n):
```

Respondendo `s`, o servidor restaura o maior lance, o vencedor atual e o tempo restante. Clientes que desconectam podem reconectar com o mesmo nome depois que a conexao antiga cair; ao fazer login novamente, recebem `START` e o `UPDATE` atual.

## Configuracao hardcoded

- Item: `Notebook_Dell`
- Lance minimo: `1500.00`
- Duracao: `60` segundos
- Arquivo de persistencia: `auction_state.json`
