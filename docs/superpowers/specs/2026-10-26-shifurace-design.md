# ShifuRace — design

Data: 2026-10-26
Autor do pedido: dono do servidor Eclipse Cobblemon
Status: rascunho para aprovação

## 1. Intenção

O servidor **Eclipse Cobblemon** (Fabric 1.21.1, Cobblemon 1.7.3) tem uma pista de corrida pronta no mundo, um circuito fechado que passa por asfalto, um trecho de água/gelo com um looping e um lago de lava com caminhos escuros por dentro. O dono quer **corridas de Pokémon estilo F1/GTA** nessa pista: vários jogadores largando juntos, voltas cronometradas, checkpoints em ordem, quem chega primeiro ganha um troféu e os melhores tempos da pista ficam expostos num placar em cima da largada.

As corridas são **eventos conduzidos pela staff** com comandos. Todos correm com o **mesmo Pokémon, o Porytronz** (espécie customizada do servidor), emprestado pelo mod e idêntico para todo mundo, para que a disputa seja justa.

O que importa para o dono:
- a corrida ser **justa** (mesmo Pokémon, mesma velocidade, sem cortar caminho);
- **tempo** como no F1: tempo por volta, melhor volta, tempo total;
- **troféu colecionável**, diferente do troféu da Torre de Batalha (ShifuTower), para dar vontade de juntar troféus diferentes;
- poder **montar a pista sozinho**, escolhendo onde ficam a linha de largada/chegada, os checkpoints e o placar.

## 2. Objetivos

1. Staff monta pistas dentro do jogo: linha de largada/chegada (a mesma linha), checkpoints em ordem, número de voltas e posição do placar.
2. Fluxo de corrida por comando: abrir inscrições → jogadores entram → staff dá a largada → contagem 3, 2, 1, VAI → corrida → chegada → resultado.
3. Porytronz emprestado, idêntico para todos, que existe só durante a corrida.
4. Voltar ao último checkpoint: ao desmontar, ao cair na lava/vazio e por uma tecla (padrão **R**).
5. Tela (HUD) estilo transmissão de F1 e checkpoints visíveis como arco amarelo com seta.
6. Expulsão automática de quem fica 30 s parado.
7. Troféu 3D próprio para o vencedor (1º lugar).
8. Placar com o **top 5 de tempo total da corrida** de todos os tempos, acima da largada.

## 3. Não-objetivos

- **Sem grid de largada marcado**: ninguém escolhe ou marca posições de largada; o mod alinha os pilotos sozinho atrás da linha.
- **Sem os 30 s de espera** depois que o primeiro cruza a chegada.
- **Sem outros prêmios** além do troféu (dinheiro, itens, comandos de recompensa). Pode vir depois, não agora.
- Sem modo contra o relógio sozinho, sem espectador, sem apostas, sem pit stop.
- Sem mexer na Torre de Batalha (ShifuTower) nem em nenhum outro mod do servidor.
- Sem versão "só servidor": todos os jogadores entram pelo launcher do servidor, que terá o mod no cliente também.

## 4. Anti-objetivos (falharia mesmo "funcionando")

- **O Porytronz emprestado nunca pode ficar com o jogador**: não pode ir para o time, PC, GTS, troca, ser solto, capturado por outro jogador, batalhar, evoluir ou sobreviver a um reinício do servidor.
- **Corrida injusta**: velocidades diferentes entre pilotos, ou dar para pular trechos da pista e ainda contar a volta.
- **Jogador perder algo por causa da corrida**: morrer, perder itens, perder Pokémon do próprio time, ficar preso na lava depois de sair da corrida.
- **Interferir na torre ou em outros mods**: o mod é separado e independente.

## 5. Restrições

