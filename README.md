# fez-translate (Zu Reader)

App Android em Kotlin que lê textos no alfabeto Zu, do jogo Fez, a partir de uma foto e mostra o texto em inglês (o Zu é uma cifra do alfabeto latino).

- Plano: [docs/PLANO.md](docs/PLANO.md)
- Design: [docs/design/mockup.png](docs/design/mockup.png) (fonte em [mockup.html](docs/design/mockup.html)) e ícone [icon.svg](docs/design/icon.svg)
- Telas no Android: [início](docs/design/home-android.png), [câmera](docs/design/camera-android.png), [recorte](docs/design/crop-android.png), [resultado do recorte](docs/design/crop-result-android.png), [resultado](docs/design/result-android.png) e [título e texto inclinados](docs/design/result-inclinado-android.png), [correção](docs/design/correction-android.png), [leitura salva](docs/design/result-saved-android.png) e [recentes](docs/design/home-recent-android.png) , [letras sobre a foto](docs/design/result-letras-android.png), [escrever à mão](docs/design/escrever-texto-android.png), [seletor Auto/Escrita/Números](docs/design/home-auto-android.png), [modo números](docs/design/home-numeros-android.png) e [números lidos](docs/design/result-numeros-android.png)
- Imagens de exemplo para testar pela galeria: [docs/samples/](docs/samples/)
- Fotos reais do jogo usadas no teste de regressão: [testdata/real/](testdata/real/) (imagens do jogo FEZ, © Polytron; repositório privado)
- Release e assinatura: [docs/RELEASE.md](docs/RELEASE.md)
- Tema claro: [início](docs/design/home-light-android.png) e [resultado](docs/design/result-light-android.png); [configurações](docs/design/settings-android.png)

## Versões

| Ferramenta | Versão |
|---|---|
| JDK | 25 (LTS) |
| Gradle | 9.8.2 |
| Kotlin | 2.4.21 |
| Android Gradle Plugin | 9.5.1 |
| Compose BOM | 2026.09.00 |
| compileSdk / targetSdk | 37 |
| minSdk | 26 (Android 8.0) |

O Gradle roda no JDK 25. O bytecode é gerado para Java 21, que é o que o Android aceita.

## Estrutura

```
app/         o aplicativo Android
core/zu/     Kotlin puro: alfabeto Zu, classificador, ordenação e leitura (testável sem emulador)
core/vision/ Kotlin puro: imagem → glifos (limiar, manchas, caixas, grade 5×5), QR, escrita em Zu
tools/       scripts de apoio (tabela de pares de letras do inglês)
```

Dentro de `app/src/main/kotlin/com/davicolzer/zureader/`:

```
MainActivity, ZuReaderApplication   entrada do app (idioma, tema, tela cheia de fotos)
data/        coleção (Room), configurações (DataStore), idioma, letras aprendidas, tamanho das fotos
media/       carregar e endireitar fotos, reduzir vídeos a 720p
ui/          ZuReaderApp (troca de telas com o cubo) e ReaderViewModel (estado de todas as telas)
ui/components/ peças repetidas: barra de cima, botões, diálogos, foto com zoom, tela cheia, cubo
ui/theme/    cores, fontes e tema claro/escuro
ui/<tela>/   uma pasta por tela: home, camera (com os analisadores da câmera), crop, result,
             correct, write, qr, note, video, history, settings
```

## Como rodar

Requisitos: JDK 25 e Android SDK com a plataforma 37 (o Android Studio instala).

```bash
./gradlew build                 # compila, roda testes e lint
./gradlew :app:installDebug     # instala no aparelho/emulador conectado
./gradlew :core:zu:test         # só os testes do leitor
```

CI em dois fluxos: **PR** (testes e lint → build dos APKs) e **Release** (no merge na `main`: build dos APKs → release com o APK para instalar). Ver `docs/RELEASE.md`.

## Licenças

Desenhos dos glifos Zu extraídos da [FEZ Pixel Font](https://github.com/TakWolf/fez-pixel-font), de TakWolf (CC0). O alfabeto Zu pertence à Polytron.

Tabela de pares de letras do inglês gerada a partir do corpus Gutenberg do NLTK (livros em domínio público), com `tools/gen_english_bigrams.py`.

Fontes Silkscreen, Figtree e JetBrains Mono sob a SIL Open Font License (ver `third_party/fonts/`).

Projeto de fã, sem vínculo com a Polytron.
