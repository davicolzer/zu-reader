# Release do Zu Reader

## Caminho de uma mudança

```
branch da mudança ──PR──▶ staging ──PR──▶ main ──▶ (depois) Play Store
                   testes   │       testes   │
                            ▼                ▼
                   APK de staging     release oficial
                   (para testar)      (Latest, download do README)
```

1. A mudança vai num **PR para a `staging`**. O CI roda testes e lint.
2. **Merge na `staging`** → o APK de teste é publicado na pré-release fixa **staging**. Link que não muda:
   **https://github.com/davicolzer/zu-reader/releases/download/staging/zu-reader-staging.apk**
   (também na página da [pré-release staging](https://github.com/davicolzer/zu-reader/releases/tag/staging),
   que lista o que está na staging e ainda não está na main). Instala como **Zu Reader Staging**,
   ao lado do app oficial, sem mexer na coleção dele.
3. Testou no celular e está bom → **PR da `staging` para a `main`**. O merge publica a release oficial.
4. **Deploy na Play Store**: fica para depois de criar as contas (Play Console e verificação de
   desenvolvedor); entra como uma etapa a mais no fluxo da `main`.

| Fluxo | Quando | Etapas |
|---|---|---|
| **PR** (`.github/workflows/pr.yml`) | em cada PR (também de forks) | 1 · Testes e lint → 2 · Build dos APKs (ficam nos artefatos da execução) |
| **Staging** (`.github/workflows/staging.yml`) | merge na `staging`, ou à mão em **Actions → Staging → Run workflow** | 1 · Build do APK de staging → 2 · Publicar na pré-release staging |
| **Release** (`.github/workflows/release.yml`) | merge na `main`, ou à mão em **Actions → Release → Run workflow** | 1 · Build dos APKs → 2 · Publicar release |

As etapas comuns ficam em `.github/actions/preparar` (JDK, Gradle, SDK) e `.github/actions/gerar-apks`.
Como `staging` e `main` não rodam testes no merge, o que entra nelas deve passar por PR (proteja as
duas branches exigindo o check "1 · Testes e lint").

### Os três apps

| Build | App no celular | Chave | De onde vem |
|---|---|---|---|
| `debug` | **Zu Reader Teste** (`…zureader.debug`) | de teste | Android Studio e artefatos dos PRs |
| `staging` | **Zu Reader Staging** (`…zureader.staging`) | de publicação | pré-release **staging** |
| `release` | **Zu Reader** (`com.davicolzer.zureader`) | de publicação | releases oficiais |

O de staging é igual ao oficial (otimizado com R8, mesma chave), só muda o pacote e o nome: o que
você testa nele é o que vai para a `main`.

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

## Verificação de desenvolvedor do Android (Google)

Desde **30/09/2026**, no **Brasil**, na Indonésia, em Singapura e na Tailândia (no mundo todo em 2027),
os celulares Android certificados conferem se o **nome do pacote + chave de assinatura** do app estão
registrados por um desenvolvedor verificado. Um APK **não registrado** só instala pelo "fluxo
avançado" (aviso de "desenvolvedor não verificado") ou por ADB. Registrado, instala normalmente.

Dados do app oficial:

| | |
|---|---|
| Pacote | `com.davicolzer.zureader` |
| Certificado (SHA-256) | `75:83:05:03:22:DA:39:AF:A9:B6:A4:01:B1:D9:EE:D2:67:08:52:C2:C1:BF:39:34:01:67:5E:88:FE:15:AD:10` |

Passos (uma vez só):

1. Criar a conta no **[Android Developer Console](https://android.google.com/developerconsole/developers)**
   como **distribuição completa** (*full distribution*): instalações ilimitadas; pede documento com
   foto, comprovante de endereço e taxa única de US$ 25. A conta de **distribuição limitada** (grátis,
   sem documento) só vale para até 20 aparelhos convidados, então não serve para download público.
   Quem já tem conta no **Play Console** usa a mesma (página "Android developer verification").
2. Em **Packages → Register package name**: informar `com.davicolzer.zureader` e, em **Add key**, o
   SHA-256 acima.
3. Para provar que a chave é sua, o console mostra um trecho de texto. Salve-o como
   **`app/src/main/assets/adi-registration.properties`** (o conteúdo não é segredo: ele vai dentro do
   APK público), faça o merge na `main`, baixe o `zu-reader.apk` da release gerada e envie em
   **Upload APK** no console.
4. Quando o status mudar para **Registered**, o APK das releases instala sem aviso nesses países.

O app de teste (`com.davicolzer.zureader.debug`) não precisa ser registrado: ele é só para quem
desenvolve, e pode ser instalado pelo fluxo avançado ou por ADB.

Fonte: [developer.android.com/developer-verification](https://developer.android.com/developer-verification).

## Antes de publicar na Play Store

- Testar o APK de release num aparelho (câmera, galeria, coleção).
- Nome na loja sem "Fez" (marca registrada) e aviso de projeto de fã.
- Ícone da loja: `docs/design/icon-512.png`.
