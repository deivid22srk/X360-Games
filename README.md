# X360 Games Downloader

Android app para baixar jogos do Xbox 360 do Internet Archive.

## Características

- 📥 Download de arquivos do Internet Archive
- 🔍 Pesquisa de jogos por nome
- 🔐 Login via WebView no Internet Archive
- 🔔 Notificações de progresso de download
- ⚙️ Tela de configurações para escolher pasta de download
- 📱 Material You (Material Design 3) com Dynamic Colors
- 🎨 Suporte a tema escuro/claro
- ⚡ Otimização para listas grandes (até 100 arquivos por item)

## Tecnologias

- Kotlin
- Jetpack Compose
- Material 3 (Material You)
- Retrofit 2
- Coroutines
- ViewModel
- DataStore
- WebView
- Notifications
- DocumentFile (SAF)

## Requisitos

- Android 7.0 (API 24) ou superior
- Conexão com a internet

## Build

```bash
./gradlew assembleDebug
```

O APK será gerado em: `app/build/outputs/apk/debug/app-debug.apk`

## Permissões

- `INTERNET` - Para baixar arquivos
- `ACCESS_NETWORK_STATE` - Para verificar conexão
- `POST_NOTIFICATIONS` - Para notificações de download (Android 13+)
- `WRITE_EXTERNAL_STORAGE` - Para salvar arquivos (Android < 10)
- `READ_MEDIA_*` - Para acessar mídia (Android 13+)

## Como usar

1. Abra o app
2. Aguarde carregar a lista de jogos
3. Use a barra de pesquisa para filtrar por nome
4. Toque em um item para expandir e ver os arquivos
5. Toque no ícone de download para baixar um arquivo
6. Acompanhe o progresso na notificação
7. (Opcional) Faça login no Internet Archive via WebView para acessar conteúdo restrito
8. (Opcional) Acesse Settings para alterar a pasta de download

## Funcionalidades

### Download com Notificações
- Notificação em tempo real com barra de progresso
- Notificação ao completar download
- Notificação de erro em caso de falha

### Tela de Configurações
- Seletor de pasta personalizada usando Storage Access Framework
- Persistência da escolha usando DataStore

### Login Internet Archive
- WebView integrada para login seguro
- Captura automática de cookies
- Suporte a downloads de arquivos restritos

## Licença

Este projeto é de código aberto.
