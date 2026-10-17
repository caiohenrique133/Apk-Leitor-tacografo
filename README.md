# APK Leitor tacografo
Leitor de dados tacografo

App **Controle de Tacógrafo — Caio (v2.3)**, publicada como PWA no GitHub Pages:
https://caiohenrique133.github.io/Apk-Leitor-tacografo/

O APK Android é gerado em https://www.pwabuilder.com a partir desse endereço.

## Vercel

O repositório está pronto para o Vercel sem compilação: em vercel.com → **Add New → Project** → importar
`Apk-Leitor-tacografo` → Framework Preset **Other**, Build Command e Output Directory vazios → **Deploy**.
O `vercel.json` impede que o `sw.js`, o `manifest.json` e o `index.html` fiquem presos em cache,
para que cada push chegue logo ao telemóvel.

## App Android

A pasta `android/` tem a app nativa: abre a versão do Vercel e dá-lhe acesso direto ao leitor de cartões USB
(ponte `window.AndroidUsb`), que o Chrome bloqueia em páginas web. Cada alteração em `android/` é compilada
pelo GitHub Actions e publicada em **Releases**:
https://github.com/caiohenrique133/Apk-Leitor-tacografo/releases/latest/download/Tacografo.apk
