# Roteiro de Testes Manuais - Leilao Distribuido

Este roteiro serve para testar o funcionamento do sistema pelo terminal, como um usuario real.

## Preparacao

Abra o PowerShell na raiz do projeto:

```powershell
cd C:\projetos\edinaldo\leilao_distruibuido
```

Limpe estado antigo para o leilao nao terminar rapido por restauracao:

```powershell
Remove-Item auction_state.json -ErrorAction SilentlyContinue
```

Compile:

```powershell
javac -encoding UTF-8 -d out (Get-ChildItem -Recurse src/main/java -Filter *.java).FullName
```

Suba o servidor no Terminal 1:

```powershell
java -cp out server.AuctionServer
```

Resultado esperado no servidor:

```text
SERVER: Novo leilao iniciado
SERVER: Escutando na porta 12345
```

Observacao: o leilao dura 300 segundos por padrao, ou 5 minutos. Se ele terminar antes disso, provavelmente foi restaurado de um `auction_state.json` antigo.

## TC-01 - Login valido

Objetivo: validar que um cliente consegue entrar no leilao.

No Terminal 2:

```powershell
java -cp out client.AuctionClient localhost 12345
```

Digite:

```text
Alice
```

Resultado esperado no cliente:

```text
OK_LOGIN
START Notebook_Dell 1500.00 <tempo_restante>
```

Resultado esperado no servidor:

```text
CLIENT Alice: LOGIN
```

## TC-02 - Primeiro lance valido

Objetivo: validar que o primeiro lance acima do minimo e aceito.

No cliente da Alice, digite:

```text
1600
```

Resultado esperado no cliente:

```text
UPDATE Alice 1600.00
OK_BID
```

A ordem pode aparecer como `UPDATE` antes de `OK_BID`, porque o servidor primeiro faz broadcast do novo lance e depois responde ao cliente que enviou o lance.

Resultado esperado no servidor:

```text
CLIENT Alice: BID 1600.00
```

## TC-03 - Segundo cliente entra e recebe estado atual

Objetivo: validar que um cliente novo recebe o leilao em andamento.

No Terminal 3:

```powershell
java -cp out client.AuctionClient localhost 12345
```

Digite:

```text
Bob
```

Resultado esperado no cliente Bob:

```text
OK_LOGIN
START Notebook_Dell 1500.00 <tempo_restante>
UPDATE Alice 1600.00
```

## TC-04 - Lance maior que o atual

Objetivo: validar troca de vencedor.

No cliente Bob, digite:

```text
1700
```

Resultado esperado nos clientes Alice e Bob:

```text
UPDATE Bob 1700.00
```

Resultado esperado no Bob:

```text
OK_BID
```

Resultado esperado no servidor:

```text
CLIENT Bob: BID 1700.00
```

## TC-05 - Lance igual ao atual

Objetivo: validar rejeicao de lance repetido.

No cliente Alice, digite:

```text
1700
```

Resultado esperado na Alice:

```text
ERR_BID lance_baixo
```

O vencedor deve continuar sendo Bob com `1700.00`.

## TC-06 - Lance menor que o atual

Objetivo: validar rejeicao de lance baixo.

No cliente Alice, digite:

```text
1650
```

Resultado esperado:

```text
ERR_BID lance_baixo
```

## TC-07 - Lance invalido no cliente

Objetivo: validar a interface do cliente quando o usuario digita texto.

No cliente Alice, digite:

```text
abc
```

Resultado esperado:

```text
Valor invalido.
```

Nesse caso o cliente nem envia `BID abc` ao servidor, porque a validacao acontece antes no console.

## TC-08 - Login duplicado

Objetivo: validar que dois clientes ativos nao podem usar o mesmo nome.

Com Alice ainda conectada, abra o Terminal 4:

```powershell
java -cp out client.AuctionClient localhost 12345
```

Digite:

```text
Alice
```

Resultado esperado:

```text
ERR_LOGIN nome_duplicado
```

## TC-09 - Nome invalido

Objetivo: validar regra do nome de usuario.

Abra outro cliente e tente nomes invalidos:

```text
Maria Silva
```

ou:

```text
UsuarioComMaisDe20Caracteres
```

Resultado esperado:

```text
ERR_LOGIN nome_invalido
```

