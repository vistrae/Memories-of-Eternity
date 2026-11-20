package com.moe.offline;
import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.content.pm.PackageInstaller;
public class UpdateStatusActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);int status=getIntent().getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE);
  if(status==PackageInstaller.STATUS_PENDING_USER_ACTION){Intent confirm=getIntent().getParcelableExtra(Intent.EXTRA_INTENT);if(confirm!=null)startActivity(confirm);finish();return;}
  new AlertDialog.Builder(this).setTitle("Game update").setMessage(status==PackageInstaller.STATUS_SUCCESS?"Update installed. Open the game to continue with your existing save.":"Update not installed. Your existing game and save are retained. You can retry from Updates.").setPositiveButton("OK",new DialogInterface.OnClickListener(){public void onClick(DialogInterface d,int w){finish();}}).show();
 }
}
