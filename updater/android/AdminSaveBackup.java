package com.moe.offline;

import android.app.Activity;
import android.util.AtomicFile;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Explicit backups of the active local profile. No updater or other profile data is copied. */
public final class AdminSaveBackup {
 private AdminSaveBackup(){}

 static File pointer(SaveSlots.Storage store,int slot)throws Exception{
  SaveSlots.validSlot(slot);return new File(store.backups,"admin-save-"+slot+".json");
 }
 static int selected(SaveSlots.Storage store,int expected)throws Exception{
  SaveSlots.validSlot(expected);
  // Reject stale UI selections before initializing or writing anything.
  if(SaveSlots.exists(store.active())&&SaveSlots.marker(store)!=expected)
   throw new IOException("The selected save changed. Reopen Admin Mode for the current save.");
  int actual=SaveSlots.initialize(store);
  if(actual!=expected)throw new IOException("The selected save changed. No backup or restore was performed.");
  finishRecoveredBaseline(store,expected);
  return actual;
 }
 static void finishRecoveredBaseline(SaveSlots.Storage store,int expected)throws Exception{
  File index=pointer(store,expected);if(!SaveSlots.exists(index))return;
  JSONObject receipt=SaveSlots.json(index);String token=receipt.optString("pending_restore_token","");
  if(token.length()==0)return;
  if(!token.matches("[a-f0-9-]{36}"))throw new IOException("The pending backup restore reference is invalid.");
  if(SaveSlots.identity(store).equals(expected+":"+token)){
   SaveSlots.Snapshot baseline=target(store,expected),committed=SaveSlots.readSnapshot(store.slot(expected),expected);
   // The persisted slot copy proves what startup recovery committed. Live
   // preferences may already contain newer regular progress, which is retained.
   if(committed.hash.equals(baseline.hash))clearBaseline(store,expected);
  }
 }
 static void clearBaseline(SaveSlots.Storage store,int expected)throws Exception{
  File index=pointer(store,expected);new AtomicFile(index).delete();
  if(SaveSlots.exists(index))throw new IOException("The restored backup reference could not be retired.");
 }
 static void recover(SaveSlots.Storage store)throws Exception{synchronized(SaveSlots.LOCK){
  int active=SaveSlots.initialize(store);finishRecoveredBaseline(store,active);
 }}
 static SaveSlots.Snapshot target(SaveSlots.Storage store,int expected)throws Exception{
  JSONObject receipt=SaveSlots.json(pointer(store,expected));
  if(receipt.getInt("schema_version")!=1||!PhoneUpdater.PKG.equals(receipt.getString("application_id"))||receipt.getInt("slot")!=expected)
   throw new IOException("The backup belongs to a different save or game.");
  String name=receipt.getString("directory"),digest=receipt.getString("sha256");
  if(!name.matches("[0-9]+-admin-save[123]-[a-f0-9-]{36}")||!digest.matches("[a-f0-9]{64}"))
   throw new IOException("The saved backup reference is invalid.");
  File parent=store.backups.getCanonicalFile(),directory=new File(parent,name).getCanonicalFile();
  File file=new File(directory,"preferences.json").getCanonicalFile();
  if(!parent.equals(directory.getParentFile())||!directory.equals(file.getParentFile())||!"preferences.json".equals(file.getName()))
   throw new IOException("The backup path is outside the save backup folder.");
  SaveSlots.Snapshot result=SaveSlots.readSnapshot(file,expected);
  if(!digest.equals(result.hash))throw new IOException("The backup reference checksum is invalid.");
  return result;
 }

 static String createBackup(SaveSlots.Storage store,int expected)throws Exception{synchronized(SaveSlots.LOCK){
  selected(store,expected);
  File index=pointer(store,expected);boolean existed=SaveSlots.exists(index);
  SaveSlots.Snapshot baseline=existed?target(store,expected):null;
  byte[] prior=existed?SaveSlots.read(index):null;
  SaveSlots.Snapshot snapshot=SaveSlots.snapshot(expected,SaveSlots.current(store));
  File directory=SaveSlots.backup(store,snapshot,"admin");
  File file=new File(directory,"preferences.json");
  if(!SaveSlots.readSnapshot(file,expected).hash.equals(snapshot.hash))throw new IOException("The backup did not verify.");
  JSONObject receipt=new JSONObject();receipt.put("schema_version",1);receipt.put("application_id",PhoneUpdater.PKG);
  receipt.put("slot",expected);receipt.put("directory",directory.getName());receipt.put("sha256",snapshot.hash);
  receipt.put("created_utc_ms",System.currentTimeMillis());
  // The first verified baseline is retained across sessions; later explicit
  // backups add immutable snapshots without replacing the player's undo target.
  try{if(!existed)SaveSlots.writeJson(index,receipt);if(!target(store,expected).hash.equals(existed?baseline.hash:snapshot.hash))throw new IOException("The backup reference did not verify.");}
  catch(Exception failure){
   if(existed)SaveSlots.write(index,prior);else new AtomicFile(index).delete();
   throw failure;
  }
  JSONObject result=new JSONObject();result.put("slot",expected);result.put("baseline_retained",existed);
  result.put("snapshot_path",file.getAbsolutePath());
  JSONObject activeReceipt=SaveSlots.json(index);
  result.put("baseline_path",new File(new File(store.backups,activeReceipt.getString("directory")),"preferences.json").getAbsolutePath());
  result.put("sha256",activeReceipt.getString("sha256"));return result.toString();
 }}

