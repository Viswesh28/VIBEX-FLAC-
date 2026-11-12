// Browser-only deterministic fixtures, NOT Android screenshots or production data.
import { chromium } from 'playwright'; import fs from 'node:fs/promises';
const phase=process.argv[2] || 'after'; const root=`../docs/ui-parity/${phase}`;await fs.mkdir(root,{recursive:true});
const image='data:image/svg+xml,'+encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" width="500" height="500"><rect width="500" height="500" fill="#626671"/><circle cx="250" cy="250" r="170" fill="#a9acb1"/><text x="50" y="460" fill="white" font-size="38">TEST ARTWORK</text></svg>');
const songs=Array.from({length:8},(_,i)=>({id:`track${i}`,name:`Test Song ${i}`,duration:120,album:{id:'album',name:'Test Album'},artists:{primary:[{id:'artist',name:'Test Artist'}]},image:[{url:image}],downloadUrl:[{quality:'320kbps',url:'/test.wav'}]}));
const browser=await chromium.launch({args:['--no-sandbox']});
for(const [w,h] of [[360,800],[393,873],[412,915]]){
 const page=await browser.newPage({viewport:{width:w,height:h}});
 await page.route('**/api/**',r=>{const u=new URL(r.request().url());const data=u.pathname.includes('/search/albums')?{results:[{id:'album',name:'Test Album',image:[{url:image}]}]}:u.pathname.includes('/albums')?{id:'album',name:'Test Album',image:[{url:image}],songs}:u.pathname.includes('/songs/')?[songs[0]]:{results:songs};return r.fulfill({json:{success:true,data}})});
 await page.route('https://lrclib.net/**',r=>r.fulfill({json:{trackName:'Test Song 0',artistName:'Test Artist',duration:120,syncedLyrics:'[00:00.00]First line\n[00:05.00]Second line'}}));
 await page.goto('http://127.0.0.1:8000/');await page.locator('.track').first().waitFor();
 const snap=async(name)=>page.screenshot({path:`${root}/${w}x${h}-${name}.png`});
 await snap('home');await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Search'}).click();await snap('search');
 await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Library'}).click();await snap('library');
 await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Downloads'}).click();await snap('downloads');
 await page.getByRole('button',{name:'Settings',exact:true}).click();await snap('settings');
 await page.getByRole('navigation',{name:'Main navigation'}).getByRole('button',{name:'Home'}).click();await page.locator('.track-main').first().click();await page.locator('.mini-info').click();await snap('player');
 await page.getByRole('button',{name:'Lyrics',exact:true}).click();await page.getByRole('dialog',{name:'Lyrics & queue'}).waitFor();await snap('lyrics');
 await page.close();
}
await browser.close(); console.log(`Saved ${phase} browser fixture screenshots to ${root}`);
