# pulso.tv — Twitch para Android TV

Cliente Twitch feito para a TV: navegação por controle remoto (D-pad), prateleiras na
home, player com chat na tela, clipes, busca e login por QR code. Toda a interface é em
Jetpack Compose, desenhada para 1920x1080 (Android TV / Google TV, Android 7+, API 24+).

<!-- Prints: coloque os arquivos em docs/img/ -->
![Home](docs/img/home.png)
![Canal](docs/img/canal.png)
![Player](docs/img/player.png)

## Recursos

- Home com prateleiras: Continuar assistindo, Seguindo, Jogos, Mais vistos e Recomendados
  (canais do seu país/idioma primeiro).
- Jogos com a prateleira "Canais em português", botão **Carregar mais** e "Outros canais".
- Player com qualidade **Automático**, proporção, temporizador de sono e chat na tela.
- Enviar mensagens no chat (logado), com emotes Twitch, 7TV, BTTV e FFZ.
- Canal com vídeos recentes e **Clipes**; lista de canais que você segue.
- Busca de canais, ao vivo e categorias, com histórico.
- Configurações: qualidade padrão, tamanho do texto do chat.
- Idiomas: português (BR) e inglês.

## Requisitos

- Android TV ou Google TV com Android 7.0 ou superior.
- Controle remoto com D-pad. Não precisa de touchscreen.

## Instalação

Baixe o `Pulso.apk` mais recente na página de **Releases** do repositório e use um dos
caminhos abaixo.

### Opção 1 — pelo PC, com adb (a mais simples)

1. Na TV: **Configurações → Sistema → Sobre**, aperte 7 vezes em **Compilação** para ativar o
   modo desenvolvedor.
2. Em **Opções do desenvolvedor**, ligue **Depuração USB** e **Depuração por rede**
   (o nome varia por fabricante). Anote o IP da TV em **Rede**.
3. No PC, com o [adb](https://developer.android.com/tools/releases/platform-tools) instalado:

```bash
adb connect <ip-da-tv>:5555
adb install -r Pulso.apk
```

Aceite o pedido de autorização que aparece na TV na primeira conexão.

### Opção 2 — pelo app Downloader, direto na TV

1. Instale o app **Downloader** (AFTVnews) pela loja da TV.
2. Em **Configurações** da TV, permita instalar apps desconhecidos para o Downloader.
3. No Downloader, digite o endereço do `Pulso.apk` da página de Releases e instale.

### Opção 3 — pendrive

Copie o `Pulso.apk` para um pendrive, ligue na TV e abra o arquivo por um gerenciador de
arquivos (permitindo instalar apps desconhecidos para ele).

Depois de instalado, o app aparece no launcher como **pulso.tv**. Para atualizar, instale o
APK novo por cima (`adb install -r`): login e configurações são mantidos, desde que o APK
tenha sido assinado com a mesma chave.

## Login

Escolha **Entrar**. A tela mostra um código e um QR: leia com o celular e autorize na conta
Twitch que estiver logada no navegador do celular. A mesma conta passa a valer na TV.

## Problemas comuns

- **"App não instalado" / conflito de assinatura:** desinstale o app antigo antes de instalar
  um APK assinado com outra chave (você perde o login).
- **`adb: device unauthorized`:** aceite a autorização na tela da TV e rode o comando de novo.
- **Pediu login de novo:** o token da Twitch pode expirar; entre novamente pelo QR.
- **Vídeo demora a abrir logo após ligar a TV:** a rede ainda está acordando; costuma
  normalizar em poucos segundos.
- **Anúncios:** ainda não há tratamento especial para anúncios da Twitch.

## Compilar do código-fonte

Precisa de JDK 21 e Android SDK. No Windows (PowerShell):

```powershell
$env:JAVA_HOME='<pasta-do-jdk-21>'
$env:ANDROID_HOME='<pasta-do-android-sdk>'
.\gradlew.bat assembleRelease --no-daemon
```

O APK sai em `app/build/outputs/apk/release/app-release.apk`. A assinatura de release lê
`keystore.properties` e um `.jks` que **não** vêm no repositório: crie o seu
(`keytool -genkey -v -keystore pulso.jks -alias pulso -keyalg RSA -keysize 2048 -validity 10000`)
e preencha o `keystore.properties` conforme o `app/build.gradle.kts`.

Notas de desenvolvimento ficam em [`docs/CLAUDE.md`](docs/CLAUDE.md) e
[`docs/HANDOFF.md`](docs/HANDOFF.md).

## Aviso legal

pulso.tv é um projeto independente e **não é afiliado, patrocinado nem endossado pela
Twitch**. "Twitch" e as demais marcas pertencem aos seus donos. Use por sua conta e risco e
respeite os termos de uso da Twitch.

## Créditos e licença

pulso.tv é construído sobre o motor do **Xtra**, o cliente Twitch para Android de
**Andrey Asadchy** (<https://github.com/andreyasadchy/xtra>), sob a
[GNU Affero General Public License v3.0](LICENSE).

Do Xtra vem a camada de dados e reprodução: ExoPlayer/media3 com HLS de baixa latência,
as APIs GraphQL e Helix da Twitch, o chat (IRC, WebSocket, EventSub, PubSub), emotes de
terceiros (7TV, BTTV, FFZ), seguidores, buscas e o banco local em Room. A interface do
pulso.tv — home, busca, canal, jogo, player e chat — é nova, em Compose, e não reutiliza
nenhuma tela do Xtra.

O texto completo dos créditos fica no cabeçalho de
`app/src/main/java/com/pulso/tv/PulsoCredits.kt`.

Como o Xtra é AGPL-3.0, o pulso.tv também é: quem distribuir este aplicativo precisa
publicar o código-fonte e manter estes créditos.