- **Plataforma**: Fabric, Minecraft 1.21.1, Java 21, Kotlin. Mesma base de build do mod da torre (`D:\Mod da torre de batalha - Eclipse 1.7.3`): Fabric Loom 1.10.5, Kotlin 2.2.20, mapeamentos Mojang + Parchment `2024.11.17`, loader 0.17.3, `fabric-language-kotlin 1.13.6+kotlin.2.2.20`, `com.cobblemon:fabric:1.7.3+1.21.1`, `splitEnvironmentSourceSets()` (código `main` + `client`).
- **No servidor**: `fabric-api-0.116.16+1.21.1` (dependência declarada deve ser `>=0.116.0`, como na torre). Cobblemon 1.7.3 vem dentro do `Eclipse-Cobblemon-1.2.0.jar`, junto com o pack de espécies do servidor.
- **Porytronz**: espécie `cobblemon:porytronz`, vem do pack `eclipse-cobblemon` dentro do `Eclipse-Cobblemon-1.2.0.jar`. Os arquivos de cliente (modelo, animações de chão `ground_walk`/`ground_run`) estão em `E:\Site Eclipse Cobblemon\ferramentas\cobblemon\extras\packs-servidor\eclipse-cobblemon`. A ficha da espécie, com as propriedades de montaria, está **só dentro do jar do servidor** e não foi lida ainda.
- **Distribuição**: o jar vai para `/mods` do servidor (painel Tick) e para o launcher dos jogadores. Há um script pronto: `node "E:/Site Eclipse Cobblemon/ferramentas/tick/subir-mod.mjs" <jar> [--trocar]`. Subir para produção e reiniciar o servidor só com o ok do dono.
- **Idioma**: todo texto visível em português do Brasil (arquivo de idioma `pt_br` + `en_us` com o mesmo texto em português, como a torre faz).
- Pasta do projeto: `D:\Mod de corrida`.

## 6. Abordagens consideradas

| | Abordagem | A favor | Contra |
|---|---|---|---|
| **1 (escolhida)** | **Mod novo e separado, "ShifuRace"**, na mesma base técnica da torre, copiando padrões úteis (troféu, HUD, armazenamento) | Independente: um problema na corrida não afeta a torre; atualizações separadas | Um jar a mais no servidor e no launcher |
| 2 | Corrida dentro do mod da torre (ShifuTower) | Reaproveita troféu e telas direto; um jar a menos | Acopla coisas sem relação; cada ajuste exige nova versão da torre; um erro derruba os dois |
| — | Plugin/datapack só no servidor | Não precisa de cliente | Descartada: não dá para ter a tecla R, a HUD estilo F1 nem os arcos 3D |

O dono escolheu a **1**: "quero um mod separado pra corrida, não quero que interfira na torre nem nada, são mods diferentes".

### Porytronz emprestado (decisão técnica do construtor, ver §7.4)

| | Opção | A favor | Contra |
|---|---|---|---|
| **A (escolhida)** | **Porytronz "de pista"**: um `Pokemon` criado na hora, **fora de qualquer armazenamento** (sem time, sem PC), só como entidade no mundo | Impossível de vender, trocar ou guardar; funciona com time cheio; nada a limpar no time se o servidor cair | Precisa confirmar que o jogador consegue **pilotar** um Pokémon sem dono |
| B (reserva) | Colocar o Porytronz no time por um tempo, com bloqueios | Fluxo normal do Cobblemon, montaria garantida | Problema de time cheio; precisa bloquear GTS/troca/PC/soltar; precisa limpar o time após queda do servidor |

O bytecode do Cobblemon 1.7.3 indica que A funciona: `PokemonEntity.getControllingPassenger()` escolhe o motorista entre os passageiros (`SelectDriverEvent`), dando preferência ao dono **se houver**, e `tryRidingPokemon(ServerPlayer)` só dispara `RIDE_EVENT_PRE`/`POST` e chama `startRiding`. **Primeira tarefa do plano: um teste rápido confirmando que dá para pilotar o Porytronz sem dono.** Se não der, cair para B.

## 7. Como funciona (decidido)

Itens marcados **(dono)** foram decididos pelo dono; **(construtor)** são escolhas minhas, que o dono pode revisar.

### 7.1 Identidade do mod — (dono: "nome do mod pode ser ShifuRace")

- id `shifurace`, nome "ShifuRace", pacote `com.shifurace`, jar `shifurace-<versão>.jar` (mesmo padrão do `shifutower`; funciona com `subir-mod.mjs --trocar`). Os comandos continuam em português: `/corrida`.
- Depende de: `fabricloader >=0.16.0`, `minecraft ~1.21.1`, `java >=21`, `fabric-api >=0.116.0`, `fabric-language-kotlin >=1.13.6+kotlin.2.2.20`, `cobblemon >=1.7.3`.

### 7.2 Pistas e montagem

