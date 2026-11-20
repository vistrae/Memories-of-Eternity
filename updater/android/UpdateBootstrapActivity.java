package com.moe.offline;
import android.app.Activity;import android.os.Bundle;
public class UpdateBootstrapActivity extends Activity {
 @Override public void onCreate(Bundle b){super.onCreate(b);setTitle("MOE update installer");PhoneUpdater.attach(this);new android.os.Handler().postDelayed(new Runnable(){public void run(){PhoneUpdater.menu();}},300);}
}
