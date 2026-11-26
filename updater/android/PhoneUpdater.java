package com.moe.offline;

import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import android.util.AtomicFile;
import org.json.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.security.spec.*;
import java.math.BigInteger;
import java.util.*;

public final class PhoneUpdater {
 static final String REPO="vistrae/Memories-of-Eternity", PKG="com.nexon.moe.global", FORMAT="unity5-global-51";
 static final int CLIENT=27;
 static final String MODULUS="qivjJ5OkgDAM050wsGwCRBraDmgrfDVG1URnnjrUlMhVyBhfe28pQ4zpjMcfJuL70V0wey0za9KKulxBrF/o9c7EtAVwZignltYRCLIrUCa7rGZHExJ4K1HaVzfdUX8kcDKNt84nNErn+IkKYCteSCDGR0qrih5KHAFXeh96jjDRY/VnMqsnICYjyrJA8BntXih7FxV22dTjmeNrcjBWatJfnOXmvXbaI7mX3now97LHZ6JDGr5ExETp8E0E48tAycVz5qxXgmuL2VKV9TfTjFi/uZKmC4CrmSbPP5mn/36uNUaaAcMEVZi+Hx3p8Wg63QXLeYmG7xogWuNpZzJbifBI30jYT58I64LPQCKDlweZSz9Wwdsg04GHA0S+rjG0uby1Q+Ljt/hYlRqyyEv27HSCeL4/II0DP6kQlW3oJ7AVfBEof1NPSxGKjoOK9QBO+LeeK3ArkWU+Le5qnPDjQQAQ0QHksR2hhIZ9UyvOpk/5/yoYf3LSAF8h+ynEKHSR";
 static boolean attached, busy;
 static Activity host;
 static Button updateButton;
 static File root;
 static ProgressDialog progress;
 // A code-drawn title control keeps Android accessibility and the existing touch bounds.
 // Chamfered edges, cyan trim and blue scanlines follow the original game panels.
 static final class UpdateButtonSkin extends android.graphics.drawable.Drawable {
  final float density;
  final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
  final android.graphics.Path shape=new android.graphics.Path();
  final android.graphics.Path inner=new android.graphics.Path();
  final android.graphics.RectF frame=new android.graphics.RectF();
  final android.graphics.RectF body=new android.graphics.RectF();
  int opacity=255;
  boolean pressed,focused,enabled=true;
  UpdateButtonSkin(float density){this.density=density;}
  float dp(float value){return value*density;}
  void bevel(android.graphics.Path path,android.graphics.RectF bounds,float cut){
   path.reset();path.moveTo(bounds.left+cut,bounds.top);path.lineTo(bounds.right-cut,bounds.top);
   path.lineTo(bounds.right,bounds.top+cut);path.lineTo(bounds.right,bounds.bottom-cut);
   path.lineTo(bounds.right-cut,bounds.bottom);path.lineTo(bounds.left+cut,bounds.bottom);
   path.lineTo(bounds.left,bounds.bottom-cut);path.lineTo(bounds.left,bounds.top+cut);path.close();
  }
  void color(int color,android.graphics.Paint.Style style,float width){
   paint.setShader(null);paint.setColor(color);paint.setAlpha(android.graphics.Color.alpha(color)*opacity/255);
   paint.setStyle(style);paint.setStrokeWidth(width);
  }
  public boolean isStateful(){return true;}
  protected boolean onStateChange(int[] states){
   boolean nextPressed=false,nextFocused=false,nextEnabled=false;
   for(int state:states){
    if(state==android.R.attr.state_pressed)nextPressed=true;
    if(state==android.R.attr.state_focused||state==android.R.attr.state_hovered)nextFocused=true;
    if(state==android.R.attr.state_enabled)nextEnabled=true;
   }
   if(pressed==nextPressed&&focused==nextFocused&&enabled==nextEnabled)return false;
   pressed=nextPressed;focused=nextFocused;enabled=nextEnabled;invalidateSelf();return true;
  }
  public void draw(android.graphics.Canvas canvas){
   android.graphics.Rect bounds=getBounds();
   frame.set(bounds.left+dp(4),bounds.top+dp(5),bounds.right-dp(4),bounds.bottom-dp(5));
   if(frame.width()<=dp(12)||frame.height()<=dp(12))return;
   float cut=Math.min(dp(6),frame.height()*.22f);
   bevel(shape,frame,cut);
   color(0xb3001635,android.graphics.Paint.Style.STROKE,dp(4));canvas.drawPath(shape,paint);
   color(enabled?0x5072eaff:0x304e728a,android.graphics.Paint.Style.STROKE,dp(2.8f));canvas.drawPath(shape,paint);
   color(enabled?0xff35d9ff:0xff50778f,android.graphics.Paint.Style.FILL,0);canvas.drawPath(shape,paint);
   body.set(frame);body.inset(dp(1.2f),dp(1.2f));bevel(inner,body,Math.max(dp(2),cut-dp(.6f)));
   paint.setStyle(android.graphics.Paint.Style.FILL);paint.setAlpha(opacity);
   paint.setShader(new android.graphics.LinearGradient(0,body.top,0,body.bottom,
    enabled?(pressed?new int[]{0xff085e99,0xff073969,0xff051d40}:new int[]{0xff138dcb,0xff075a98,0xff052b5c}):
     new int[]{0xff234c68,0xff153850,0xff10283d},new float[]{0,.48f,1},android.graphics.Shader.TileMode.CLAMP));
   canvas.drawPath(inner,paint);
   int saved=canvas.save();canvas.clipPath(inner);
   color(enabled?0x174adfff:0x103d748e,android.graphics.Paint.Style.STROKE,dp(.5f));
   for(float y=body.top+dp(4);y<body.bottom;y+=dp(3))canvas.drawLine(body.left,y,body.right,y,paint);
   color(enabled?0xff95edff:0xff53778c,android.graphics.Paint.Style.STROKE,dp(.8f));
   canvas.drawLine(body.left+cut,body.top+dp(.9f),body.right-cut,body.top+dp(.9f),paint);
   color(0xb0001a3b,android.graphics.Paint.Style.STROKE,dp(1));
   canvas.drawLine(body.left+cut,body.bottom-dp(1),body.right-cut,body.bottom-dp(1),paint);
   color(enabled?0xff65eaff:0xff527c92,android.graphics.Paint.Style.STROKE,dp(1.3f));
   canvas.drawLine(body.left+cut+dp(2),body.top+dp(2.4f),body.left+cut+dp(14),body.top+dp(2.4f),paint);
   canvas.drawLine(body.right-cut-dp(10),body.bottom-dp(2.4f),body.right-cut-dp(2),body.bottom-dp(2.4f),paint);
   canvas.restoreToCount(saved);
   color(enabled?(focused?0xffe1fcff:pressed?0xffc2faff:0xff5ce5ff):0xff527c92,
    android.graphics.Paint.Style.STROKE,dp(focused?1.8f:.85f));canvas.drawPath(shape,paint);
  }
  public void setAlpha(int alpha){opacity=Math.max(0,Math.min(255,alpha));invalidateSelf();}
  public void setColorFilter(android.graphics.ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
  public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
 }
 static void styleUpdateButton(Button button){
  int left=button.getPaddingLeft(),top=button.getPaddingTop(),right=button.getPaddingRight(),bottom=button.getPaddingBottom();
  int minWidth=button.getMinWidth(),minHeight=button.getMinHeight();
  int viewMinWidth=button.getMinimumWidth(),viewMinHeight=button.getMinimumHeight();
  button.setBackground(new UpdateButtonSkin(button.getResources().getDisplayMetrics().density));
  button.setPadding(left,top,right,bottom);button.setMinWidth(minWidth);button.setMinHeight(minHeight);
  button.setMinimumWidth(viewMinWidth);button.setMinimumHeight(viewMinHeight);
  button.setStateListAnimator(null);button.setElevation(0);button.setAlpha(1f);
  button.setText("UPDATE");button.setAllCaps(false);button.setTextSize(11);button.setSingleLine(true);
  button.setTypeface(android.graphics.Typeface.create("sans-serif-condensed",android.graphics.Typeface.BOLD));
  button.setLetterSpacing(.045f);button.setGravity(Gravity.CENTER);
  button.setTextColor(new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[0]},new int[]{0xff8da9bb,0xffffffff}));
  float density=button.getResources().getDisplayMetrics().density;
  button.setShadowLayer(density,0,density,0xff001c3b);button.setContentDescription("Update");
 }
 static void ui(Runnable r){host.runOnUiThread(r);}
 static void message(final String s){ui(new Runnable(){public void run(){new AlertDialog.Builder(host).setTitle("Game updates").setMessage(s).setPositiveButton("OK",null).show();}});}
 public static void attach(final Activity a){a.runOnUiThread(new Runnable(){public void run(){
  if(attached&&host==a&&updateButton!=null&&updateButton.getParent()!=null)return;
  if(updateButton!=null&&updateButton.getParent() instanceof ViewGroup)((ViewGroup)updateButton.getParent()).removeView(updateButton);
  attached=true;host=a;root=new File(a.getFilesDir(),"moe-updates");root.mkdirs();
  if(PKG.equals(a.getPackageName()))try{SaveSlots.recover(a);}catch(Exception error){
   new AlertDialog.Builder(a).setTitle("Local save recovery").setMessage(error.getMessage()+"\n\nExisting progress was not overwritten. Save-slot switching is unavailable until recovery succeeds.").setPositiveButton("OK",null).show();
  }
  Button b=new Button(a);updateButton=b;b.setText("Update");b.setTextSize(11);styleUpdateButton(b);
  android.widget.FrameLayout.LayoutParams p=new android.widget.FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);
  a.addContentView(b,p);b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){menu();}});
  if(new File(root,"pending-manifest.json").exists())new Handler().postDelayed(new Runnable(){public void run(){finishPending();}},1200);
 }});}
 public static void setVisible(final Activity a,final boolean visible){a.runOnUiThread(new Runnable(){public void run(){if(updateButton!=null)updateButton.setVisibility(visible?View.VISIBLE:View.GONE);}});}
 static void menu(){
  final boolean helper=!host.getPackageName().equals(PKG);
  new AlertDialog.Builder(host).setTitle(helper?"MOE Update Installer":"Offline playtest B"+CLIENT)
   .setMessage("Play offline at any time. Checking downloads only the files needed for an update. Your save stays on this phone.")
   .setPositiveButton("Check for updates",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){check();}})
   .setNeutralButton(helper?"Installation details":"Restore assets",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){if(helper)installationDetails();else restore();}})
   .setNegativeButton("Keep playing",null).show();
 }
 static boolean hasCertificates(PackageInfo p){
  if(p==null||p.signatures==null||p.signatures.length==0)return false;
  for(android.content.pm.Signature s:p.signatures)if(s==null||s.toByteArray().length==0)return false;
  return true;
 }
 static String signerFingerprints(PackageInfo p)throws Exception{
  if(!hasCertificates(p))return "Unavailable";
  ArrayList<String> values=new ArrayList<String>();
  for(android.content.pm.Signature s:p.signatures)values.add(hex(MessageDigest.getInstance("SHA-256").digest(s.toByteArray())));
  Collections.sort(values);StringBuilder result=new StringBuilder();
  for(String value:values){if(result.length()>0)result.append("\n");result.append(value);}return result.toString();
 }
 static String describePackage(PackageInfo p)throws Exception{
  if(p==null)return "Package could not be read.";
  return "Package: "+p.packageName+"\nVersion: "+p.versionName+" (code "+p.versionCode+")\nCertificate SHA-256:\n"+signerFingerprints(p);
 }
 static void installationDetails(){try{
  PackageManager pm=host.getPackageManager();PackageInfo game=null;
  try{game=pm.getPackageInfo(PKG,PackageManager.GET_SIGNATURES);}catch(PackageManager.NameNotFoundException absent){}
  final String details="MOE Update Installer B"+CLIENT+"\nAndroid: "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")\n\nInstalled game:\n"+
   (game==null?PKG+" is not installed.":describePackage(game))+"\n\nInstaller:\n"+describePackage(pm.getPackageInfo(host.getPackageName(),PackageManager.GET_SIGNATURES));
  new AlertDialog.Builder(host).setTitle("Installation details").setMessage(details)
   .setPositiveButton("Copy details",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){
    ClipboardManager clipboard=(ClipboardManager)host.getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("MOE installation details",details));Toast.makeText(host,"Details copied",Toast.LENGTH_SHORT).show();
   }}).setNegativeButton("Close",null).show();
 }catch(Exception e){message("Could not read installation details: "+e.getMessage());}}
 interface Work{void run() throws Exception;}
 static synchronized void worker(final Work task){if(busy)return;busy=true;
  progress=new ProgressDialog(host);progress.setMessage("Checking update files...");progress.setCancelable(false);progress.show();
  new Thread(new Runnable(){public void run(){try{task.run();}catch(final Exception e){android.util.Log.w("MOEUpdater","Update failed",e);message("Update unavailable: "+e.getMessage()+"\n\nYour installed game and save are retained. You can keep playing offline.");}finally{ui(new Runnable(){public void run(){progress.dismiss();busy=false;}});}}},"MOE-updater").start();}
 static byte[] read(File f) throws Exception{FileInputStream in=new FileInputStream(f);try{return drain(in,2*1024*1024);}finally{in.close();}}
 static byte[] drain(InputStream in,int cap)throws Exception{ByteArrayOutputStream o=new ByteArrayOutputStream();byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1){if(o.size()+n>cap)throw new IOException("Response too large");o.write(b,0,n);}return o.toByteArray();}
 static HttpURLConnection connect(String location,boolean api)throws Exception{
  URL url=new URL(location);if(!url.getProtocol().equals("https"))throw new IOException("HTTPS required");
  if(api){if(!location.equals("https://api.github.com/repos/"+REPO+"/releases/latest"))throw new IOException("Invalid update service");}
  else if(!url.getHost().equals("github.com")||!url.getPath().startsWith("/"+REPO+"/releases/download/"))throw new IOException("Invalid release location");
  for(int i=0;i<6;i++){
   HttpURLConnection c=(HttpURLConnection)url.openConnection();c.setConnectTimeout(15000);c.setReadTimeout(45000);c.setInstanceFollowRedirects(false);c.setRequestProperty("User-Agent","MOE-Offline-Updater");
   int code=c.getResponseCode();if(code>=300&&code<400){String next=c.getHeaderField("Location");c.disconnect();URL n=new URL(url,next);String h=n.getHost();if(!n.getProtocol().equals("https")||!(h.equals("github.com")||h.equals("release-assets.githubusercontent.com")||h.equals("objects.githubusercontent.com")))throw new IOException("Unexpected release redirect");url=n;continue;}
   if(code!=200){c.disconnect();throw new IOException("Server returned "+code);}return c;
  }throw new IOException("Too many redirects");
 }
 static byte[] get(String url,boolean api,int cap)throws Exception{HttpURLConnection c=connect(url,api);try{InputStream in=c.getInputStream();try{return drain(in,cap);}finally{in.close();}}finally{c.disconnect();}}
 static String sha(File f)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");FileInputStream in=new FileInputStream(f);try{byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1)md.update(b,0,n);}finally{in.close();}return hex(md.digest());}
 static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(java.util.Locale.US,"%02x",v&255));return b.toString();}
 static void atomic(File f,byte[] b)throws Exception{f.getParentFile().mkdirs();AtomicFile a=new AtomicFile(f);FileOutputStream out=a.startWrite();try{out.write(b);a.finishWrite(out);}catch(Exception e){a.failWrite(out);throw e;}}
 static void copy(File from,File to)throws Exception{to.getParentFile().mkdirs();FileInputStream in=new FileInputStream(from);try{FileOutputStream out=new FileOutputStream(to);try{byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.getFD().sync();}finally{out.close();}}finally{in.close();}}
 static void path(String p)throws Exception{if(p.length()==0||p.length()>240||!p.matches("[A-Za-z0-9_./-]+"))throw new IOException("Invalid asset path");for(String s:p.split("/",-1))if(s.equals("")||s.equals(".")||s.equals(".."))throw new IOException("Invalid asset path");String l=p.toLowerCase(java.util.Locale.US);if(l.endsWith(".dll")||l.endsWith(".dex")||l.endsWith(".so")||l.endsWith(".apk")||l.endsWith(".exe")||l.endsWith(".mp4"))throw new IOException("Unsupported asset type");}
 static void spec(JSONObject s)throws Exception{String h=s.getString("sha256");long n=s.getLong("size");if(!h.matches("[0-9a-f]{64}")||n<1||n>2147483647L)throw new IOException("Invalid file description");URL u=new URL(s.getString("url"));if(!u.getProtocol().equals("https")||!u.getHost().equals("github.com")||!u.getPath().startsWith("/"+REPO+"/releases/download/"))throw new IOException("Wrong file repository");}
 static void verifyApkVariants(JSONObject m)throws Exception{
  if(!m.has("apk_variants")||m.isNull("apk_variants"))return;
  JSONArray variants=m.getJSONArray("apk_variants");
  if(variants.length()>16)throw new IOException("Too many APK signing variants");
  if(variants.length()>0&&(!m.has("apk")||m.isNull("apk")))throw new IOException("APK signing variants require a default APK");
  Set<String> certificates=new HashSet<String>(),hashes=new HashSet<String>(),urls=new HashSet<String>();
  for(int i=0;i<variants.length();i++){
   JSONObject variant=variants.getJSONObject(i);String certificate=variant.getString("signing_certificate_sha256");
   if(!certificate.matches("[0-9a-f]{64}"))throw new IOException("Invalid APK signing certificate fingerprint");
   spec(variant);
   if(!certificates.add(certificate)||!hashes.add(variant.getString("sha256"))||!urls.add(variant.getString("url")))throw new IOException("Duplicate APK signing variant");
  }
 }
 static JSONObject chooseApkForInstalled(JSONObject m,PackageInfo installed)throws Exception{
  if(!m.has("apk")||m.isNull("apk"))return null;
  if(!hasCertificates(installed))throw new IOException("Android did not return the installed game's signing certificate. No APK was selected.\n\nInstalled:\n"+describePackage(installed));
  if(installed.signatures.length!=1)throw new IOException("This updater requires one installed signing certificate. No APK was selected.\n\nInstalled:\n"+describePackage(installed));
  String certificate=signerFingerprints(installed);
  if(m.has("apk_variants")&&!m.isNull("apk_variants")){
   JSONArray variants=m.getJSONArray("apk_variants");
   for(int i=0;i<variants.length();i++){JSONObject variant=variants.getJSONObject(i);if(certificate.equals(variant.getString("signing_certificate_sha256")))return variant;}
  }
  return m.getJSONObject("apk");
 }
 static JSONObject selectedApk(JSONObject m)throws Exception{
  if(!m.has("apk")||m.isNull("apk"))return null;
  PackageInfo installed;
  try{installed=host.getPackageManager().getPackageInfo(PKG,PackageManager.GET_SIGNATURES);}catch(PackageManager.NameNotFoundException absent){throw new IOException("The game "+PKG+" is not installed. Keep your existing game if it uses another package ID.");}
  return chooseApkForInstalled(m,installed);
 }
 static JSONObject verify(byte[] bytes,byte[] sig)throws Exception{
  PublicKey key=KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(new BigInteger(1,android.util.Base64.decode(MODULUS,0)),BigInteger.valueOf(65537)));
  java.security.Signature v=java.security.Signature.getInstance("SHA256withRSA");v.initVerify(key);v.update(bytes);if(!v.verify(android.util.Base64.decode(sig,0)))throw new IOException("Update signature is invalid");
  JSONObject m=new JSONObject(new String(bytes,"UTF-8").replaceFirst("^\\uFEFF",""));
  if(m.getInt("schema_version")!=1||!m.getString("application_id").equals(PKG)||!m.getString("asset_format").equals(FORMAT)||!m.getString("channel").equals("stable")||m.getInt("save_schema_version")!=1||m.getInt("min_updater_version")>1||m.getLong("sequence")<1||m.getLong("required_client_sequence")>m.getLong("sequence"))throw new IOException("Incompatible update");
  if(!m.getString("release_id").matches("[A-Za-z0-9_.-]{1,100}"))throw new IOException("Invalid release ID");
  if(m.has("apk")&&!m.isNull("apk"))spec(m.getJSONObject("apk"));verifyApkVariants(m);
  JSONArray a=m.getJSONArray("assets"),removed=m.getJSONArray("remove_assets");if(a.length()+removed.length()>900)throw new IOException("Too many assets");Set<String> seen=new HashSet<String>();
  for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);String p=x.getString("path");path(p);if(!seen.add(p))throw new IOException("Duplicate asset");spec(x);}
  for(int i=0;i<removed.length();i++){String p=removed.getString(i);path(p);if(!seen.add(p))throw new IOException("Duplicate removal");}
  return m;
 }
 static File download(JSONObject s)throws Exception{spec(s);File dir=new File(root,"objects");dir.mkdirs();File f=new File(dir,s.getString("sha256"));long size=s.getLong("size");if(f.exists()&&f.length()==size&&sha(f).equals(f.getName()))return f;
  if(dir.getUsableSpace()<size+16*1024*1024)throw new IOException("Not enough free storage");File part=new File(dir,f.getName()+".partial");HttpURLConnection c=connect(s.getString("url"),false);
  try{InputStream in=c.getInputStream();try{FileOutputStream out=new FileOutputStream(part);try{byte[] b=new byte[262144];long total=0;int n;while((n=in.read(b))!=-1){total+=n;if(total>size)throw new IOException("Download too large");out.write(b,0,n);}out.getFD().sync();}finally{out.close();}}finally{in.close();}
   if(part.length()!=size||!sha(part).equals(f.getName()))throw new IOException("Download verification failed");if(f.exists()&&!f.delete())throw new IOException("Cannot replace cached file");if(!part.renameTo(f))throw new IOException("Cannot store verified file");return f;
  }finally{c.disconnect();part.delete();}
 }
 static void check(){worker(new Work(){public void run()throws Exception{
  JSONObject release=new JSONObject(new String(get("https://api.github.com/repos/"+REPO+"/releases/latest",true,2*1024*1024),"UTF-8"));JSONArray a=release.getJSONArray("assets");String manifest=null,signature=null;
  for(int i=0;i<a.length();i++){JSONObject x=a.getJSONObject(i);if(x.getString("name").equals("moe-update.json"))manifest=x.getString("browser_download_url");if(x.getString("name").equals("moe-update.json.sig"))signature=x.getString("browser_download_url");}
  if(manifest==null||signature==null)throw new IOException("Release files are missing");final byte[] bytes=get(manifest,false,2*1024*1024),sig=get(signature,false,16384);final JSONObject m=verify(bytes,sig);
  long installed=host.getPackageName().equals(PKG)?CLIENT:0;File active=new File(root,"active.json");if(active.exists())installed=Math.max(installed,new JSONObject(new String(read(active),"UTF-8")).optLong("sequence"));
  if(m.getLong("sequence")<=installed){message("Your playtest is up to date (B"+installed+").");return;}
  final String desc=m.optString("changelog","An update is available.");ui(new Runnable(){public void run(){new AlertDialog.Builder(host).setTitle("Update B"+m.optLong("sequence")).setMessage(desc+"\n\nDownload now? Your save will be kept.").setNegativeButton("Keep playing",null).setPositiveButton("Download",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){stage(m,bytes,sig);}}).show();}});
 }});}
 static void backup()throws Exception{File dest=new File(root,"backups/"+System.currentTimeMillis());dest.mkdirs();File prefs=new File(host.getApplicationInfo().dataDir,"shared_prefs");File[] files=prefs.listFiles();if(files!=null)for(File f:files)if(f.isFile())copy(f,new File(dest,"shared_prefs/"+f.getName()));File index=new File(root,"active.json");if(index.exists()){copy(index,new File(dest,"active.json"));atomic(new File(root,"previous-active.json"),read(index));}else atomic(new File(root,"previous-active.json"),"null".getBytes("UTF-8"));}
 static void stage(final JSONObject m,final byte[] bytes,final byte[] sig){worker(new Work(){public void run()throws Exception{
  JSONObject apkSpec=selectedApk(m);File apk=apkSpec==null?null:download(apkSpec);JSONArray assets=m.getJSONArray("assets");for(int i=0;i<assets.length();i++)download(assets.getJSONObject(i));
  if(m.getLong("required_client_sequence")>CLIENT&&apk==null)throw new IOException("A newer app is required");backup();
  if(apk!=null){validateApk(apk);atomic(new File(root,"pending-manifest.json"),bytes);atomic(new File(root,"pending-manifest.sig"),sig);final File ready=apk;ui(new Runnable(){public void run(){install(ready);}});}
  else{activate(m);message("Asset update ready. Close and reopen the game to use it. Your save is retained.");}
 }});}
 static void activate(JSONObject m)throws Exception{
  if(m.getLong("required_client_sequence")>CLIENT)throw new IOException("New client not installed yet");
  File active=new File(root,"active.json");JSONObject index=active.exists()?new JSONObject(new String(read(active),"UTF-8")):new JSONObject();JSONObject files=index.optJSONObject("files"),hashes=index.optJSONObject("hashes");if(files==null)files=new JSONObject();if(hashes==null)hashes=new JSONObject();
  JSONArray assets=m.getJSONArray("assets");String release="releases/"+m.getLong("sequence")+"-"+m.getString("release_id")+"/";
  for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);String p=a.getString("path"),h=a.getString("sha256");File source=download(a),dest=new File(root,release+p);copy(source,dest);if(!sha(dest).equals(h))throw new IOException("Asset staging failed");files.put(p,release+p);hashes.put(p,h);}
  JSONArray removed=m.getJSONArray("remove_assets");for(int i=0;i<removed.length();i++){files.remove(removed.getString(i));hashes.remove(removed.getString(i));}
  index.put("schema_version",1);index.put("asset_format",FORMAT);index.put("sequence",m.getLong("sequence"));index.put("release_id",m.getString("release_id"));index.put("files",files);index.put("hashes",hashes);atomic(active,index.toString().getBytes("UTF-8"));
 }
 public static void finishPending(){worker(new Work(){public void run()throws Exception{File f=new File(root,"pending-manifest.json"),s=new File(root,"pending-manifest.sig");if(!f.exists()||!s.exists())return;JSONObject m=verify(read(f),read(s));if(m.getLong("required_client_sequence")>CLIENT)return;JSONObject apkSpec=selectedApk(m);if(apkSpec!=null&&!sha(new File(host.getApplicationInfo().sourceDir)).equals(apkSpec.getString("sha256")))return;activate(m);f.delete();s.delete();message("Update completed. Reopen the game to load updated assets.");}});}
 static void checkApkIdentity(PackageInfo next,PackageInfo current)throws Exception{
  String details="\n\nInstalled:\n"+describePackage(current)+"\n\nDownloaded:\n"+describePackage(next);
  if(current==null)throw new IOException("The installed game could not be inspected."+details);
  if(next==null)throw new IOException("Android could not read the downloaded APK, including an .apk filename retry."+details);
  if(!PKG.equals(next.packageName))throw new IOException("The downloaded APK has a different package ID."+details);
  if(next.versionCode!=current.versionCode)throw new IOException("The installed and downloaded version codes differ. This update requires the same game version code."+details);
  if(!hasCertificates(current))throw new IOException("Android did not return the installed game's signing certificate. Installation was stopped."+details);
  if(!hasCertificates(next))throw new IOException("Android did not return the downloaded APK's signing certificate. Installation was stopped."+details);
  if(current.signatures.length!=1||next.signatures.length!=1)throw new IOException("This updater requires one installed and one downloaded signing certificate. Installation was stopped."+details);
  if(!Arrays.equals(next.signatures,current.signatures))throw new IOException("The installed game and downloaded APK use different signing certificates. Keep the installed game; do not uninstall it before preserving your save."+details);
 }
 static void validateApk(File f)throws Exception{
  PackageManager pm=host.getPackageManager();PackageInfo current;
  try{current=pm.getPackageInfo(PKG,PackageManager.GET_SIGNATURES);}catch(PackageManager.NameNotFoundException absent){throw new IOException("The game "+PKG+" is not installed. Keep your existing game if it uses another package ID.");}
  PackageInfo next=pm.getPackageArchiveInfo(f.getPath(),PackageManager.GET_SIGNATURES);
  if(next==null){
   File named=new File(host.getCacheDir(),"moe-update-base.apk");
   try{copy(f,named);if(named.length()!=f.length()||!sha(named).equals(sha(f)))throw new IOException("Could not prepare a verified APK for Android to inspect.");
    next=pm.getPackageArchiveInfo(named.getPath(),PackageManager.GET_SIGNATURES);
   }finally{named.delete();}
  }
  checkApkIdentity(next,current);
 }
 static void install(File apk){try{
  if(Build.VERSION.SDK_INT>=26&&!host.getPackageManager().canRequestPackageInstalls()){new AlertDialog.Builder(host).setTitle("Allow game updates").setMessage("Allow this game to install updates, then return and check again. Android will ask before installing. Your save is retained.").setPositiveButton("Open settings",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){host.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+host.getPackageName())));}}).setNegativeButton("Later",null).show();return;}
  validateApk(apk);PackageInstaller pi=host.getPackageManager().getPackageInstaller();PackageInstaller.SessionParams p=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_INHERIT_EXISTING);p.setAppPackageName(PKG);p.setSize(apk.length());int id=pi.createSession(p);PackageInstaller.Session session=pi.openSession(id);
  try{FileInputStream in=new FileInputStream(apk);try{OutputStream out=session.openWrite("base.apk",0,apk.length());try{byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1)out.write(b,0,n);session.fsync(out);}finally{out.close();}}finally{in.close();}
   Intent status=new Intent(host,UpdateStatusActivity.class);status.setAction("com.moe.offline.INSTALL_STATUS");int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=0x02000000;
   session.commit(PendingIntent.getActivity(host,id,status,flags).getIntentSender());
  }catch(Exception e){session.abandon();throw e;}finally{session.close();}
 }catch(Exception e){message("Could not start installation: "+e.getMessage()+". Your game and save are unchanged.");}}
 static void restore(){new AlertDialog.Builder(host).setTitle("Restore previous assets?").setMessage("Restore the previous asset selection and reopen the game. Your current save will be kept.").setPositiveButton("Restore",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{File f=new File(root,"previous-active.json");if(!f.exists())throw new IOException("No previous asset selection");byte[] b=read(f);if(new String(b,"UTF-8").equals("null"))new AtomicFile(new File(root,"active.json")).delete();else atomic(new File(root,"active.json"),b);message("Previous assets selected. Close and reopen the game.");}catch(Exception e){message(e.getMessage());}}}).setNegativeButton("Cancel",null).show();}
 public static void confirmSaveReset(final Activity a,final String target){a.runOnUiThread(new Runnable(){public void run(){
  final int selectedSlot;
  try{selectedSlot=SaveSlots.activeSaveSlot(a);}catch(Exception error){
   new AlertDialog.Builder(a).setTitle("Save unavailable").setMessage("Could not identify the selected save. Nothing was deleted.").setPositiveButton("OK",null).show();return;
  }
  final EditText input=new EditText(a);input.setSingleLine(true);input.setHint("Type RESET");
  final AlertDialog dialog=new AlertDialog.Builder(a).setTitle("Reset Save "+selectedSlot)
   .setMessage("This resets only Save "+selectedSlot+": its progress, roster, currencies, tutorial and settings. Your other saves, installed game files and updates are kept. A recovery copy is saved on this device. The game will close. Reopen it to start fresh in Save "+selectedSlot+".\n\nType RESET to confirm.")
   .setView(input).setNegativeButton("Cancel",null).setPositiveButton("Reset Save",null).create();
  dialog.setOnShowListener(new DialogInterface.OnShowListener(){public void onShow(DialogInterface d){dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener(){public void onClick(View v){
   if(!input.getText().toString().trim().equalsIgnoreCase("RESET")){input.setError("Type RESET to confirm");return;}
   try{if(SaveSlots.activeSaveSlot(a)!=selectedSlot)throw new IOException("Selected save changed");Class.forName("com.unity3d.player.UnityPlayer").getMethod("UnitySendMessage",String.class,String.class,String.class).invoke(null,target,"OnConfirmLocalSaveReset","RESET");dialog.dismiss();}
   catch(Exception e){new AlertDialog.Builder(a).setMessage("Could not request reset. No save was deleted.").setPositiveButton("OK",null).show();}
  }});}});dialog.show();
 }});}
 public static int activeSaveSlot(Activity a)throws Exception{return SaveSlots.activeSaveSlot(a);}
 public static boolean switchSaveSlot(Activity a,int target)throws Exception{return SaveSlots.switchSaveSlot(a,target);}
 public static String backupSaveForReset(Activity a)throws Exception{return SaveSlots.backupSaveForReset(a);}
 public static void closeAfterSaveReset(Activity a)throws Exception{SaveSlots.closeAfterSaveReset(a);}


}
