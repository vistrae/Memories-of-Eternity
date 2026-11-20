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
 static final int CLIENT=13;
 static final String MODULUS="qivjJ5OkgDAM050wsGwCRBraDmgrfDVG1URnnjrUlMhVyBhfe28pQ4zpjMcfJuL70V0wey0za9KKulxBrF/o9c7EtAVwZignltYRCLIrUCa7rGZHExJ4K1HaVzfdUX8kcDKNt84nNErn+IkKYCteSCDGR0qrih5KHAFXeh96jjDRY/VnMqsnICYjyrJA8BntXih7FxV22dTjmeNrcjBWatJfnOXmvXbaI7mX3now97LHZ6JDGr5ExETp8E0E48tAycVz5qxXgmuL2VKV9TfTjFi/uZKmC4CrmSbPP5mn/36uNUaaAcMEVZi+Hx3p8Wg63QXLeYmG7xogWuNpZzJbifBI30jYT58I64LPQCKDlweZSz9Wwdsg04GHA0S+rjG0uby1Q+Ljt/hYlRqyyEv27HSCeL4/II0DP6kQlW3oJ7AVfBEof1NPSxGKjoOK9QBO+LeeK3ArkWU+Le5qnPDjQQAQ0QHksR2hhIZ9UyvOpk/5/yoYf3LSAF8h+ynEKHSR";
 static boolean attached, busy;
 static Activity host;
 static File root;
 static ProgressDialog progress;
 static void ui(Runnable r){host.runOnUiThread(r);}
 static void message(final String s){ui(new Runnable(){public void run(){new AlertDialog.Builder(host).setTitle("Game updates").setMessage(s).setPositiveButton("OK",null).show();}});}
 public static void attach(final Activity a){a.runOnUiThread(new Runnable(){public void run(){
  if(attached)return;attached=true;host=a;root=new File(a.getFilesDir(),"moe-updates");root.mkdirs();
  Button b=new Button(a);b.setText("Updates");b.setTextSize(11);b.setAlpha(.8f);
  android.widget.FrameLayout.LayoutParams p=new android.widget.FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);
  a.addContentView(b,p);b.setOnClickListener(new View.OnClickListener(){public void onClick(View v){menu();}});
  if(new File(root,"pending-manifest.json").exists())new Handler().postDelayed(new Runnable(){public void run(){finishPending();}},1200);
 }});}
 static void menu(){new AlertDialog.Builder(host).setTitle("Offline playtest B"+CLIENT)
  .setMessage("Play offline at any time. Checking downloads only the files needed for an update. Your save stays on this phone.")
  .setPositiveButton("Check for updates",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){check();}})
  .setNeutralButton("Restore assets",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){restore();}})
  .setNegativeButton("Keep playing",null).show();}
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
 static JSONObject verify(byte[] bytes,byte[] sig)throws Exception{
  PublicKey key=KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(new BigInteger(1,android.util.Base64.decode(MODULUS,0)),BigInteger.valueOf(65537)));
  java.security.Signature v=java.security.Signature.getInstance("SHA256withRSA");v.initVerify(key);v.update(bytes);if(!v.verify(android.util.Base64.decode(sig,0)))throw new IOException("Update signature is invalid");
  JSONObject m=new JSONObject(new String(bytes,"UTF-8").replaceFirst("^\\uFEFF",""));
  if(m.getInt("schema_version")!=1||!m.getString("application_id").equals(PKG)||!m.getString("asset_format").equals(FORMAT)||!m.getString("channel").equals("stable")||m.getInt("save_schema_version")!=1||m.getInt("min_updater_version")>1||m.getLong("sequence")<1||m.getLong("required_client_sequence")>m.getLong("sequence"))throw new IOException("Incompatible update");
  if(!m.getString("release_id").matches("[A-Za-z0-9_.-]{1,100}"))throw new IOException("Invalid release ID");
  if(m.has("apk")&&!m.isNull("apk"))spec(m.getJSONObject("apk"));
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
  File apk=null;if(m.has("apk")&&!m.isNull("apk"))apk=download(m.getJSONObject("apk"));JSONArray assets=m.getJSONArray("assets");for(int i=0;i<assets.length();i++)download(assets.getJSONObject(i));
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
 public static void finishPending(){worker(new Work(){public void run()throws Exception{File f=new File(root,"pending-manifest.json"),s=new File(root,"pending-manifest.sig");if(!f.exists()||!s.exists())return;JSONObject m=verify(read(f),read(s));if(m.getLong("required_client_sequence")>CLIENT)return;if(m.has("apk")&&!m.isNull("apk")&&!sha(new File(host.getApplicationInfo().sourceDir)).equals(m.getJSONObject("apk").getString("sha256")))return;activate(m);f.delete();s.delete();message("Update completed. Reopen the game to load updated assets.");}});}
 static void validateApk(File f)throws Exception{PackageManager pm=host.getPackageManager();PackageInfo next=pm.getPackageArchiveInfo(f.getPath(),PackageManager.GET_SIGNATURES),current=pm.getPackageInfo(PKG,PackageManager.GET_SIGNATURES);if(next==null||!PKG.equals(next.packageName)||next.versionCode!=current.versionCode||!Arrays.equals(next.signatures,current.signatures))throw new IOException("APK identity or signing certificate does not match this installation");}
 static void install(File apk){try{
  if(Build.VERSION.SDK_INT>=26&&!host.getPackageManager().canRequestPackageInstalls()){new AlertDialog.Builder(host).setTitle("Allow game updates").setMessage("Allow this game to install updates, then return and check again. Android will ask before installing. Your save is retained.").setPositiveButton("Open settings",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){host.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+PKG)));}}).setNegativeButton("Later",null).show();return;}
  validateApk(apk);PackageInstaller pi=host.getPackageManager().getPackageInstaller();PackageInstaller.SessionParams p=new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_INHERIT_EXISTING);p.setAppPackageName(PKG);p.setSize(apk.length());int id=pi.createSession(p);PackageInstaller.Session session=pi.openSession(id);
  try{FileInputStream in=new FileInputStream(apk);try{OutputStream out=session.openWrite("base.apk",0,apk.length());try{byte[] b=new byte[262144];int n;while((n=in.read(b))!=-1)out.write(b,0,n);session.fsync(out);}finally{out.close();}}finally{in.close();}
   Intent status=new Intent(host,UpdateStatusActivity.class);status.setAction("com.moe.offline.INSTALL_STATUS");int flags=PendingIntent.FLAG_UPDATE_CURRENT;if(Build.VERSION.SDK_INT>=31)flags|=0x02000000;
   session.commit(PendingIntent.getActivity(host,id,status,flags).getIntentSender());
  }catch(Exception e){session.abandon();throw e;}finally{session.close();}
 }catch(Exception e){message("Could not start installation: "+e.getMessage()+". Your game and save are unchanged.");}}
 static void restore(){new AlertDialog.Builder(host).setTitle("Restore previous assets?").setMessage("Restore the previous asset selection and reopen the game. Your current save will be kept.").setPositiveButton("Restore",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){try{File f=new File(root,"previous-active.json");if(!f.exists())throw new IOException("No previous asset selection");byte[] b=read(f);if(new String(b,"UTF-8").equals("null"))new AtomicFile(new File(root,"active.json")).delete();else atomic(new File(root,"active.json"),b);message("Previous assets selected. Close and reopen the game.");}catch(Exception e){message(e.getMessage());}}}).setNegativeButton("Cancel",null).show();}
}