- **Várias pistas por nome** (construtor). Hoje existe uma; o custo de suportar várias é baixo.
- **Linha de largada/chegada é a mesma linha** (dono).
- **Número de voltas**: a pista guarda um número **padrão**, escolhido na montagem (dono), e cada corrida pode usar outro em `/corrida abrir <pista> [voltas]`. Os recordes são **separados por número de voltas** (dono, ver §7.12), então mudar as voltas não apaga nada.
- **Portões**: a linha e cada checkpoint são um "portão" definido por **dois blocos clicados**, um de cada lado da pista (construtor, aceito pelo dono). O portão é um retângulo vertical entre os dois pontos, do bloco mais baixo até `alturaPortao` blocos acima (padrão 6, configurável por portão).
- **Ferramenta**: `/corrida ferramenta <pista>` dá um item de edição. Clique esquerdo marca o ponto 1, clique direito o ponto 2; o modo atual (linha ou checkpoint) aparece na barra de ação; trocar modo com shift + scroll ou comando. Cada checkpoint novo entra no fim da ordem.
- **Direção da pista**: definida pela linha ao ser criada: o lado "para frente" é para onde o staff estava olhando ao marcar o 2º ponto. Os checkpoints valem cruzando em qualquer sentido, mas **só na ordem**; a linha de chegada só conta cruzando para frente.
- **Modo edição**: quem está editando vê todos os portões com número (o mesmo renderizador dos arcos, em cor de edição) e a seta da direção da linha.
- **Placar**: `/corrida placar <pista>` coloca o placar onde o staff está, virado para onde ele olha (dono pediu "acima da largada"; o staff posiciona).

### 7.3 Fluxo da corrida

1. **`/corrida abrir <pista> [voltas]`** (staff): estado INSCRIÇÕES, com o número de voltas dado ou o padrão da pista. Anúncio no chat do servidor com o número de voltas e botão clicável **[Entrar]** (`/corrida entrar <pista>`).
2. **`/corrida entrar [pista]`** (jogador): só com inscrições abertas e fora de batalha/outra corrida. O mod **guarda onde o jogador estava**, leva-o para trás da linha de largada e já o coloca **montado no Porytronz emprestado** (dono: "ao entrar eles recebam o porytronz"). Enquanto espera, pode andar perto da largada; cruzar a linha não conta.
3. **`/corrida sair`** (jogador): sai a qualquer momento (ver §7.6).
4. **`/corrida iniciar`** (staff): **sem grid** (dono): o mod alinha todos **lado a lado logo atrás da linha**, espaçados ~2,5 blocos ao longo da largura do portão; se não couber, nova fileira 4 blocos atrás (ordem de inscrição) (construtor). Todos ficam **travados** durante **3, 2, 1, VAI!** (título na tela + som). O cronômetro começa no VAI, igual para todos.
5. **Corrida**: checkpoints na ordem; uma volta fecha ao cruzar a linha para frente depois de passar por todos os checkpoints daquela volta.
6. **Chegada**: o primeiro a fechar a última volta **vence**: título "VENCEDOR" para os pilotos, anúncio no chat do servidor, troféu. **Sem 30 s de espera** (dono): os outros continuam até terminar; cada um que termina recebe posição e tempo.
7. **Fim**: quando o último piloto ainda correndo termina ou sai, ou quando a staff usa **`/corrida encerrar`**. Quem não terminou fica como "não terminou". Tela de resultado para todos os pilotos e resumo no chat.

Uma corrida ativa por pista (construtor).

### 7.4 Porytronz emprestado

