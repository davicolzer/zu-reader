# Release do Zu Reader

Há dois fluxos no GitHub Actions, cada um com só as etapas que rodam:

| Fluxo | Quando | Etapas |
|---|---|---|
| **PR** (`.github/workflows/pr.yml`) | em cada PR | 1 · Testes e lint → 2 · Build dos APKs |
| **Release** (`.github/workflows/release.yml`) | merge na `main`, ou à mão em **Actions → Release → Run workflow** | 1 · Build dos APKs → 2 · Publicar release |

As etapas comuns ficam em `.github/actions/preparar` (JDK, Gradle, SDK) e `.github/actions/gerar-apks`.
No PR, o APK fica nos artefatos da execução (`zu-reader`); na `main`, vai para **Releases**.

Como a `main` não roda testes, o que entra nela deve passar por PR.

## Instalar

Na página da release, baixe `zu-reader-<versão>.apk` no celular e abra. Sem chave de publicação
configurada, ele é assinado com a chave do projeto (`app/debug.keystore`, senha pública `android`), então cada versão nova
instala por cima da anterior. O número da build (`versionCode`, e o "build.N" no nome) é a quantidade de commits: sempre cresce, nos dois fluxos.

A chave do projeto é pública e serve só para uso pessoal e testes. Para publicar, use uma chave própria (abaixo).

## Assinar o release

1. Crie uma chave (uma vez só; guarde o arquivo e as senhas fora do repositório):

   ```bash
   keytool -genkeypair -v -keystore zu-release.jks -alias zu-reader \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

2. No GitHub, em **Settings → Secrets and variables → Actions**, crie:

   | Secret | Valor |
   |---|---|
   | `ZU_KEYSTORE_BASE64` | conteúdo de `base64 -w0 zu-release.jks` |
   | `ZU_KEYSTORE_PASSWORD` | senha do keystore |
   | `ZU_KEY_ALIAS` | `zu-reader` (ou o alias usado) |
   | `ZU_KEY_PASSWORD` | senha da chave |

3. O próximo build gera o release assinado.

Para assinar localmente, exporte `ZU_KEYSTORE_PATH` (caminho do `.jks`) e as três variáveis acima e rode `./gradlew bundleRelease`.

## Antes de publicar

- Aumentar `versionCode` e `versionName` em `app/build.gradle.kts`.
- Testar o APK de release num aparelho (câmera, galeria, histórico).
- Nome na loja sem "Fez" (marca registrada) e aviso de projeto de fã.
- Ícone da loja: `docs/design/icon-512.png`.
