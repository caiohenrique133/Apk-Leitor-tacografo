# APK Leitor tacografo
Leitor de dados tacografo

App **Controle de Tacógrafo (v4.4)**, publicada como PWA no Vercel:
https://apk-leitor-tacografo.vercel.app/

Sem dados pessoais no código: quem abre a app pela primeira vez cria a sua conta de condutor no ecrã de
boas-vindas. Cada aparelho pode ter várias contas, cada uma com os seus próprios dados (notas, ficheiros do
cartão e fotos dos discos), guardados só nesse aparelho.

## Vercel

O repositório está pronto para o Vercel sem compilação: em vercel.com → **Add New → Project** → importar
`Apk-Leitor-tacografo` → Framework Preset **Other**, Build Command e Output Directory vazios → **Deploy**.
O `vercel.json` impede que o `sw.js`, o `manifest.json` e o `index.html` fiquem presos em cache,
para que cada push chegue logo ao telemóvel.

## App Android

A pasta `android/` tem a app nativa: abre a versão do Vercel e dá-lhe acesso direto ao leitor de cartões USB
(ponte `window.AndroidUsb`), que o Chrome bloqueia em páginas web. Cada alteração em `android/` é compilada
pelo GitHub Actions e publicada em **Releases**:
https://apk-leitor-tacografo.vercel.app/Tacografo.apk
(o GitHub Actions coloca cada APK novo em `apk/` e o Vercel publica-o; o repositório é privado)

## Código fonte e versão publicada

- Código fonte legível: `index.html`, `sw.js`, `manifest.json`, `icons/` e `android/` (raiz do repositório).
- Versão publicada: o Vercel corre `node tools/build.mjs`, que gera `dist/` com o JavaScript ofuscado e o
  HTML/CSS minificado. É essa versão que o site e a app Android abrem.
- Para gerar `dist/` no computador: `npm ci --prefix tools` e depois `node tools/build.mjs`.
- Licença: todos os direitos reservados (ver `LICENSE`).
