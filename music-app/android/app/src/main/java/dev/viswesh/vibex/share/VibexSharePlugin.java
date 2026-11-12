package dev.viswesh.vibex.share;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.text.*;
import android.text.TextPaint;
import androidx.core.content.FileProvider;
import com.getcapacitor.*;
import com.getcapacitor.annotation.CapacitorPlugin;
import dev.viswesh.vibex.audio.VibeArtwork;
import java.io.*;
import java.util.concurrent.*;
import org.json.*;

/** Generates a bounded card on a worker; Sharesheet receives only a temporary content URI. */
@CapacitorPlugin(name="VibexShare")
public final class VibexSharePlugin extends Plugin {
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  @PluginMethod public void shareCard(PluginCall call) {
    JSObject song=call.getObject("song"); String shape=call.getString("shape","story");
    if(song==null || !(shape.equals("story") || shape.equals("square")) || song.toString().length()>65536){call.reject("Invalid share request");return;}
    worker.execute(()->{
      Bitmap card=null, art=null;
      try {
        Context c=getContext(); File dir=new File(c.getCacheDir(),"share-cards"); if(!dir.exists()&&!dir.mkdirs())throw new IOException("Cannot create cache");
        File[] old=dir.listFiles(); if(old!=null)for(File f:old)if(f.isFile()&&System.currentTimeMillis()-f.lastModified()>24*60*60*1000L)f.delete();
        int w=1080,h=shape.equals("square")?1080:1920; card=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(card); Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new LinearGradient(0,0,w,h,0xff24172f,0xff76586c,Shader.TileMode.CLAMP));canvas.drawRect(0,0,w,h,p);p.setShader(null);
        String img="";JSONArray images=song.optJSONArray("image");if(images!=null&&images.length()>0){JSONObject last=images.optJSONObject(images.length()-1);if(last!=null)img=last.optString("url");}
        try {
          Uri remote=Uri.parse(img); String host=remote.getHost();
          if("https".equals(remote.getScheme()) && host!=null && (host.equals("saavncdn.com")||host.endsWith(".saavncdn.com")||host.endsWith(".jiosaavn.com"))) {
            Uri uri=Uri.parse(VibeArtwork.cached(c,img));
            if("file".equals(uri.getScheme())) {
              File file=new File(uri.getPath());File allowed=new File(c.getFilesDir(),"artwork");
              if(file.getCanonicalPath().startsWith(allowed.getCanonicalPath()+File.separator)) {
                BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);
                o.inSampleSize=1;while(Math.max(o.outWidth,o.outHeight)/o.inSampleSize>1200)o.inSampleSize*=2;
                o.inJustDecodeBounds=false;art=BitmapFactory.decodeFile(file.getPath(),o);
              }
            }
          }
        } catch(Exception ignored) { }
        int margin=86, size=shape.equals("square")?520:900, top=shape.equals("square")?75:210;
        RectF dest=new RectF(margin,top,margin+size,top+size);
        p.setColor(0xff4e3a59);canvas.drawRoundRect(dest,28,28,p);
        if(art!=null){
          float scale=Math.max(size/(float)art.getWidth(),size/(float)art.getHeight());
          float sw=size/scale, sx=(art.getWidth()-sw)/2, sy=(art.getHeight()-sw)/2;
          canvas.save(); Path clip=new Path();clip.addRoundRect(dest,28,28,Path.Direction.CW);canvas.clipPath(clip);
          canvas.drawBitmap(art,new Rect((int)sx,(int)sy,(int)(sx+sw),(int)(sy+sw)),dest,p);canvas.restore();
        }
        int tx=shape.equals("square")?660:margin, tw=shape.equals("square")?330:908;
        int ty=shape.equals("square")?185:1160;
        drawText(canvas,song.optString("name","Untitled"),tx,ty,tw,shape.equals("square")?54:68,3,0xffffffff);
        String artist="";JSONObject artists=song.optJSONObject("artists");JSONArray primary=artists==null?null:artists.optJSONArray("primary");
        if(primary!=null)for(int i=0;i<Math.min(primary.length(),3);i++){JSONObject a=primary.optJSONObject(i);if(a!=null){if(!artist.isEmpty())artist+=", ";artist+=a.optString("name");}}
        if(artist.isEmpty())artist=song.optString("subtitle","");
        drawText(canvas,artist,tx,ty+(shape.equals("square")?220:265),tw,36,2,0xffeadce8);
        // Only explicit user-authored lyric text is accepted; external lyrics are not redistributed.
        String lyric=call.getString("manualLyric",""); if(lyric!=null&&!lyric.trim().isEmpty())drawText(canvas,lyric.trim(),margin,shape.equals("square")?720:1510,w-2*margin,38,3,0xfff0e5f0);
        drawText(canvas,"VIBEX",margin,h-120,w-2*margin,43,1,0xffffffff);
        File out=File.createTempFile("vibex-share-",".png",dir); try(FileOutputStream os=new FileOutputStream(out)){if(!card.compress(Bitmap.CompressFormat.PNG,100,os))throw new IOException("Image encoding failed");}
        Uri uri=FileProvider.getUriForFile(c,c.getPackageName()+".sharecards",out);
        Intent send=new Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri("VIBEX",uri));
        getActivity().runOnUiThread(()->{try{getActivity().startActivity(Intent.createChooser(send,"Share music"));call.resolve();}catch(Exception e){call.reject("No compatible sharing app",e);}});
      } catch(Exception e){call.reject("Could not create share card",e);}
      finally {if(art!=null)art.recycle();if(card!=null)card.recycle();}
    });
  }
  private static void drawText(Canvas canvas,String text,int x,int y,int width,int size,int maxLines,int color){
    TextPaint paint=new TextPaint(Paint.ANTI_ALIAS_FLAG);paint.setColor(color);paint.setTextSize(size);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
    StaticLayout layout=StaticLayout.Builder.obtain(text,0,text.length(),paint,width).setMaxLines(maxLines).setEllipsize(TextUtils.TruncateAt.END).setIncludePad(false).build();
    canvas.save();canvas.translate(x,y);layout.draw(canvas);canvas.restore();
  }
  @Override protected void handleOnDestroy(){worker.shutdownNow();}
}
