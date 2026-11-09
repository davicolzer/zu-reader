# Release do Zu Reader

Há dois fluxos no GitHub Actions:

| Fluxo | Quando | Etapas |
|---|---|---|
| **PR** (`.github/workflows/pr.yml`) | em cada PR (também de forks) | 1 · Testes e lint → 2 · Build dos APKs (ficam nos artefatos da execução) |
| **Release** (`.github/workflows/release.yml`) | merge na `main`, ou à mão em **Actions → Release → Run workflow** | 1 · Build dos APKs → 2 · Publicar release |

As etapas comuns ficam em `.github/actions/preparar` (JDK, Gradle, SDK) e `.github/actions/gerar-apks`.
Como a `main` não roda testes, o que entra nela deve passar por PR (vale ativar a proteção da branch
`main` exigindo o check "1 · Testes e lint").

## Pré-release e versão estável

- **Cada merge na `main`** publica uma **pré-release** (`v1.0.0-beta-build.N`): serve para testar.
- **Versão estável**: em **Actions → Release → Run workflow**, marque **"Versão estável"**. A release
  sai sem a marca de pré-release e vira a **Latest** da página de releases (é a que o botão de download
  do README mostra primeiro). As notas listam os PRs desde a última versão estável.

Antes de uma versão estável, mude `versionName` em `app/build.gradle.kts` (ex.: `1.0.0-beta` → `1.0.0`).
O número da build (`versionCode`, e o "build.N" no nome) é a quantidade de commits: sempre cresce.

Em cada release ficam só o **APK para instalar** (`zu-reader-<versão>.apk`) e o **AAB** para a Play
Store. As notas trazem como instalar (inglês e português) e a lista de mudanças gerada pelo GitHub
a partir dos PRs. O APK de depuração e o sem assinatura ficam só nos artefatos da execução.

## Assinatura

Sem chave de publicação configurada, o APK é assinado com a **chave do projeto**
(`app/debug.keystore`, senha pública `android`). Ela serve para desenvolvimento: como está no
repositório, **qualquer pessoa pode assinar um APK com ela**, e esse APK instalaria por cima do app
de quem baixou daqui. Para o repositório público, configure uma **chave de publicação** (abaixo).

> **Atenção ao trocar de chave**: o Android não instala um APK assinado com outra chave por cima do
> app. Quem já tem o app instalado com a chave do projeto precisa **desinstalar uma vez** (a coleção
> salva no aparelho é apagada junto) e instalar a versão nova. Depois disso, as atualizações voltam a
> instalar por cima.

### Criar a chave de publicação

1. Crie a chave (uma vez só; guarde o arquivo e as senhas **fora do repositório**, num lugar seguro —
   sem ela não dá para publicar atualizações):

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

3. O próximo build gera o release assinado com ela. Os secrets não ficam disponíveis para PRs de
   forks, então a chave nunca sai do fluxo de release da `main`.

Para assinar localmente, exporte `ZU_KEYSTORE_PATH` (caminho do `.jks`) e as três variáveis acima e
rode `./gradlew bundleRelease`.

## Antes de publicar na Play Store

- Testar o APK de release num aparelho (câmera, galeria, coleção).
- Nome na loja sem "Fez" (marca registrada) e aviso de projeto de fã.
- Ícone da loja: `docs/design/icon-512.png`.
