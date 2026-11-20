package com.moe.offline;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import android.util.Log;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

/** Three local profiles; update state and third-party SDK preferences stay global. */
public final class SaveSlots {
 static final String PREFS=PhoneUpdater.PKG+".v2.playerprefs";
 // Written in the same commit as the restored preferences, solely to resolve an
 // interrupted cross-file transaction. Active selection remains outside PlayerPrefs.
 static final String IDENTITY="MOE_Offline_NativeSlotTransaction";
 static final int MAX_SNAPSHOT_BYTES=32*1024*1024;
 static final Object LOCK=new Object();

 static final class Snapshot {
  final int slot;
  final String payload,hash;
  final Map<String,Object> values;
  Snapshot(int slot,String payload,String hash,Map<String,Object> values){this.slot=slot;this.payload=payload;this.hash=hash;this.values=values;}
 }

 // Package-private core entry points allow isolated instrumentation with a
 // separate preference name and directory, without disabling production exits.
 static final class Storage {
  final SharedPreferences prefs;
  final File directory,backups;
  Storage(SharedPreferences prefs,File directory,File backups){this.prefs=prefs;this.directory=directory;this.backups=backups;}
  File active(){return new File(directory,"active.json");}
  File slot(int slot)throws Exception{validSlot(slot);return new File(directory,"slot-"+slot+".json");}
  File journal(){return new File(directory,"journal.json");}
  File rollback(){return new File(directory,"rollback.json");}
 }