- **Criado ao entrar** (dono), idêntico para todos (dono): espécie, nível e aspectos vêm da configuração (padrão `cobblemon:porytronz`, nível 50, sem shiny), **sem bônus de montaria** (`rideBoosts` vazios) e estamina cheia. Cada `Pokemon` guarda bônus de montaria próprios no Cobblemon 1.7.3, por isso o emprestado é a forma de garantir velocidade igual.
- **Some** quando a corrida acaba, quando o jogador sai, é expulso, desconecta ou morre (dono).
- **Proteções** (construtor, aceitas pelo dono): marcado como Pokémon de corrida; ninguém além do próprio piloto pode montar ou interagir; não pode ser capturado, batalhado, receber itens, evoluir ou ganhar experiência; não é salvo no mundo. Qualquer entidade de corrida encontrada sem corrida ativa (por exemplo, depois de uma queda do servidor) é removida.
- Durante a corrida o piloto **não pode montar em outro Pokémon** nem soltar Pokémon do próprio time (`RIDE_EVENT_PRE` e o evento de envio de Pokémon do Cobblemon).
- **Remontar**: depois de desmontar, o piloto clica no próprio Porytronz para montar de novo. Como o Pokémon não tem dono, o mod trata esse clique (`UseEntityCallback`) e monta o piloto.
- **Velocidade igual** (construtor): no Cobblemon 1.7.3 cada atributo de montaria = mínimo da faixa da espécie + bônus do Pokémon (o Porytronz tem `land/vehicle`, SPEED 45–65). O emprestado recebe o mesmo bônus em todos os atributos, configurável em `boostMontaria` (0 = mínimo, 1 = máximo; padrão 1).
- **Estamina** (construtor): segue o comportamento padrão do Cobblemon, igual para todos.

### 7.5 Voltar ao checkpoint

- **"Último checkpoint"** = o último portão que o piloto cruzou validamente, incluindo a linha de largada.
- **Desmontou** (dono): piloto e Porytronz vão para o último checkpoint, virados para o próximo. O cronômetro continua; o piloto **precisa montar de novo sozinho** para seguir (dono: "até montar dnv e sair andando").
- **Tecla G e `/corrida voltar`** (dono: "tecla pra se ficar presa"): volta **já montado**, com velocidade zerada. Recarga de 3 s para evitar abuso (construtor). A tecla pode ser trocada em Controles, na categoria "ShifuRace". Padrão **G** e não R: no launcher, R já é "soltar Pokémon" do Cobblemon e "ver receita" do JEI; G, I e N estão livres.
- **Lava e vazio** (dono): tocar lava, fogo ou bloco de magma, ou cair abaixo do Y mínimo da pista (configurável, padrão: o portão mais baixo menos 20) → volta já montado, fogo apagado. Também volta em sufocamento dentro de bloco. Afogamento não (a pista tem trecho de água; quem afundar usa a tecla).
- **Sem dano** (construtor, aceito pelo dono): piloto e Porytronz não tomam nenhum dano durante a corrida, nem de outros jogadores.
- O ponto de reaparição fica no centro do portão, alguns blocos para frente, para não reativar o mesmo portão nem cair de volta no buraco.

### 7.6 Saídas da corrida (construtor)

**Antes de terminar** (por `/corrida sair`, expulsão por AFK, `/corrida encerrar`, teleporte para longe, troca de dimensão ou desconexão), o piloto volta para **onde estava antes do `/corrida entrar`** e o Porytronz some. Assim ninguém fica no meio da lava sem proteção.

**Quem termina** continua montado e protegido até a corrida acabar; no fim, volta para onde estava antes de entrar, como todo mundo.

**Desconexão ou queda do servidor**: o "ponto de volta" fica salvo em disco; ao entrar de novo, o jogador é levado até ele.

Teleporte (`/home`, `/spawn`, `/tpa` etc.) para mais de 64 blocos do Porytronz ou para outra dimensão tira o piloto da corrida.

### 7.7 Parado demais (dono)

- Quem fica **30 s parado** é **expulso automaticamente** (dono). "Parado" = não se afastou mais de 2 blocos do ponto onde estava há 30 s (construtor).
- **Aviso na tela a partir de 20 s**: "Mexa-se! Expulsão em 10s", com contagem (construtor).
- Não conta durante as inscrições, a contagem 3-2-1 nem depois que o piloto termina.
- Valores configuráveis.

### 7.8 Tempos e posições

- **Tempo total**: do VAI até cruzar a linha na última volta. **Tempo de volta**: entre cruzamentos da linha.
- **Precisão em milissegundos** (exibição `m:ss.SSS`), usando o tempo do servidor. O instante do cruzamento é **interpolado dentro do tick** pelo ponto em que o segmento posição-anterior→posição-atual corta o portão (construtor).
- **Detecção de cruzamento**: pelo segmento entre a posição do tick anterior e a do atual do Porytronz cortando o retângulo do portão, para não perder cruzamentos em alta velocidade.
- **Posição na corrida**: quem terminou, pela ordem de chegada; depois, por voltas completas, checkpoints passados e menor distância até o próximo portão.
- **Diferença para o líder**: tempo entre o líder e o piloto passarem pelo mesmo portão na mesma volta; "1 volta"/"N voltas" quando o piloto está uma ou mais voltas atrás.
- **Delta da volta**: em cada checkpoint, compara o tempo parcial da volta atual com o mesmo parcial da **melhor volta do piloto nesta corrida**. Verde = mais rápido, vermelho = mais lento. Na primeira volta não aparece.
- **Roxo** = volta mais rápida da corrida até agora; **verde** = melhor volta pessoal nesta corrida (construtor; F1 faz assim por sessão).

