/* Gera dist/: a versão que vai para o Vercel e para a app Android.
   O código fonte legível fica na raiz do repositório; em dist/ o JavaScript é ofuscado
   e o HTML/CSS minificado, para que não seja fácil copiar ou alterar a app. */
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import JavaScriptObfuscator from 'javascript-obfuscator';
import {minify as terser} from 'terser';
import CleanCSS from 'clean-css';
import {minify as htmlMin} from 'html-minifier-terser';

const RAIZ=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const DIST=path.join(RAIZ,'dist');
const ANO=new Date().getFullYear();
const AVISO=`/* Controle de Tacógrafo © ${ANO}. Todos os direitos reservados. Proibida a cópia, modificação ou redistribuição sem autorização. */`;

fs.rmSync(DIST,{recursive:true,force:true});
fs.mkdirSync(path.join(DIST,'icons'),{recursive:true});

const ofusca=code=>JavaScriptObfuscator.obfuscate(code,{
  target:'browser',compact:true,
  renameGlobals:false,               // funções globais são chamadas pelo HTML e pela app Android
  identifierNamesGenerator:'hexadecimal',
  stringArray:true,stringArrayEncoding:['base64'],stringArrayThreshold:0.8,
  stringArrayRotate:true,stringArrayShuffle:true,splitStrings:false,
  controlFlowFlattening:false,deadCodeInjection:false,  // mantém a app rápida no telemóvel
  selfDefending:false,debugProtection:false,unicodeEscapeSequence:false,
  transformObjectKeys:false,numbersToExpressions:false,simplify:true
}).getObfuscatedCode();

/* index.html */
let html=fs.readFileSync(path.join(RAIZ,'index.html'),'utf8');
const css=new CleanCSS({level:1});
html=html.replace(/<style>([\s\S]*?)<\/style>/g,(_,c)=>'<style>'+css.minify(c).styles+'</style>');
const blocos=[];
html=html.replace(/<script>([\s\S]*?)<\/script>/g,(_,c)=>{blocos.push(c);return `<script>@@JS${blocos.length-1}@@</script>`});
for(let i=0;i<blocos.length;i++){
  const c=blocos[i];
  const out=c.length>5000?AVISO+ofusca(c):(await terser(c,{compress:true,mangle:true})).code;
  if(out.includes('</script'))throw new Error('bloco '+i+' contém </script');
  blocos[i]=out;
}
html=await htmlMin(html,{collapseWhitespace:true,conservativeCollapse:true,removeComments:true,minifyCSS:false,minifyJS:false});
html=html.replace(/@@JS(\d+)@@/g,(_,i)=>blocos[+i]);
fs.writeFileSync(path.join(DIST,'index.html'),html);

/* service worker */
const sw=fs.readFileSync(path.join(RAIZ,'sw.js'),'utf8');
fs.writeFileSync(path.join(DIST,'sw.js'),(await terser(sw,{compress:true,mangle:true})).code);

/* ficheiros estáticos */
fs.copyFileSync(path.join(RAIZ,'manifest.json'),path.join(DIST,'manifest.json'));
for(const f of fs.readdirSync(path.join(RAIZ,'icons')))fs.copyFileSync(path.join(RAIZ,'icons',f),path.join(DIST,'icons',f));

const kb=f=>(fs.statSync(f).size/1024).toFixed(0)+' KB';
console.log('dist/ pronto · index.html',kb(path.join(RAIZ,'index.html')),'→',kb(path.join(DIST,'index.html')));
