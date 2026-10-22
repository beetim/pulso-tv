<p align="center">
  <img src="docs/img/logo.png" alt="pulso.tv" width="120">
</p>

<h1 align="center">pulso.tv</h1>

<p align="center">
  <b>A Twitch feita para a sua TV.</b><br>
  Cliente de código aberto para Android TV e Google TV, pensado para o controle remoto.
</p>

<p align="center">
  <a href="https://github.com/beetim/pulso-tv/releases/latest"><img alt="Último release" src="https://img.shields.io/github/v/release/beetim/pulso-tv?color=FFC800&labelColor=18181B"></a>
  <img alt="Android 7+" src="https://img.shields.io/badge/Android-7.0%2B-3D6BFF?labelColor=18181B">
  <img alt="Licença AGPL-3.0" src="https://img.shields.io/badge/licen%C3%A7a-AGPL--3.0-EFEFF1?labelColor=18181B">
</p>

<p align="center">
  <img src="docs/img/home.png" alt="Home do pulso.tv" width="85%">
</p>

## Por que o pulso.tv

O app oficial da Twitch na TV é pesado e pouco pensado para quem assiste do sofá. O pulso.tv é um
cliente leve, com a interface inteira desenhada para o D-pad: tudo se alcança com as setas e o OK,
sem precisar de mouse, toque ou teclado.

- **Sem anúncios do app e sem rastreamento.** Nenhuma telemetria, nenhuma conta própria, nenhuma
  propaganda. O app só conversa com a Twitch e com os serviços de emotes.
- **Feito para o controle remoto.** O foco nunca se perde: cada tela tem um ponto de partida claro
  e o botão Voltar faz o que você espera.
- **Gratuito e de código aberto** (AGPL-3.0). Você pode ler, auditar e compilar o app você mesmo.

## Recursos

| | |
|---|---|
| **Home em prateleiras** | Continuar assistindo, Seguindo, Jogos, Mais vistos e Recomendados. |
| **Canais no seu idioma primeiro** | Recomendações priorizam o seu país; nos jogos, "Canais em português" vêm antes de "Outros canais", com **Carregar mais**. |
| **Player completo** | Qualidade **Automático** (adaptativa) ou fixa, proporção, temporizador de sono, mudo e tela cheia. |
| **Chat na tela** | Emotes e badges da Twitch, 7TV, BTTV e FFZ; envie mensagens quando estiver logado. |
| **Canais, vídeos e clipes** | Página do canal com vídeos recentes e **Clipes**; lista dos canais que você segue, mesmo offline. |
| **Busca** | Canais, ao vivo e categorias, com histórico. |
| **Login por QR code** | Leia o código com o celular e pronto, sem digitar senha na TV. |
| **Retomar vídeos** | Vídeos gravados continuam de onde você parou. |
| **Atualização integrada** | O app avisa quando sai uma versão nova e instala com um clique. |
| **Português e inglês** | Troque o idioma direto na Home. |

## Requisitos

- Android TV ou Google TV com **Android 7.0** ou superior.
- Controle remoto com D-pad. Touchscreen não é necessário.

## Instalação