### 7.9 Tela (HUD) — opção C escolhida pelo dono

- **Topo, centro**: "VOLTA 2/3 · CP 5/12", tempo da volta atual grande, delta verde/vermelho "vs sua melhor".
- **Esquerda**: torre de posições com todos os pilotos (posição, nome, diferença para o líder); o próprio piloto destacado.
- **Aviso de volta** no meio da tela por ~3 s: "VOLTA 1 1:14.221", em roxo + "MELHOR VOLTA DA CORRIDA" quando for o caso, verde quando for a melhor pessoal.
- Títulos grandes para 3, 2, 1, VAI!, "ÚLTIMA VOLTA", "VENCEDOR" e o aviso de AFK.
- **Tela de resultado** (GUI) no fim: posição, nome, tempo total, melhor volta (roxo para a mais rápida), "não terminou"; destaque se alguém entrou no top 5 ou bateu o recorde. Resumo também no chat.
- O cliente desenha o cronômetro localmente a partir do instante de largada que o servidor envia (como `RunHud` da torre) para ficar fluido; o servidor manda o estado (posições, voltas, tempos) por pacote quando algo muda e ~4 vezes por segundo.

### 7.10 Checkpoints na pista — opção C escolhida pelo dono

- **Arco amarelo** translúcido sobre o **próximo** portão, com uma **seta** apontando para o portão seguinte. O **seguinte** aparece fraco. Na última volta, a linha de chegada aparece **quadriculada**.
- Desenhados no cliente (evento de renderização do mundo). O servidor manda a geometria da pista ao piloto quando ele entra.
- Só os pilotos daquela corrida veem os arcos (e quem está em modo edição).

### 7.11 Troféu (dono)

- O **1º lugar** ganha um **troféu 3D** que pode ser colocado no chão, com nome do vencedor, pista, tempo total, voltas, número de pilotos e data gravados (tooltip do item e informação ao olhar/clicar no bloco).
- **Modelo próprio, diferente do da torre** (dono: "pra que o povo queira colecionar os troféus diferentes"): uma taça com bandeira quadriculada, por exemplo. Gerado por código como o da torre (`tools/gerar_texturas.js` na torre é a referência).
- **Cada pista tem a sua cor de troféu**, configurável, para dar coleção por pista também (construtor).
- Só vale troféu se a corrida teve **pelo menos 2 pilotos** na largada (construtor; evita troféu de graça correndo sozinho). Configurável.
- `/corrida trofeu <jogador> <pista>` entrega um troféu manualmente (staff), como a torre faz.
- Usa o padrão técnico da torre (`TrophyBlock`, `TrophyBlockEntity`, `TrophyItem`, `TrophyInfo` com componente de dados, renderizador no cliente), **copiado e adaptado com ids próprios**, sem depender da torre.

### 7.12 Placar top 5 (dono)

- Mostra o **top 5 de tempo total da corrida** de todos os tempos na pista (dono), **um ranking para cada número de voltas** (dono: "separar corrida com 1 volta, 2 voltas, 3 voltas, pra cada corrida ter seu top").
- **O placar vai alternando** entre os rankings que têm pelo menos um tempo, a cada 8 s (configurável), com o título "RANKING · CORRIDA DE N VOLTA(S)" (dono). Com um ranking só, ele fica parado.
- Visual do modelo aprovado: nome da pista, título do ranking, 1º–5º com nome, tempo e data; ouro/prata/bronze nas três primeiras posições.
- Quando uma corrida termina e alguém entra num ranking, o placar pula para esse ranking primeiro (construtor).
- **Uma linha por jogador**: vale o melhor tempo de cada um (construtor). Empate: quem fez primeiro fica na frente.
- Feito com entidade **Text Display** do próprio Minecraft (construtor): visível para qualquer um, mesmo fora de corrida. O mod guarda o UUID e recria o placar se ele sumir.
- Atualiza na hora. Novo 1º lugar: anúncio no chat do servidor. Entrou no top 5: aviso para os pilotos.
- Staff: `/corrida recordes <pista> [voltas] limpar` e `/corrida recordes <pista> [voltas] remover <jogador>` (sem `voltas` = todos os rankings da pista).

