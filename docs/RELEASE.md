# Release do Zu Reader

Há dois fluxos no GitHub Actions:

| Fluxo | Quando | Etapas |
|---|---|---|
| **PR** (`.github/workflows/pr.yml`) | em cada PR (também de forks) | 1 · Testes e lint → 2 · Build dos APKs (ficam nos artefatos da execução) |
| **Release** (`.github/workflows/release.yml`) | merge na `main`, ou à mão em **Actions → Release → Run workflow** | 1 · Build dos APKs → 2 · Publicar release |

As etapas comuns ficam em `.github/actions/preparar` (JDK, Gradle, SDK) e `.github/actions/gerar-apks`.
Como a `main` não roda testes, o que entra nela deve passar por PR (vale ativar a proteção da branch
`main` exigindo o check "1 · Testes e lint").

## Releases

**Cada merge na `main`** publica uma release (`v1.0.0-beta-build.N`) marcada como a mais recente
(**Latest**). Para mudar a versão (ex.: `1.0.0-beta` → `1.0.0`), edite `versionName` em
`app/build.gradle.kts`. O número da build (`versionCode`, e o "build.N" no nome) é a quantidade de
commits: sempre cresce.

Em cada release fica só o **APK para instalar**: `zu-reader.apk` (nome fixo) e o mesmo arquivo como
`zu-reader-<versão>.apk`. O botão de download do README aponta para
`releases/latest/download/zu-reader.apk`, então sempre baixa a versão mais nova. As notas trazem como
instalar (inglês e português) e a lista de mudanças gerada pelo GitHub a partir dos PRs.

O **AAB** (para a Play Store) e o APK de teste ficam só nos **artefatos da execução** (Actions → a
execução → Artifacts → `zu-reader`). Publicar o AAB não seria perigoso — é o mesmo código aberto,
sem nenhuma chave dentro —, mas ninguém instala um AAB no celular: ele só serve para enviar à Play
Store, então fica fora da página de releases.

## Assinatura: teste e produção

| | Chave | App no celular | Onde aparece |
|---|---|---|---|
| **Teste** | do projeto (`app/debug.keystore`, senha pública `android`) | **Zu Reader Teste** (`com.davicolzer.zureader.debug`) | APK dos PRs e do Android Studio |
| **Produção** | de publicação (secrets `ZU_*`, nunca no repositório) | **Zu Reader** (`com.davicolzer.zureader`) | Releases |

Os dois são apps diferentes: o de teste instala **ao lado** do oficial, sem apagar a coleção dele.

A chave de teste está no repositório, então **qualquer pessoa pode assinar um APK com ela**. Por isso
a release **só é publicada com a chave de publicação**: sem os secrets, o fluxo de Release falha de
propósito em vez de publicar um APK com a chave de teste.

> **Ao trocar para a chave de publicação**: o Android não instala um APK assinado com outra chave por
> cima do app. Quem tem o Zu Reader instalado de uma release antiga (assinada com a chave de teste)
> precisa **desinstalar uma vez** (a coleção salva no aparelho é apagada junto) e instalar a versão
> nova. Depois disso, as atualizações voltam a instalar por cima.

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
