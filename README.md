# fez-translate (Zu Reader)

App Android em Kotlin que lê textos no alfabeto Zu, do jogo Fez, a partir de uma foto e traduz o resultado.

- Plano: [docs/PLANO.md](docs/PLANO.md)
- Design: [docs/design/mockup.png](docs/design/mockup.png) (fonte em [mockup.html](docs/design/mockup.html)) e ícone [icon.svg](docs/design/icon.svg)
- Telas no Android: [início](docs/design/home-android.png), [câmera](docs/design/camera-android.png), [recorte](docs/design/crop-android.png), [resultado do recorte](docs/design/crop-result-android.png), [resultado](docs/design/result-android.png) e [título e texto inclinados](docs/design/result-inclinado-android.png)
- Imagens de exemplo para testar pela galeria: [docs/samples/](docs/samples/)

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
app/        Activity, tema, telas (Compose)
core/zu/    Kotlin puro: alfabeto Zu, classificador, ordenação e leitura (testável sem emulador)
core/vision/ Kotlin puro: imagem → glifos (limiar, manchas, caixas, grade 5×5)
tools/      scripts de apoio (tabela de pares de letras do inglês)
```

## Como rodar

Requisitos: JDK 25 e Android SDK com a plataforma 37 (o Android Studio instala).

```bash
./gradlew build                 # compila, roda testes e lint
./gradlew :app:installDebug     # instala no aparelho/emulador conectado
./gradlew :core:zu:test         # só os testes do leitor
```

O CI (`.github/workflows/android.yml`) roda `./gradlew build` em cada push na `main` e em cada PR, e publica o APK de debug como artefato.

## Licenças

Desenhos dos glifos Zu extraídos da [FEZ Pixel Font](https://github.com/TakWolf/fez-pixel-font), de TakWolf (CC0). O alfabeto Zu pertence à Polytron.

Tabela de pares de letras do inglês gerada a partir do corpus Gutenberg do NLTK (livros em domínio público), com `tools/gen_english_bigrams.py`.

Fontes Silkscreen, Figtree e JetBrains Mono sob a SIL Open Font License (ver `third_party/fonts/`).

Projeto de fã, sem vínculo com a Polytron.
