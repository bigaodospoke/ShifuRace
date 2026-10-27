# ShifuRace

Corridas de Pokémon estilo F1 para o **Eclipse Cobblemon** (Fabric 1.21.1, Cobblemon 1.7.3).

Os jogadores correm juntos numa pista montada no próprio mundo, todos com um **Porytronz emprestado e idêntico**, passando pelos checkpoints na ordem. O mod cronometra cada volta e mostra uma tela estilo transmissão de F1. Quem chega em 1º ganha um **troféu 3D colecionável**, e um **placar** em cima da largada mostra o top 5 de todos os tempos, separado por número de voltas.

## Instalação

1. Baixe `shifurace-1.0.0.jar` em [Releases](../../releases) e coloque em `/mods` do servidor **e** no launcher dos jogadores. O mod tem parte no cliente: tela, arcos e tecla.
2. Depende de: Fabric API, Fabric Language Kotlin e Cobblemon 1.7.3. Todos já estão no modpack do servidor.
3. Reinicie o servidor. A configuração é criada em `config/shifurace/config.json`.
4. Confira o Porytronz com `/corrida diagnostico`: tem que aparecer a espécie, o estilo `LAND` e os atributos de montaria.

## Montando a pista (staff)

1. `/corrida pista criar Cavernas`: cria a pista e já entrega a **Ferramenta de Pista**.
2. **Linha de largada/chegada:** com a ferramenta, dê **clique esquerdo** num bloco de um lado da pista e **clique direito** no bloco do outro lado, **olhando para o sentido da corrida**. A seta verde mostra o sentido; se ficar ao contrário, use `/corrida linha Cavernas inverter`.
3. **Checkpoints:** do mesmo jeito, na ordem da volta. Cada portão novo entra no fim da lista; a barra de ação mostra o número.
   - Agachado + clique direito no ar troca entre "linha" e "checkpoint".
   - `/corrida checkpoint Cavernas remover 3`: apaga o 3 e renumera os seguintes.
   - `/corrida checkpoint Cavernas inserir 3`: o próximo que você marcar entra como número 3.
   - Por coordenadas: `/corrida checkpoint Cavernas adicionar <x y z> <x y z>` e `/corrida linha Cavernas definir <x y z> <x y z>`.
4. `/corrida voltas Cavernas 3`: número padrão de voltas da pista.
5. **Placar:** fique onde quer o placar (em cima da largada), olhe para a pista e use `/corrida placar Cavernas`. Para tirar: `/corrida placar Cavernas remover`.
6. `/corrida pista cor Cavernas 3FA9F5`: cor dos troféus desta pista. Cada pista com uma cor = troféus diferentes para colecionar.
7. `/corrida editar Cavernas` liga e desliga a visualização dos portões, com o número de cada um.

Coloque os checkpoints de modo que não dê para cortar caminho: eles só valem na ordem, e a volta só fecha cruzando a linha depois de passar por todos.

## Fazendo uma corrida

| Quem | Comando | O que faz |
|---|---|---|
| Staff | `/corrida abrir Cavernas [voltas]` | Abre as inscrições e anuncia no chat com o botão **[Entrar]**. Sem `voltas`, usa o padrão da pista |
| Jogador | `/corrida entrar` | Entra, é levado para a largada e já recebe o Porytronz de corrida |
| Jogador | `/corrida sair` | Sai e volta para onde estava antes de entrar |
| Staff | `/corrida iniciar` | Alinha todo mundo atrás da linha, faz 3, 2, 1, VAI! |
| Staff | `/corrida encerrar` | Encerra na hora; quem não terminou fica como "não terminou" |

Durante a corrida:

- **Tecla G** (ou `/corrida voltar`) volta para o último checkpoint, já montado. Dá para trocar a tecla em Opções → Controles → "ShifuRace". O padrão não é R porque R já é "soltar Pokémon" do Cobblemon.
- **Desmontou** → volta para o último checkpoint e precisa **clicar no Porytronz** para montar de novo. O tempo continua correndo.
- **Lava, fogo, vazio** → volta para o último checkpoint. Ninguém toma dano na corrida.
- **Parado 30 s** no mesmo lugar → expulso. O aviso aparece a partir de 20 s.
- O 1º que fecha a última volta **vence** e ganha o troféu. Os outros continuam até terminar. A corrida acaba quando o último termina ou sai.
- No fim aparece a tela de resultado, o Porytronz emprestado some e todo mundo volta para onde estava antes do `/corrida entrar`.

O troféu só sai em corridas com pelo menos 2 pilotos na largada. Isso é configurável.

## Recordes

- Cada pista tem **um ranking por número de voltas** (1 volta, 2 voltas, 3 voltas…), com o melhor tempo total de cada jogador.
- O placar alterna entre os rankings a cada 8 segundos e pula para o ranking de uma corrida que acabou de terminar.
- `/corrida recordes Cavernas limpar [voltas]` e `/corrida recordes Cavernas remover <jogador> [voltas]`.
- `/corrida trofeu <jogador> Cavernas` entrega um troféu na mão.

## Configuração (`config/shifurace/config.json`)

| Opção | Padrão | O que é |
|---|---|---|
| `pokemon` | `porytronz level=50` | Pokémon emprestado (formato do `/pokegive`) |
| `boostMontaria` | `1.0` | Bônus de montaria igual para todos: 0 = mínimo da espécie, 1 = máximo |
| `maxPilotos` | `16` | Máximo de pilotos por corrida |
| `segundosContagem` | `3` | Contagem antes do VAI |
| `segundosParadoAviso` / `segundosParadoExpulsao` | `20` / `30` | Aviso e expulsão de quem fica parado |
| `raioParado` | `2.0` | Quem não se afasta mais que isso (blocos) conta como parado |
| `segundosRecargaVoltar` | `3` | Intervalo mínimo entre dois usos da tecla de voltar |
| `minimoPilotosTrofeu` | `2` | Mínimo de pilotos na largada para dar troféu |
| `alturaPortao` | `6` | Altura dos portões novos |
| `margemVazio` | `20` | Abaixo do portão mais baixo menos isso = caiu no vazio |
| `distanciaMaximaDoPokemon` | `64` | Piloto mais longe que isso do Porytronz (teleporte) sai da corrida |
| `segundosTrocaPlacar` / `linhasPlacar` | `8` / `5` | Placar: tempo de troca e quantas posições |

`/corrida recarregar` relê a configuração.

## Dados

Ficam em `<mundo>/shifurace/`: `pistas.json`, `retornos.json` (para onde cada um volta se o servidor cair no meio de uma corrida) e `recordes/<pista>.json`.

## Para desenvolvedores

- `./gradlew build` compila e roda os testes da lógica (portões, voltas, posições, ranking).
- `bash run/selftest.sh run/test-output.log | ./gradlew runServer | tee run/test-output.log` sobe um servidor de teste e roda os comandos principais pelo console. O Porytronz vem de um datapack em `run/world/datapacks/porytronz`.
- `node tools/gerar_recursos.js` regera as texturas, o modelo do troféu e o ícone.