 static boolean hasBackup(SaveSlots.Storage store,int expected){synchronized(SaveSlots.LOCK){
  try{selected(store,expected);target(store,expected);return true;}catch(Exception unavailable){return false;}
 }}

 // Same-slot transactions use SaveSlots' existing journal format, so startup
 // recovery completes or rolls back an interrupted restore without new launch hooks.
 static boolean restoreBackup(SaveSlots.Storage store,int expected)throws Exception{synchronized(SaveSlots.LOCK){
  selected(store,expected);
  SaveSlots.Snapshot restored=target(store,expected); // fully parse and verify before touching live preferences
  byte[] baselinePointer=SaveSlots.read(pointer(store,expected));
  SaveSlots.Snapshot before=SaveSlots.snapshot(expected,SaveSlots.current(store));
  String previousIdentity=SaveSlots.identity(store),token=UUID.randomUUID().toString();
  SaveSlots.backup(store,before,"admin-before-restore");
  SaveSlots.writeSnapshot(store.slot(expected),before);SaveSlots.writeSnapshot(store.rollback(),before);
  JSONObject journal=new JSONObject();journal.put("schema_version",1);journal.put("from",expected);journal.put("to",expected);
  journal.put("token",token);journal.put("before_identity",previousIdentity);journal.put("before_sha256",before.hash);journal.put("target",SaveSlots.encode(restored));
  SaveSlots.writeJson(store.journal(),journal);
  boolean touched=false;
  try{
   JSONObject pending=new JSONObject(new String(baselinePointer,"UTF-8"));pending.put("pending_restore_token",token);
   SaveSlots.writeJson(pointer(store,expected),pending);
   SaveSlots.writeSnapshot(store.slot(expected),restored);
   touched=true;SaveSlots.apply(store,restored,expected+":"+token);
   SaveSlots.marker(store,expected);clearBaseline(store,expected);SaveSlots.cleanTransaction(store);return true;
  }catch(Exception failure){
   try{
    if(touched)SaveSlots.apply(store,before,previousIdentity);
    SaveSlots.write(pointer(store,expected),baselinePointer);
    SaveSlots.writeSnapshot(store.slot(expected),before);SaveSlots.marker(store,expected);SaveSlots.cleanTransaction(store);
    if(!SaveSlots.payload(SaveSlots.current(store)).equals(before.payload)||!SaveSlots.identity(store).equals(previousIdentity))
     throw new IOException("Backup restore rollback did not verify.");
   }catch(Exception rollback){Log.e("MOEAdminBackup","Restore requires startup recovery",rollback);return false;}
   throw failure;
  }
 }}

 public static String createBackup(Activity activity,int expectedSlot)throws Exception{
  return createBackup(SaveSlots.storage(activity),expectedSlot);
 }
 public static void recover(Activity activity)throws Exception{recover(SaveSlots.storage(activity));}
 public static boolean hasBackup(Activity activity,int expectedSlot)throws Exception{
  return hasBackup(SaveSlots.storage(activity),expectedSlot);
 }
 public static boolean restoreBackup(Activity activity,int expectedSlot)throws Exception{synchronized(SaveSlots.LOCK){
  boolean clean=restoreBackup(SaveSlots.storage(activity),expectedSlot);
  // Unity still caches the pre-restore preferences. Never finish/quit through
  // Unity or return to gameplay where those cached values could overwrite the restore.
  SaveSlots.close(activity,clean?"Save "+expectedSlot+" restored. Reopen the game to continue.":
   "Save recovery copies were kept. Reopen the game to recover safely.");
  return clean;
 }}
}
