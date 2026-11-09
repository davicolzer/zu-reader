<p align="center">
  <img src="docs/design/icon-512.png" width="96" alt="Ícone do Zu Reader">
</p>

<h1 align="center">Zu Reader</h1>

<p align="center">
  Leia e escreva <b>Zu</b>, o alfabeto do jogo <i>FEZ</i>, com a câmera do celular.<br>
  Um app Android de código aberto, feito por fãs.
</p>

<p align="center">
  <a href="../../releases"><img alt="Download" src="https://img.shields.io/github/v/release/davicolzer/zu-reader?label=download&color=d7263d"></a>
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3ddc84">
  <a href="LICENSE"><img alt="Licença: MIT" src="https://img.shields.io/badge/licen%C3%A7a-MIT-blue"></a>
</p>

<p align="center">
  <a href="README.md">English</a> · <b>Português</b>
</p>

<p align="center">
  <img src="docs/screenshots/home-pt.png" width="24%" alt="Tela inicial">
  <img src="docs/screenshots/result-pt.png" width="24%" alt="Foto lida como texto">
  <img src="docs/screenshots/write-pt.png" width="24%" alt="Escrevendo em Zu">
  <img src="docs/screenshots/commands-pt.png" width="24%" alt="Sequência de comandos">
</p>

## O que o app faz

- **Ler Zu**: tire uma foto (ou escolha da galeria), recorte em volta do texto e veja o texto em inglês. Cada glifo fica marcado na foto; toque numa letra para corrigir.
- **Números**: lê também os números do Zu (0–10). O modo automático separa letras e números sozinho.
- **Escrever à mão**: digite com um teclado de glifos (ou de letras) e veja o texto desenhado em Zu, com cursor como num campo de texto.
- **Comandos**: lê as sequências de comandos em peças de Tetris do jogo, coladas ou separadas, em linha ou em coluna, e mostra o botão de cada peça (↑ ↓ ← →, A, LT, RT). Dá também para corrigir ou montar sequências à mão.
- **QR code, anotações e vídeo**: lê QR codes, desenha sobre fotos e grava até 10 minutos do jogo para ler os quadros depois.
- **Coleção**: salve o que leu ou escreveu, com nome e descrição, e compartilhe como imagem e texto.
- Tema claro e escuro, inglês e português. **Funciona sem internet**: sem conta, sem anúncios, sem rastreamento.

## Baixar

<p>
  <a href="https://github.com/davicolzer/zu-reader/releases/latest/download/zu-reader.apk"><img alt="Baixar o APK" src="https://img.shields.io/badge/Baixar-APK-d7263d?style=for-the-badge&logo=android&logoColor=white"></a>
</p>

1. No celular, toque em **Baixar APK** acima (ou pegue na [release mais recente](https://github.com/davicolzer/zu-reader/releases/latest)).
2. Abra o arquivo. Se o Android pedir, permita instalar apps dessa fonte.
3. Versões novas instalam por cima da anterior e mantêm a coleção.

Precisa de Android 8.0 ou mais novo.

<details>
<summary>Conferir se o APK é o oficial</summary>

Os APKs oficiais vêm só das Releases deste repositório e são assinados com este certificado (SHA-256):

```
75:83:05:03:22:DA:39:AF:A9:B6:A4:01:B1:D9:EE:D2:67:08:52:C2:C1:BF:39:34:01:67:5E:88:FE:15:AD:10
```

Confira com `apksigner verify --print-certs zu-reader.apk`. Uma impressão digital diferente quer dizer que o arquivo não veio daqui; não instale.
</details>

## Sugestões e problemas

Achou um problema, ou o app leu algo errado? Tem uma ideia? **[Abra uma issue](../../issues/new/choose)** (pode ser em português). Fotos em que o app lê errado ajudam muito.

- [Relatar um erro](../../issues/new?template=bug_report.yml)
- [Sugerir uma função](../../issues/new?template=feature_request.yml)

## Créditos e licenças

- Código: [MIT](LICENSE).
- Desenhos dos glifos Zu da [FEZ Pixel Font](https://github.com/TakWolf/fez-pixel-font), de TakWolf (CC0).
- Pares de letras do inglês gerados a partir do corpus Gutenberg do NLTK (livros em domínio público), com `tools/gen_english_bigrams.py`.
- Fontes Silkscreen, Figtree e JetBrains Mono, sob a SIL Open Font License (ver `third_party/fonts/`).
- As fotos em `testdata/real/` são capturas do *FEZ*, usadas só para testar o leitor.

**Projeto de fã, sem vínculo com a Polytron.** O *FEZ* e o alfabeto Zu pertencem à Polytron Corporation. O ícone e a arte do app são originais.

<sub>Quer compilar o app? Veja [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) (em inglês).</sub>
