# Plano: leitor e tradutor do alfabeto Zu (Fez) em Kotlin

> Projeto de fã, sem vínculo com a Polytron. Não usamos sprites, logos nem assets do jogo:
> o ícone (chapéu fez) e a paleta são arte original inspirada no clima do jogo.

## 1. Objetivo

Um app Android simples:

1. **Tirar uma foto** (ou escolher da galeria) de um texto escrito no alfabeto Zu.
2. **Ler os glifos** da imagem e montar o texto (em inglês, porque o Zu é uma cifra de substituição do alfabeto latino).
3. ~~Traduzir o texto para português~~: fora do escopo por enquanto (fase opcional no fim). O app mostra o texto em inglês.

Nome sugerido: **Zu Reader**. Evitar "Fez" no nome publicado na Play Store para não esbarrar em marca registrada. O repositório pode continuar `fez-translate`.

## 2. O alfabeto Zu (validado na Fase 1)

| Ponto | O que sabemos | Como o app trata |
|---|---|---|
| Natureza | Cifra de substituição: cada glifo = uma letra latina | Tabela glifo → letra (`ZuAlphabet`) |
| Forma | Grade 5×5 de blocos | `BitMatrix` 5×5 |
| Quantidade | **24 glifos para 26 letras**: K/Q e U/V compartilham glifo | O modelo de inglês escolhe a letra pelo contexto (QUICK, OVER) |
| Estrutura | **6 formas, cada uma em 4 rotações** (ex.: A, G, M, S são a mesma forma girada) | Nunca girar glifo por glifo: a rotação muda a letra |
| Rotação da foto | Um texto girado também forma letras válidas | Testar as 4 orientações do texto inteiro e escolher a mais parecida com inglês |
| Direção | Colunas de cima para baixo, da direita para a esquerda (**confirmado** com a parede "THE POINTS CONSTELLATE…") | Padrão; opção horizontal |
| Números | Sistema separado (0–10, com variantes de 3–7), todos com moldura e miolo 3×3; alguns ficam a só 1 bloco um do outro (ex.: 6 e 9). Também trocam de valor ao girar | **Modo separado** "Números" (Fase 8): não mistura letras e números; a confiança considera a folga de 1 bloco |

