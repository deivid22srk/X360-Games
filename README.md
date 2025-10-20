# X360 Games Downloader

Android app para baixar jogos do Xbox 360 do Internet Archive.

## Características

- 📥 Download de arquivos do Internet Archive
- 🔍 Pesquisa de jogos
- 🔐 Login opcional no Internet Archive
- 📱 Material You (Material Design 3)
- 🎨 Suporte a tema escuro/claro
- 📂 Escolha do diretório de download

## Tecnologias

- Kotlin
- Jetpack Compose
- Material 3 (Material You)
- Retrofit 2
- Coroutines
- ViewModel
- DataStore

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
- `WRITE_EXTERNAL_STORAGE` - Para salvar arquivos (Android < 10)
- `READ_MEDIA_*` - Para acessar mídia (Android 13+)

## Como usar

1. Abra o app
2. Aguarde carregar a lista de jogos
3. Use a barra de pesquisa para filtrar
4. Toque em um item para expandir e ver os arquivos
5. Toque no ícone de download para baixar um arquivo
6. (Opcional) Faça login no Internet Archive para acessar conteúdo restrito

## Licença

Este projeto é de código aberto.
