// Local deterministic browser-only inspection. Not an Android frame benchmark.
import { chromium } from '@playwright/test';
const browser = await chromium.launch({headless:true,args:['--no-sandbox']});
const page = await browser.newPage({viewport:{width:320,height:800}});
const songs=Array.from({length:8},(_,i)=>({id:`test-${i}`,name:`Test song ${i}`,duration:120,album:{name:'Test album'},artists:{primary:[{name:'Test artist'}]},image:[]}));
await page.route('**/api/**',async r=>r.fulfill({json:{success:true,data:{results:songs}}}));
await page.goto('http://127.0.0.1:8000/');
await page.locator('.track').first().waitFor();
for(const width of [320,360,393,412]) {
  await page.setViewportSize({width,height:800});
  const values=await page.evaluate(()=>{
    const read=(sel)=>{const e=document.querySelector(sel);if(!e)return null;const r=e.getBoundingClientRect(),s=getComputedStyle(e);return {font:s.fontSize,width:+r.width.toFixed(1),height:+r.height.toFixed(1)};};
    return {width:innerWidth,documentWidth:document.documentElement.scrollWidth,heroTag:read('.hero-tag'),heroDescription:read('.hero-copy p'),artCaption:read('.art-caption > span'),trackMeta:read('.track-info > span'),trackAction:read('.track .icon-button'),bottomNav:read('.bottom-nav > button')};
  });
  console.log(JSON.stringify(values));
}
await browser.close();