 static Storage storage(Activity activity)throws IOException{
  if(!PhoneUpdater.PKG.equals(activity.getPackageName()))throw new IOException("Save profiles are available inside the game only.");
  return new Storage(activity.getSharedPreferences(PREFS,Context.MODE_PRIVATE),
   new File(activity.getFilesDir(),"moe-save-slots"),new File(activity.getFilesDir(),"moe-save-backups"));
 }
 static void validSlot(int slot)throws IOException{if(slot<1||slot>3)throw new IOException("Invalid save slot.");}
 static void folder(File path)throws IOException{if(!path.isDirectory()&&!path.mkdirs())throw new IOException("Could not create save storage.");}
 static boolean exists(File file){return file.isFile()||new File(file.getPath()+".bak").isFile();}
 static String hash(byte[] bytes)throws Exception{return PhoneUpdater.hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
 static byte[] read(File file)throws Exception{
  FileInputStream input=new AtomicFile(file).openRead();
  try{return PhoneUpdater.drain(input,MAX_SNAPSHOT_BYTES);}finally{input.close();}
 }
 static void write(File file,byte[] bytes)throws Exception{
  folder(file.getParentFile());AtomicFile atomic=new AtomicFile(file);FileOutputStream output=atomic.startWrite();
  try{output.write(bytes);atomic.finishWrite(output);}catch(Exception error){atomic.failWrite(output);throw error;}
  if(!Arrays.equals(read(file),bytes))throw new IOException("Save storage verification failed.");
 }
 static JSONObject json(File file)throws Exception{return new JSONObject(new String(read(file),"UTF-8"));}
 static void writeJson(File file,JSONObject value)throws Exception{write(file,value.toString().getBytes("UTF-8"));}

 static Map<String,Object> current(Storage store){
  Map<String,Object> values=new HashMap<String,Object>();
  for(Map.Entry<String,?> entry:store.prefs.getAll().entrySet())if(!IDENTITY.equals(entry.getKey())){
   Object value=entry.getValue();
   if(value instanceof Set<?>)value=new HashSet<String>((Set<String>)value);
   values.put(entry.getKey(),value);
  }
  return values;
 }
 static String identity(Storage store)throws IOException{
  Object value=store.prefs.getAll().get(IDENTITY);
  if(value==null)return "";
  if(!(value instanceof String))throw new IOException("The save transaction marker is unreadable.");
  return (String)value;
 }
 static String payload(Map<String,Object> values)throws Exception{
  JSONArray entries=new JSONArray();List<String> keys=new ArrayList<String>(values.keySet());Collections.sort(keys);
  for(String key:keys){
   if(key==null||IDENTITY.equals(key))throw new IOException("Invalid save preference key.");
   Object value=values.get(key);JSONObject entry=new JSONObject();entry.put("key",key);
   if(value instanceof String){entry.put("type","string");entry.put("value",value);}
   else if(value instanceof Integer){entry.put("type","int");entry.put("value",value);}
   else if(value instanceof Long){entry.put("type","long");entry.put("value",value);}
   else if(value instanceof Float){entry.put("type","float-bits");entry.put("value",Float.floatToRawIntBits((Float)value));}
   else if(value instanceof Boolean){entry.put("type","boolean");entry.put("value",value);}
   else if(value instanceof Set<?>){
    List<String> strings=new ArrayList<String>();
    for(Object item:(Set<?>)value){if(!(item instanceof String))throw new IOException("Invalid saved string set.");strings.add((String)item);}
    Collections.sort(strings);JSONArray array=new JSONArray();for(String item:strings)array.put(item);
    entry.put("type","string-set");entry.put("value",array);
   }else throw new IOException("Unsupported save preference type.");
   entries.put(entry);
  }
  return entries.toString();
 }
 static long integer(JSONObject entry)throws Exception{
  Object value=entry.get("value");
  if(!(value instanceof Integer)&&!(value instanceof Long))throw new IOException("Invalid saved integer.");
  return ((Number)value).longValue();
 }
 static Map<String,Object> decode(String payload)throws Exception{
  JSONArray entries=new JSONArray(payload);Map<String,Object> values=new HashMap<String,Object>();
  if(entries.length()>100000)throw new IOException("Save snapshot contains too many preferences.");
  for(int index=0;index<entries.length();index++){
   JSONObject entry=entries.getJSONObject(index);String key=entry.getString("key"),type=entry.getString("type");Object value;
   if(key.length()>4096||IDENTITY.equals(key)||values.containsKey(key))throw new IOException("Invalid or duplicate save preference.");
   if(type.equals("string")){value=entry.get("value");if(!(value instanceof String))throw new IOException("Invalid saved text.");}
   else if(type.equals("int")||type.equals("float-bits")){
    long number=integer(entry);if(number<Integer.MIN_VALUE||number>Integer.MAX_VALUE)throw new IOException("Saved integer is out of range.");
    value=type.equals("int")?(Object)Integer.valueOf((int)number):(Object)Float.valueOf(Float.intBitsToFloat((int)number));
   }else if(type.equals("long"))value=Long.valueOf(integer(entry));
   else if(type.equals("boolean")){value=entry.get("value");if(!(value instanceof Boolean))throw new IOException("Invalid saved boolean.");}
   else if(type.equals("string-set")){
    JSONArray array=entry.getJSONArray("value");Set<String> strings=new HashSet<String>();
    for(int n=0;n<array.length();n++){Object item=array.get(n);if(!(item instanceof String)||!strings.add((String)item))throw new IOException("Invalid saved string set.");}
    value=strings;
   }else throw new IOException("Unknown save preference type.");
   values.put(key,value);
  }
  return values;
 }
 static Snapshot snapshot(int slot,Map<String,Object> values)throws Exception{
  validSlot(slot);String data=payload(values);if(data.getBytes("UTF-8").length>MAX_SNAPSHOT_BYTES/2)throw new IOException("Save snapshot is too large.");
  return new Snapshot(slot,data,hash(data.getBytes("UTF-8")),decode(data));
 }
 static JSONObject encode(Snapshot snapshot)throws Exception{
  JSONObject value=new JSONObject();value.put("schema_version",1);value.put("application_id",PhoneUpdater.PKG);
  value.put("slot",snapshot.slot);value.put("payload",snapshot.payload);value.put("sha256",snapshot.hash);return value;
 }
 static Snapshot decodeSnapshot(JSONObject value,int expectedSlot)throws Exception{
  validSlot(expectedSlot);
  if(value.getInt("schema_version")!=1||!PhoneUpdater.PKG.equals(value.getString("application_id"))||value.getInt("slot")!=expectedSlot)
   throw new IOException("Save snapshot belongs to a different slot or application.");
  String data=value.getString("payload"),digest=value.getString("sha256");
  if(!digest.matches("[0-9a-f]{64}")||!hash(data.getBytes("UTF-8")).equals(digest))throw new IOException("Save snapshot checksum is invalid.");
  return new Snapshot(expectedSlot,data,digest,decode(data));
 }
 static Snapshot readSnapshot(File file,int slot)throws Exception{return decodeSnapshot(json(file),slot);}
 static void writeSnapshot(File file,Snapshot snapshot)throws Exception{
  writeJson(file,encode(snapshot));if(!readSnapshot(file,snapshot.slot).hash.equals(snapshot.hash))throw new IOException("Save snapshot did not verify.");
 }
 static int marker(Storage store)throws Exception{
  JSONObject active=json(store.active());int selected=active.getInt("active_slot");validSlot(selected);
  if(active.getInt("schema_version")!=1)throw new IOException("Save slot selection has an unknown format.");return selected;
 }
 static void marker(Storage store,int selected)throws Exception{
  validSlot(selected);JSONObject active=new JSONObject();active.put("schema_version",1);active.put("active_slot",selected);
  writeJson(store.active(),active);if(marker(store)!=selected)throw new IOException("Save slot selection did not verify.");
 }
 static void apply(Storage store,Snapshot snapshot,String transactionIdentity)throws Exception{
  SharedPreferences.Editor editor=store.prefs.edit().clear();
  for(Map.Entry<String,Object> entry:snapshot.values.entrySet()){
   String key=entry.getKey();Object value=entry.getValue();
   if(value instanceof String)editor.putString(key,(String)value);
   else if(value instanceof Integer)editor.putInt(key,(Integer)value);
   else if(value instanceof Long)editor.putLong(key,(Long)value);
   else if(value instanceof Float)editor.putFloat(key,(Float)value);
   else if(value instanceof Boolean)editor.putBoolean(key,(Boolean)value);
   else if(value instanceof Set<?>)editor.putStringSet(key,new HashSet<String>((Set<String>)value));
   else throw new IOException("Unsupported save preference type.");
  }
  if(transactionIdentity.length()>0)editor.putString(IDENTITY,transactionIdentity);
  if(!editor.commit())throw new IOException("Could not commit the selected save.");
  if(!payload(current(store)).equals(snapshot.payload)||!identity(store).equals(transactionIdentity))throw new IOException("Selected save verification failed.");
 }
 static void cleanTransaction(Storage store){new AtomicFile(store.journal()).delete();new AtomicFile(store.rollback()).delete();}

 static void recover(Storage store)throws Exception{
  if(!exists(store.journal()))return;
  JSONObject journal=json(store.journal());
  int from=journal.getInt("from"),to=journal.getInt("to");validSlot(from);validSlot(to);
  String token=journal.getString("token"),beforeIdentity=journal.getString("before_identity");
  if(journal.getInt("schema_version")!=1||!token.matches("[a-f0-9-]{36}"))throw new IOException("Save recovery journal is invalid.");
  Snapshot before=readSnapshot(store.rollback(),from),target=decodeSnapshot(journal.getJSONObject("target"),to);
  if(!before.hash.equals(journal.getString("before_sha256")))throw new IOException("Save recovery copy did not verify.");
  String currentIdentity=identity(store),currentHash=hash(payload(current(store)).getBytes("UTF-8"));
  boolean committed=currentIdentity.equals(to+":"+token);
  boolean notCommitted=currentIdentity.equals(beforeIdentity);
  if(!committed&&!notCommitted){committed=currentHash.equals(target.hash);notCommitted=currentHash.equals(before.hash);}
  if(committed){writeSnapshot(store.slot(to),target);marker(store,to);cleanTransaction(store);}
  else if(notCommitted){writeSnapshot(store.slot(from),before);marker(store,from);cleanTransaction(store);}
  else throw new IOException("Save recovery needs attention. Existing progress and recovery copies were kept.");
 }
 static int initialize(Storage store)throws Exception{
  folder(store.directory);recover(store);
  if(exists(store.active()))return marker(store);
  String transaction=identity(store);int initial=1;
  if(transaction.matches("[123]:[a-f0-9-]{36}"))initial=Integer.parseInt(transaction.substring(0,1));
  else if(exists(store.slot(2))||exists(store.slot(3)))throw new IOException("Save slot selection is missing. Existing progress was kept.");
  Snapshot current=snapshot(initial,current(store));writeSnapshot(store.slot(initial),current);marker(store,initial);return initial;
 }
 static File backup(Storage store,Snapshot snapshot,String reason)throws Exception{
  File directory=new File(store.backups,System.currentTimeMillis()+"-"+reason+"-save"+snapshot.slot+"-"+UUID.randomUUID().toString());
  folder(directory);File file=new File(directory,"preferences.json");writeSnapshot(file,snapshot);return directory;
 }

 // All target data is parsed and verified before any current save snapshot is changed.
 // A false rollback result means the caller must close without returning to Unity.
 static boolean change(Storage store,int targetSlot,boolean reset)throws Exception{
  validSlot(targetSlot);int selected=initialize(store);if(!reset&&selected==targetSlot)return true;
  Snapshot target=reset?snapshot(selected,new HashMap<String,Object>()):
   (exists(store.slot(targetSlot))?readSnapshot(store.slot(targetSlot),targetSlot):snapshot(targetSlot,new HashMap<String,Object>()));
  Snapshot before=snapshot(selected,current(store));String beforeIdentity=identity(store),token=UUID.randomUUID().toString();
  backup(store,before,reset?"reset":"switch");
  writeSnapshot(store.slot(selected),before);writeSnapshot(store.rollback(),before);
  JSONObject journal=new JSONObject();journal.put("schema_version",1);journal.put("from",selected);journal.put("to",target.slot);
  journal.put("token",token);journal.put("before_identity",beforeIdentity);journal.put("before_sha256",before.hash);journal.put("target",encode(target));
  writeJson(store.journal(),journal);
  boolean preferencesTouched=false;
  try{
   writeSnapshot(store.slot(target.slot),target);
   preferencesTouched=true;apply(store,target,target.slot+":"+token);
   marker(store,target.slot);cleanTransaction(store);return true;
  }catch(Exception error){
   try{
    if(preferencesTouched)apply(store,before,beforeIdentity);
    writeSnapshot(store.slot(selected),before);marker(store,selected);cleanTransaction(store);
    if(!payload(current(store)).equals(before.payload)||!identity(store).equals(beforeIdentity))throw new IOException("Rollback verification failed.");
   }catch(Exception rollback){Log.e("MOESaveSlots","Save transaction requires restart recovery",rollback);return false;}
   throw error;
  }
 }

 public static void recover(Activity activity)throws Exception{synchronized(LOCK){Storage store=storage(activity);if(store.directory.exists())recover(store);}}
 public static int activeSaveSlot(Activity activity)throws Exception{synchronized(LOCK){return initialize(storage(activity));}}
 static void close(final Activity activity,final String message){
  activity.runOnUiThread(new Runnable(){public void run(){Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}});
  // No Activity.finish(), Unity quit or managed callbacks: they can flush the old cached profile.
  android.os.Process.killProcess(android.os.Process.myPid());System.exit(0);
 }
 public static boolean switchSaveSlot(Activity activity,int target)throws Exception{synchronized(LOCK){
  Storage store=storage(activity);int selected=initialize(store);validSlot(target);if(target==selected)return false;
  boolean clean=change(store,target,false);
  close(activity,clean?"Save "+target+" selected. Reopen the game to continue.":"Save recovery copies were kept. Reopen the game to recover safely.");return true;
 }}
 public static String backupSaveForReset(Activity activity)throws Exception{synchronized(LOCK){
  Storage store=storage(activity);int selected=initialize(store);return backup(store,snapshot(selected,current(store)),"before-reset").getAbsolutePath();
 }}
 public static void closeAfterSaveReset(Activity activity)throws Exception{synchronized(LOCK){
  Storage store=storage(activity);int selected=initialize(store);boolean clean=change(store,selected,true);
  close(activity,clean?"Save "+selected+" was reset. Reopen the game to start fresh.":"Save recovery copies were kept. Reopen the game to recover safely.");
 }}
}