### 7.13 Comandos

Staff (nível de permissão 2, como a torre):

| Comando | Faz |
|---|---|
| `/corrida pista criar <nome>` / `remover <nome>` / `lista` / `cor <nome> <hex>` | Gerencia pistas (cor = cor do troféu) |
| `/corrida ferramenta <pista>` | Item para marcar linha e checkpoints |
| `/corrida editar <pista>` | Liga/desliga a visualização dos portões |
| `/corrida linha <pista> definir <x y z> <x y z>` / `inverter` | Linha por coordenadas / inverte o sentido |
| `/corrida checkpoint <pista> adicionar <x y z> <x y z>` / `inserir <n>` / `remover <n>` / `limpar` | Ajusta checkpoints (renumera) |
| `/corrida voltas <pista> <n>` | Define o número padrão de voltas da pista |
| `/corrida placar <pista>` / `placar <pista> remover` | Coloca o placar onde você está, virado para você |
| `/corrida abrir <pista> [voltas]` / `iniciar [pista]` / `encerrar [pista]` | Conduz a corrida |
| `/corrida recordes <pista> limpar [voltas]` / `remover <jogador> [voltas]` | Gerencia os rankings |
| `/corrida trofeu <jogador> <pista>` | Entrega troféu |
| `/corrida diagnostico` | Cria um Porytronz de teste, mostra espécie e atributos de montaria e apaga |
| `/corrida recarregar` | Relê a configuração |

Jogadores: `/corrida entrar [pista]`, `/corrida sair`, `/corrida voltar`.

`/corrida iniciar` não funciona se a pista não tiver linha e pelo menos 1 checkpoint, e mostra o que falta.

### 7.14 Dados e configuração (construtor)

- `config/shifurace/config.json`: espécie/nível/aspectos do Porytronz, `boostMontaria` (1.0), `maxPilotos` (padrão 16), tempos do AFK (30/20 s), contagem (3), recarga da tecla de voltar (3 s), mínimo de pilotos para troféu (2), altura padrão do portão (6).
- `<mundo>/shifurace/pistas.json`: pistas (dimensão, portões, direção, voltas, cor do troféu, posição e UUID do placar).
- `<mundo>/shifurace/recordes/<pista>.json`: rankings da pista, um por número de voltas.
- Tempo de troca do placar (8 s) em `config.json`.
- `<mundo>/shifurace/retornos.json`: ponto de volta de quem estava em corrida (para reinício/queda).
- Escrita segura em disco como `SafeFiles` da torre.

## 8. Testes e entrega

- **Primeiro**: teste de pilotar um Porytronz sem dono (§6). Em desenvolvimento, a espécie é configurável, então dá para testar com qualquer Pokémon montável do Cobblemon puro. O teste final com o Porytronz precisa do `Eclipse-Cobblemon-1.2.0.jar` (ou do pack de espécies dele) no ambiente de teste.
- Testes automatizados para a lógica pura: cruzamento de portão (incluindo alta velocidade e sentido), ordem de checkpoints e contagem de voltas, cálculo de posições e diferenças, delta, ranking top 5 (uma linha por jogador, empate), regra de AFK.
- Teste manual com `runServer`/`runClient` antes de subir.
- Entrega: build → `subir-mod.mjs` (com ok do dono) → reiniciar servidor (com ok do dono) → dono coloca o jar no launcher.

## 9. Fica a critério do construtor

- Desenho exato da HUD, cores e fontes, mantendo o modelo C aprovado.
- Modelo e textura do troféu, desde que claramente diferente do da torre.
- Como travar o Porytronz durante a contagem.
- Detalhes de rede (formato dos pacotes, frequência exata).
- Colisão entre Porytronz de pilotos diferentes: manter o padrão do Minecraft; se atrapalhar nos testes, desligar o empurrão entre eles.
- Mensagens de chat e sons.