Nomes validos devem conter apenas letras e numeros, com ate 20 caracteres.

## TC-10 - Encerramento automatico do leilao

Objetivo: validar mensagem final apos o tempo acabar.

Aguarde completar os 5 minutos desde que o servidor iniciou.

Resultado esperado nos clientes:

```text
END Bob 1700.00
```

Se nenhum lance tiver sido feito, o esperado e:

```text
END NINGUEM 0.00
```

Resultado esperado no servidor:

```text
SERVER: Leilao encerrado
```

## TC-11 - Lance depois do fim

Objetivo: validar que nao e possivel dar lance depois do encerramento.

Depois da mensagem `END`, tente digitar um novo valor:

```text
2000
```

Resultado esperado:

```text
ERR_BID tempo_esgotado
```

## TC-12 - Persistencia do estado

Objetivo: validar que o arquivo `auction_state.json` e criado e atualizado.

Durante um leilao com lance aceito, em outro terminal rode:

```powershell
Get-Content auction_state.json
```

Resultado esperado:

```json
{
  "itemName": "Notebook_Dell",
  "currentBid": 1700.00,
  "currentWinner": "Bob",
  "remainingSeconds": <tempo_restante>,
  "active": true
}
```

Os valores exatos dependem do lance feito e do tempo restante.

## TC-13 - Restaurar leilao anterior

Objetivo: validar restauracao de estado salvo.

1. Inicie um leilao limpo.
2. Entre com Alice.
3. Faca um lance, por exemplo `1600`.
4. Pare o servidor com `Ctrl+C` antes do tempo acabar.
5. Suba o servidor novamente:

```powershell
java -cp out server.AuctionServer
```

Quando aparecer:

```text
Leilao anterior encontrado. Continuar? (s/n):
```

Digite:

```text
s
```

Resultado esperado:

```text
SERVER: Leilao restaurado com <tempo>s restantes
```

Depois conecte Alice ou outro cliente. O cliente deve receber `START` e `UPDATE Alice 1600.00`.

## TC-14 - Iniciar novo leilao ignorando estado salvo

Objetivo: validar que e possivel descartar restauracao.

Com `auction_state.json` existente, suba o servidor:

```powershell
java -cp out server.AuctionServer
```

Quando perguntar se deseja continuar, digite:

```text
n
```

Resultado esperado:

```text
SERVER: Novo leilao iniciado
```

O primeiro cliente deve receber `START Notebook_Dell 1500.00` sem `UPDATE` de vencedor anterior.

## TC-15 - Limite de 5 clientes

Objetivo: validar o limite de conexoes simultaneas.

Abra 5 clientes com nomes diferentes:

```text
User1
User2
User3
User4
User5
```

Abra um sexto cliente:

```text
User6
```

Resultado esperado no sexto cliente:

```text
ERR_LOGIN servidor_cheio
```

Resultado esperado no servidor:

```text
SERVER: Conexao recusada: limite de clientes atingido
```

## TC-16 - Desconexao e reconexao

Objetivo: validar que um cliente pode sair e reconectar com o mesmo nome.

1. Entre com `Alice`.
2. Faca um lance `1600`.
3. Digite `sair`.
4. Abra outro cliente.
5. Digite novamente `Alice`.

Resultado esperado:

```text
OK_LOGIN
START Notebook_Dell 1500.00 <tempo_restante>
UPDATE Alice 1600.00
```

No servidor deve aparecer:

```text
CLIENT Alice: RECONNECT
```

## TC-17 - Concorrencia basica

Objetivo: validar que dois clientes tentando lances proximos nao quebram o estado.

1. Abra Alice e Bob.
2. Em Alice, digite rapidamente:

```text
1800
```

3. Em Bob, digite rapidamente:

```text
1800
```

Resultado esperado:

- Um cliente recebe `OK_BID`.
- O outro recebe `ERR_BID lance_baixo`.
- Todos veem apenas um vencedor com `UPDATE <usuario> 1800.00`.

## Como encerrar o teste

Nos clientes:

```text
sair
```

No servidor:

```text
Ctrl+C
```

Se quiser limpar o ambiente para novo teste:

```powershell
Remove-Item auction_state.json -ErrorAction SilentlyContinue
Remove-Item out -Recurse -Force -ErrorAction SilentlyContinue
```
