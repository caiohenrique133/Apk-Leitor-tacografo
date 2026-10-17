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