Fonte dos desenhos: [FEZ Pixel Font, de TakWolf](https://github.com/TakWolf/fez-pixel-font) (CC0). O alfabeto pertence à Polytron.

Referências: [StrategyWiki: Fez/Alphabet](https://StrategyWiki.org/wiki/Fez/Alphabet), [dCode: Fez alphabet](https://www.dcode.fr/fez-alphabet), [Zu Language typeface (itch.io)](https://themizarkshow.itch.io/zu-language).

## 3. Arquitetura

### Stack

| Camada | Escolha | Por quê |
|---|---|---|
| Linguagem | Kotlin 2.x | Pedido do projeto |
| UI | Jetpack Compose + Material 3 | Tema próprio com a paleta do design |
| Câmera | CameraX (`Preview`, `ImageCapture`, `ImageAnalysis`) | Preview e detecção ao vivo |
| Galeria | Photo Picker (`PickVisualMedia`) | Sem permissão de armazenamento |
| Visão | Kotlin puro (`core/vision`); OpenCV só se a Fase 3 pedir correção de perspectiva | Glifos são blocos: limiar, manchas e grade bastam. Testável na JVM e sem 30 MB de bibliotecas nativas |
| Classificação | Template matching (MVP) → TensorFlow Lite (Fase 6) | MVP sem treinar modelo |
| Tradução (opcional, depois) | ML Kit Translation (EN → PT, on-device) | Só se for retomada: gratuito e funciona offline após baixar o pacote |
| Persistência | Room (histórico) + DataStore (preferências) | |
| DI | Hilt | |
| Imagens | Coil | |
| Testes | JUnit 5, Turbine, Compose UI Test | |
| CI | GitHub Actions (build + testes + lint) | |

`minSdk 26`, `targetSdk` mais recente. Tudo roda no aparelho, sem servidor.

### Módulos

```
fez-translate/
├── app/                      # Activity, navegação, tema, DI
├── core/zu/                  # Kotlin puro: alfabeto, BitMatrix, decoder, ordenação
├── core/vision/              # Kotlin puro: limiar, manchas, caixas de glifo, grade 5×5
├── core/data/                # Room, DataStore, repositórios
├── feature/home/
├── feature/camera/
├── feature/result/           # resultado + correção de letra
└── feature/history/
```

`core/zu` é Kotlin/JVM puro: toda a lógica de reconhecimento testável sem emulador.

### Modelos principais (`core/zu`)

```kotlin
class BitMatrix                       // grade de blocos; rotate(), hammingDistance()
data class ZuGlyph(val letters: String, val pattern: BitMatrix)   // "KQ", "UV" ou uma letra
data class PlacedGlyph(val x: Float, val y: Float, val size: Float, val bits: BitMatrix)

class GlyphClassifier { fun classify(bits: BitMatrix): GlyphClassification }
object ZuLayout { fun order(...); fun compose(text, ...); fun rotate(glyphs, quarterTurns) }

class ZuReader { fun read(glyphs: List<PlacedGlyph>, rotation: Int? = null): ZuReading }
data class ZuReading(val text: String, val glyphs: List<GlyphReading>, val rotation: Int, ...)
data class GlyphReading(val source: PlacedGlyph, val letter: Char, val confidence: Float,
                        val alternatives: List<LetterScore>)
```

A Fase 2 só precisa transformar a foto em `List<PlacedGlyph>`; o resto já existe.

## 4. Pipeline de leitura

```mermaid
flowchart LR
  A[Foto / galeria] --> B[Pré-processamento]
  B --> C[Segmentação dos glifos]
  C --> D[Normalização em matriz de bits]
  D --> E[Classificação por template]
  E --> F[Ordenação: colunas e linhas]
  F --> G[Texto em inglês]
```

1. **Limiar**: escala de cinza (transparência vira branco) e limiar de Otsu. Testa texto escuro e texto claro e fica com o que encontra mais glifos confiáveis.
2. **Manchas**: componentes conectados (vizinhança de 8).
3. **Caixas de glifo**: a maioria dos glifos tem duas peças separadas por 1 bloco, o mesmo vão entre glifos, então não dá para juntar por distância. Mas todo glifo tem tinta em todas as linhas e colunas: a sombra das peças em cada eixo é contínua e do tamanho do glifo. Corta-se em faixas por um eixo e depois pelo outro (as duas ordens são testadas). Manchas grandes demais (molduras, cenário), sujeira e quadrados sólidos são descartados.
4. **Grade 5×5 de cada glifo**: cada célula vira bloco se mais da metade do seu miolo for tinta → `BitMatrix`. Células "meio a meio" indicam ruído e o glifo é descartado.

Antes do passo 2, a inclinação é medida (±12°) e a máscara é endireitada; a leitura endireitada só é usada se for melhor que a original. No passo 3, largura e altura do glifo são medidas separadamente (aceita imagem esticada) e glifos de tamanhos diferentes formam blocos separados, lidos com a mesma orientação.
5. **Classificação**: distância de Hamming contra os 24 modelos, sem girar o glifo. Confiança = folga para o segundo colocado × proximidade do modelo (exato = 1; 1 bloco errado sem empate = 0,8; empate = 0). Abaixo de 0,7 vira "letra com dúvida" (dourado na UI).
6. **Ordenação**: agrupar em colunas pelo centro X (tolerância de meio glifo); colunas da direita para a esquerda, glifos de cima para baixo. Fim de coluna e vãos maiores que 1,75 glifo viram espaço (suposição a validar com prints reais).
7. **Texto e orientação**: para cada uma das 4 orientações, Viterbi sobre pares de letras do inglês escolhe a sequência mais provável (resolve K/Q, U/V e blocos com ruído). Vence a orientação de maior pontuação; girar a foto tem um custo, porque fotos e prints chegam em pé: textos com menos de 6 glifos ficam na orientação da foto (sem contexto, o inglês erra nomes como "ZU", que de cabeça para baixo vira "IE"); a partir de 6 glifos o inglês decide o giro.
8. **Tradução**: fora do escopo por enquanto.

Na câmera ao vivo, os passos 1 a 5 rodam no `ImageAnalysis` a cada ~300 ms em resolução reduzida, só para desenhar as caixas e o contador "7 glifos". A leitura completa roda na foto em resolução cheia.

## 5. Telas (ver `docs/design/mockup.html`)

1. **Início**: botão "Fotografar" (vermelho), "Escolher da galeria", lista de recentes.
2. **Câmera**: guia com cantos dourados, caixas turquesa nos glifos detectados, dourado nos duvidosos, contador de glifos, flash, galeria.
3. **Resultado**: foto com as caixas, texto lido em fonte pixel, faixa glifo → letra, ações Copiar / Compartilhar / Salvar.
4. **Corrigir letra** (bottom sheet): glifo ampliado, até 3 sugestões, teclado A–Z. A correção atualiza o texto na hora. Usar as correções para melhorar o reconhecimento fica para a Fase 6.
5. **Histórico** e **Configurações** (direção de leitura, tema claro/escuro).

### Identidade visual

| Token | Cor | Uso |
|---|---|---|
| Night | `#1E1B3A` | Fundo (céu noturno) |
| Surface | `#2A2650` | Cards, sheets |
| Fez Red | `#D7263D` | Ação principal |
| Fez Deep | `#A3172B` | Pressionado, sombra do botão |
| Cube Gold | `#F5B700` | Destaques, letra com dúvida, links |
| Scan Teal | `#4FC3C9` | Glifo detectado, sucesso |
| Paper | `#FFF6E5` | Texto principal |
| Muted | `#A9A3C9` | Texto secundário |

Fontes: **Silkscreen** (títulos e texto lido, pixel), **Figtree** (interface), **JetBrains Mono** (números/confiança), todas OFL, empacotadas em `res/font`.
Ícone: `docs/design/icon.svg` (chapéu fez 16×16), convertido em adaptive icon (foreground vetorial + fundo `#1E1B3A`).

## 6. Fases

| Fase | Entrega | Pronto quando |
|---|---|---|
| **0. Setup** ✅ | Projeto Gradle multi-módulo (version catalog), tema Compose com a paleta, ícone, CI no GitHub Actions. JDK 25 LTS, Gradle 9.8.2, Kotlin 2.4.21, AGP 9.5.1 | `./gradlew build` verde no CI; app abre a tela inicial |
| **1. Alfabeto + decoder** ✅ | `core/zu`: 24 glifos, classificador por distância, ordenação de colunas, modelo de pares de letras do inglês, escolha da orientação | Testes: frases sintéticas lidas certas em pé e giradas (≥ 4 letras); 99,8% de acerto por caractere com 1 bloco errado em cada glifo |
| **2. Visão em imagem estática** 🟡 | `core/vision` (Kotlin puro); galeria → recorte → resultado. Com fotos reais: limiar local (média da vizinhança) em várias escalas, com desfoque leve contra o chiado de foto de tela; combinação das máscaras glifo a glifo (votação); junção de blocos de tamanho parecido (perspectiva); descarte de blocos extras sem confiança; se a leitura sair fraca, versões de cor (contraste esticado, canais R/G/B, saturação) entram na mesma combinação | Fotos reais (`testdata/real`, teste `RealPhotosTest`): parede escura **100%**, parede clara **98%**, lápide 2 **79%** (recortada **95%**), lápide 1 **63%** (recortada **81%**) de acerto por letra (~1–3 s por foto na JVM). Segunda etapa (leitura fraca) inclui máscaras afinadas (erosão) para separar glifos que a foto juntou; partes do mesmo texto até 2,5 glifos de distância são juntadas. Confirmado com texto real: colunas da direita para a esquerda, palavras separadas por vão na coluna e por fim de coluna. Vagas da grade: depois de escolher a orientação, um vão de 2–3 passos entre glifos da mesma coluna com tinta no meio vira glifo (lido com ajuste fino de posição); vão sem tinta continua espaço. **Falta**: glifos perdidos na ponta das colunas e mais fotos |
| **2.5. Glifos distorcidos e de tamanhos variados** ✅ | Largura e altura do glifo medidas separadamente; grupos por tamanho (ex.: título e corpo, separados por quebra de linha); inclinação corrigida pela projeção mais "pontuda" (±12°), usada só quando melhora a leitura; filtro de nitidez e tamanho mínimo (2 px por bloco) contra ruído | Testes sintéticos: 100% com esticamento vertical e horizontal de 0,5× a 3×, inclinação de ±3° a ±10°, título grande + texto pequeno (também com a foto girada) e combinação inclinada + esticada + JPEG. Leitura de 1680×1309 px em ~200 ms na JVM |
| **3. Câmera** 🟡 | CameraX (preview, análise e captura em 4:3); permissão com explicação e atalho para as configurações; visor ao vivo com caixas, contador e texto lido a cada 300 ms (quadros de 640 px, só o plano Y); flash; captura girada e reduzida fora da thread principal; tela de recorte retangular (quinas sempre a 90°): puxar cantos ou bordas, arrastar o quadro por dentro ou tocar fora e arrastar para desenhar um novo; sugestão automática em volta do texto | Testes sintéticos: foto de lado em trapézio não é lida sem recorte e é lida 100% depois (também com cantos marcados com erro de alguns pixels); conversão de quadros e mapeamento das caixas testados. **Falta**: ≥ 85% em 30 fotos reais de monitor/TV e teste em aparelho |
| **4. Correção e histórico** ✅ | Toque numa letra da faixa abre o painel de correção (glifo ampliado, sugestões, teclado A–Z); letras corrigidas ficam em turquesa e deixam de ser dúvida. Salvar guarda imagem (JPEG) e leitura completa (Room 2.8.5 + formato `ReadingCodec`); correções numa leitura salva são gravadas na hora. Recentes na tela inicial, tela de histórico, apagar com confirmação | Testes: troca de letra mantendo espaços e quebras de linha; gravação e leitura da leitura completa (ida e volta, inclusive com letra corrigida, inclinação e dois blocos). Banco verificado em compilação pelo Room. **Falta**: testar o fluxo num aparelho |
| **5. Polimento** ✅ | Tema claro completo (e câmera sempre escura); configurações com direção de leitura, tema e "Sobre" com créditos e licenças (DataStore 1.3.0); contraste revisado (texto ≥ 4,5:1, bordas de controles ≥ 3:1 nos dois temas); alvos de toque ≥ 48 dp (teclado de correção com 6 teclas por linha, faixa de letras, botões da câmera); títulos de seção anunciados como títulos; texto lido ao vivo anunciado pelo leitor de tela; rótulos que encolhem com fonte grande; release com R8 (APK de 2,5 MB) e assinatura opcional por secrets; CI publica APK e AAB; ícone 512 px para a loja | Build de release verificado (sem assinatura e com chave de teste). Capturas em tema claro, configurações e fonte 150%. **Falta**: rodar o TalkBack num aparelho |
| **6a. Aprender com as correções** ✅ | Ao corrigir uma letra, o desenho do glifo (na orientação de leitura) vira um exemplo do usuário (Room, até 300, migração automática 1→2). O classificador compara com o alfabeto e com os exemplos; um exemplo só vence se estiver mais perto (empate = alfabeto) e, se for idêntico, vale contra o contexto do inglês. Vale para galeria, câmera e visor ao vivo. Botão "Esquecer correções" nas configurações | Testes: um "E" deformado que o alfabeto lê como outra letra passa a ser lido certo no texto inteiro depois de um exemplo, também com a foto girada; empate continua com o alfabeto; exemplos da mesma letra não tiram confiança |
| **6b. Modelo treinado (opcional)** 🧪 | Testado: rede pequena (MLP 16×16 → 96 → 24 glifos + "não é glifo") treinada com numpy em ~93 mil recortes sintéticos degradados + glifos das paredes reais (gerador `GlyphNetDataGenerator`, script `tools/train_glyphnet.py`); 99,3% na validação. No leitor, ela resgatava glifos borrados e descartava lixo | **Não entrou no app**: nas lápides (fora do treino) o resultado oscilou — lápide 2 inteira subiu de 79% para 95%, mas as recortadas caíram (95% → 84% e 69% → 13%); restringir a correção a grades parecidas não deixou nenhuma variante melhor em todas as fotos. Retomar com mais fotos reais de lápides/pedra para treino |
| **7. Tradução (opcional)** | ML Kit Translation EN → PT no aparelho, mostrando inglês e português | Só se o projeto decidir retomar a tradução |
| **8. Números** ✅ | Seletor **Auto / Escrita / Números** (Auto é o padrão: lê em paralelo como escrita, como números e **misturado** — letras e números na mesma frase, ex.: "ROOM 12" —, uma thread por modo, e fica com a leitura de nota maior; a mista só vale com letras e números confiantes no mesmo bloco e letras iguais às da leitura só de escrita; nenhuma letra tem moldura completa e o par mais parecido, B e 0, difere em 2 quadradinhos) na tela inicial e na câmera (fica salvo). Modo números: 16 desenhos (0–10 e variantes de 3–7, todos com moldura e miolo 3×3), sem modelo do inglês, orientação da foto; resultado "3 10 7 / 1 2", correção com teclado 0–10; o modo fica salvo na leitura do histórico; exemplos aprendidos separados por modo | Testes: 16 desenhos distintos com moldura, cada um reconhecido; números em colunas, com ruído, em imagem com borrão/ruído/JPEG e colorida; modo escrita não devolve números; leitura salva guarda o modo (e as antigas abrem como escrita). Placas de contas da biblioteca ("1 2 7 = 10"): o sinal de igual (dois traços da largura de um número) vira `=`, e a faixa de números aceita bordas mais finas que um bloco. Tabela real dos números do jogo (0–10, enviada pelo usuário) bate com os 16 desenhos e é lida inteira no modo Auto (`RealNumbersTest`). Placas da biblioteca (números encostados numa faixa em relevo): fatiadas de 4 em 4 blocos; recortadas, são lidas como números. **Falta**: gabarito dos dígitos dessas placas e leitura sem recorte |
| **9. Escrever à mão (teclado Zu)** ✅ | **Terceira opção na tela inicial** ("Escrever à mão"), no mesmo nível de "Fotografar" e "Escolher da galeria". Tudo numa tela só, sem rolagem (a mensagem ocupa o espaço que o teclado deixa; cabe em 360×640 dp). Dois jeitos de digitar: **Glifos** (para quem copia do jogo e não sabe o que os glifos significam: organizados pelo desenho em 6 colunas, uma coluna por forma e uma linha por rotação, `ZuAlphabet.shapes`, e embaixo, depois de uma separação, todos os 16 desenhos de números, com as variantes de 3 a 7; K/Q e U/V numa tecla só, com a letra escolhida pelo contexto do inglês e atualizada enquanto digita, como na leitura de foto, `SharedLetters`) ou **Letras e números** (A–Z e, depois de uma separação, 0–10, um de cada, com K e Q separados; cada tecla mostra a letra e o glifo dela). 6 teclas por linha, com altura que se ajusta à tela (46 dp, até 36 dp em tela baixa, para a mensagem em Zu continuar com espaço); Espaço, Nova coluna (na leitura horizontal, Nova linha: o próximo glifo começa uma coluna nova), Apagar, Limpar e Pronto numa linha. A mensagem em Zu fica sempre legível: as colunas aparecem inteiras, com os glifos do maior tamanho que a altura permite, e se não couberem todas aparecem as mais recentes (onde o usuário está digitando); o texto em inglês mostra o final. A mensagem segue o cursor: o último glifo fica sempre à vista, junto da borda para onde a mensagem cresce (colunas novas à esquerda, linhas novas embaixo). O teclado tem prioridade: tocar na mensagem abre ela inteira em tela cheia, num modal com zoom de dois dedos e o texto completo em inglês; fechar volta a escrever, Espaço, Apagar e Limpar tudo; em cima, a mensagem em Zu em colunas (como no jogo, na direção de leitura escolhida, último glifo em dourado) e o texto em inglês. "Pronto" abre a tela de resultado (copiar, compartilhar, salvar no histórico, zoom); voltar reabre o teclado com o texto. A leitura é exata (`ZuWriter`): o texto montado vira a leitura sem passar pelo leitor de imagens | Testes: texto vira imagem e leitura exata (K/Q do usuário mantido; o leitor de imagens lê a mesma coisa na imagem gerada), números e misto, caixas em cima dos glifos desenhados, salvar e abrir igual. Capturas: [tela inicial](design/home-escrever-android.png), [teclado](design/escrever-vazio-android.png), [glifos](design/escrever-texto-android.png), [letras e números](design/escrever-letras-android.png), [tela pequena](design/escrever-pequeno-android.png), [mensagem longa](design/escrever-longo-android.png), [tela cheia](design/escrever-tela-cheia-android.png), [resultado](design/escrever-resultado-android.png) |
| **10. QR code** ✅ | Botão próprio **"Ler QR code"** na tela inicial, ao lado de "Anotar foto": câmera com leitura ao vivo (basta apontar; também dá para fotografar) ou galeria, e uma tela só do QR com o conteúdo grande e selecionável, "Abrir link" (se for http/https), Copiar, Compartilhar e "Ler outro"; a tela mostra a imagem de onde o QR foi lido (na câmera ao vivo, o app tira uma foto colorida ao achar o QR), que dá para **recortar**, e **Salvar** guarda imagem e texto no histórico ("QR: …"), reabrindo na mesma tela. Além disso, Toda imagem lida (câmera ou galeria) também é procurada por um QR code (ZXing 3.5.4, sem internet; tenta também com as cores invertidas e reduzida). Se o recorte deixou o QR de fora, procura na foto inteira. No resultado, um cartão mostra o texto inteiro em fonte monoespaçada e selecionável (pode ser um comando para digitar no jogo), com "Abrir link" quando é http/https e "Copiar"; sem glifos, aparece só o QR. Copiar/Compartilhar do rodapé levam o texto do QR junto; o QR vai para o histórico | Testes: link e texto, QR claro sobre escuro, com ruído/JPEG, pequeno no canto de foto grande, imagem sem QR; ida e volta no histórico (com `;`, `|`, quebra de linha e acentos) e leituras antigas sem QR. Capturas: [link](design/qr-link-android.png), [texto](design/qr-texto-android.png) |
| **11. Anotar foto** ✅ | Opção da tela inicial, ao lado de "Ler QR code" ("Escrever à mão" fica por último): tirar uma foto ou escolher da galeria e anotar por cima — caneta (risco à mão livre), retângulo, círculo e texto, em 4 cores (dourado, vermelho, azul, branco), com desfazer e limpar. Os desenhos ficam em pixels da imagem (a foto salva sai igual à tela; espessura e texto proporcionais à foto). Salvar pede um nome (opcional) e guarda no histórico com a marca "Anotação" (Room 3, coluna `kind`, migração automática); depois dá para ver, anotar mais, compartilhar a imagem (FileProvider) e apagar | Capturas: [editor](design/anotar-editor-android.png), [anotação salva](design/anotar-salva-android.png). Falta testar os gestos num aparelho |

### Dados de teste

- **Prints**: capturas do próprio jogo (de quem tem o jogo), guardadas fora do repositório público se houver dúvida de direitos.
- **Sintéticos**: script que renderiza os templates com ruído, desfoque, perspectiva, moiré e variação de cor. Serve para os testes da Fase 2 e para treinar o modelo da Fase 6.
- Métrica: taxa de acerto por letra e por frase, registrada a cada PR no CI.

## 7. Riscos

| Risco | Mitigação |
|---|---|
| Moiré e reflexo em foto de tela | Desfoque antes do limiar, dica na câmera ("afaste um pouco"), importar print pela galeria |
| K/Q e U/V com o mesmo glifo; B/H/N/T e F/L/R/Y a 2 blocos de distância | Contexto do inglês escolhe; empates marcados como dúvida com alternativas |
| Texto girado | As 4 orientações são testadas; textos com menos de 6 glifos ficam na orientação da foto (placa "NU ZU" não vira "IE IB") |
| Foto muito de lado (texto em trapézio) | Recorte retangular em volta do texto; a leitura aguenta esticamento até 3× e inclinação até ±10° (Fase 2.5) |
| Glifos colados ou muito pequenos | Exigir tamanho mínimo; recorte manual; zoom na câmera |
| Foto de lado ou imagem esticada deixa o glifo retangular | Resolvido na Fase 2.5 (esticamento até 3×, inclinação até ±10°). Perspectiva forte (trapézio) fica para a Fase 3 |
| Marca "Fez" | Nome do app sem "Fez"; aviso de projeto de fã; nenhum asset do jogo |

## 8. Próximos passos

1. Aprovar este plano e o design (`docs/design/mockup.png`).
2. Fase 0: criar o projeto Android no repositório.
3. Fechar a Fase 2 com prints reais do jogo: medir o acerto e validar a direção de leitura, como as palavras são separadas e a orientação dos glifos.
4. Testar num aparelho real: câmera, histórico e TalkBack.
5. Novos estilos reais: balão de fala (100%), parede de ladrilhos em relevo (93%) e prints das lápides (recortados: 100% e 100%; inteiros ~75%, com lixo do cenário) já são lidos; **letreiros de neon** (cada glifo é o contorno da sua área de tinta): o miolo é pintado pela profundidade (cruzar um contorno alterna fundo/tinta, então glifos com buraco, como G e O, saem certos), a moldura da placa é ignorada (bem maior que um glifo) e o preenchimento roda também na intensidade da cor (canal mais forte − mais fraco), que tira as linhas apagadas da parede atrás do neon. A cidade inteira, sem recorte, é lida **"ZU GOOD WORK"** (100%); recortadas, "GOOD", "WORK" e "EAT MEAT" 100%, com qualquer folga. A placa pequena do alto é um desenho (um bonequinho), não texto, e não é lida. **Falta**: "EAT MEAT" na foto inteira e o recorte só da placa "ZU" (sai "IE"; na foto inteira sai certo).
6. **Manchas de luz** (luz passando pelas folhas, na lápide em print): onde a luz cai em cima da tinta, ela fica da cor da pedra. Caixas bem menores que as do bloco (meio glifo cortado pela luz) são descartadas e a vaga é relida; na ponta da coluna, só com coluna vizinha na mesma altura e desenho a até 3 blocos de uma letra. Luz e tinta separadas pela cor: a luz puxa para o magenta (vermelho − verde sobe), então o brilho é dividido pela luz estimada (`RgbImage.unlit`); a lápide em print recortada passa a ser lida **100%** ("THE QUICK BROWN FOX"). Lápide 1 (foto de tela) ainda troca O por D/E; mais fotos reais (com saturação, contraste e brilho diferentes) para o `RealPhotosTest`.
8. Fase 6b: retomar quando houver mais fotos reais de lápides/pedras (o treino já está pronto para rodar de novo).
9. Fase 7 (tradução), quando decidido.