Baixe o **`Pulso.apk`** mais recente na página de
[Releases](https://github.com/beetim/pulso-tv/releases/latest) e escolha um dos caminhos abaixo.

### Opção 1 · pelo PC, com adb

1. Na TV, abra **Configurações → Sistema → Sobre** e aperte 7 vezes em **Compilação** para ativar
   o modo desenvolvedor.
2. Em **Opções do desenvolvedor**, ligue **Depuração USB** e **Depuração por rede**
   (o nome muda conforme o fabricante). Anote o IP da TV em **Rede**.
3. No PC, com o [adb](https://developer.android.com/tools/releases/platform-tools) instalado:

```bash
adb connect <ip-da-tv>:5555
adb install -r Pulso.apk
```

Aceite o pedido de autorização que aparece na TV na primeira conexão.

### Opção 2 · pelo app Downloader, direto na TV

1. Instale o app **Downloader** (AFTVnews) pela loja da TV.
2. Nas configurações da TV, permita **instalar apps desconhecidos** para o Downloader.
3. No Downloader, digite o endereço do `Pulso.apk` na página de Releases e instale.

### Opção 3 · pendrive

Copie o `Pulso.apk` para um pendrive, ligue na TV e abra o arquivo por um gerenciador de arquivos
(permitindo instalar apps desconhecidos para ele).

Depois de instalado, o app aparece no launcher como **pulso.tv**.

## Atualizações

O pulso.tv confere os Releases deste repositório ao abrir a Home. Quando há versão nova, aparece
**Atualizar vX.Y.Z** no topo da tela: o app baixa o APK e abre o instalador do Android, onde você
só confirma. Também dá para conferir na hora em **Perfil → Verificar atualizações**.

Atualizar por cima mantém login e configurações, desde que o APK tenha a mesma assinatura.

## Login

Escolha **Entrar**. A tela mostra um código e um QR: leia com o celular e autorize na conta Twitch
que estiver aberta no navegador dele. A mesma conta passa a valer na TV.

## Problemas comuns

- **"App não instalado" / conflito de assinatura:** desinstale a versão antiga antes de instalar
  um APK assinado com outra chave (o login será perdido).
- **`adb: device unauthorized`:** aceite a autorização na tela da TV e repita o comando.
- **Pediu login de novo:** o token da Twitch pode expirar; entre novamente pelo QR.
- **O vídeo demora a abrir logo depois de ligar a TV:** a rede ainda está acordando; costuma
  normalizar em poucos segundos.

## Compilar do código-fonte

Precisa de JDK 21 e do Android SDK. No Windows (PowerShell):

```powershell
$env:JAVA_HOME='<pasta-do-jdk-21>'
$env:ANDROID_HOME='<pasta-do-android-sdk>'
.\gradlew.bat assembleRelease --no-daemon
```

O APK sai em `app/build/outputs/apk/release/app-release.apk`. A assinatura de release lê o
`keystore.properties` e um `.jks` que **não** vêm no repositório. Crie o seu:

```bash
keytool -genkey -v -keystore pulso.jks -alias pulso -keyalg RSA -keysize 2048 -validity 10000
```

e preencha o `keystore.properties` conforme o `app/build.gradle.kts`. Sem ele, o build usa a chave
de depuração. Para o atualizador apontar para o seu fork, mude `pulso.githubRepo` no
`gradle.properties`.

Notas de desenvolvimento: [`docs/CLAUDE.md`](docs/CLAUDE.md) e [`docs/HANDOFF.md`](docs/HANDOFF.md).

## Aviso legal

pulso.tv é um projeto independente e **não é afiliado, patrocinado nem endossado pela Twitch**.
"Twitch" e as demais marcas pertencem aos seus donos. Use por sua conta e risco e respeite os
termos de uso da Twitch.

## Créditos e licença

O pulso.tv é construído sobre o motor do **[Xtra](https://github.com/andreyasadchy/xtra)**, o cliente
Twitch para Android de **Andrey Asadchy**, sob a [GNU Affero General Public License v3.0](LICENSE).

Do Xtra vem a camada de dados e reprodução: ExoPlayer/media3 com HLS de baixa latência, as APIs
GraphQL e Helix da Twitch, o chat (IRC, WebSocket, EventSub, PubSub), emotes de terceiros (7TV,
BTTV, FFZ), seguidores, buscas e o banco local em Room. A interface do pulso.tv (home, busca, canal,
jogo, player e chat) é nova, em Compose, e não reutiliza nenhuma tela do Xtra. O texto completo dos
créditos fica em `app/src/main/java/com/pulso/tv/PulsoCredits.kt`.

Como o Xtra é AGPL-3.0, o pulso.tv também é: quem distribuir este aplicativo precisa publicar o
código-fonte e manter estes créditos.
