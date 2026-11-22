# ClipLocal — Android

Aplicativo nativo Kotlin para baixar vídeos **públicos** de links de posts do X/Twitter para o celular.

## Instalar pelo GitHub Actions (sem PC)

1. Abra a aba **Actions** deste repositório.
2. Selecione **Compilar APK Android** e abra a execução mais recente; o workflow inicia automaticamente em cada push para a branch `main`.
3. Ao terminar com status verde, abra a execução e baixe o arquivo de artefato **ClipLocal-APK-debug**.
4. Extraia o ZIP baixado; nele está `app-debug.apk`.
5. Abra o APK no Android e permita a instalação de aplicativos dessa origem, se solicitado.

## Funcionalidades

- Colar links do X/Twitter ou compartilhar texto diretamente com ClipLocal.
- Download, barra de progresso, cancelamento e abertura do último vídeo.
- Armazenamento local em `Downloads/ClipLocal`.
- Botão para atualizar yt-dlp caso os extratores fiquem desatualizados.

## Requisitos

Android 10 (API 29) ou superior; acesso à Internet para fazer os downloads. A primeira compilação baixa dependências do Maven Central.

## Observações

Alguns vídeos exigem autenticação no X e não são baixáveis sem login. O app não solicita cookies nem senhas. O APK de debug é para instalação e testes, não é uma versão de produção assinada para a Play Store.

Código licenciado sob GPL-3.0; utiliza yt-dlp/FFmpeg via youtubedl-android, respeitando licenças de dependências. Baixe apenas vídeos que você tem permissão de armazenar.
