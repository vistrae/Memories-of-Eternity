using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using Newtonsoft.Json;
using UnityEngine;

public static partial class OfflineCampaign
{
    private sealed class OfflineUpdateIndex
    {
        public int schema_version;
        public string asset_format;
        public Dictionary<string, string> files;
        public Dictionary<string, string> hashes;
    }
    private static bool updateIndexRead;
    private static string updateAssetRoot;
    private static OfflineUpdateIndex updateIndex;
    private static readonly HashSet<string> checkedUpdateAssets = new HashSet<string>(StringComparer.Ordinal);
    private static readonly HashSet<string> rejectedUpdateAssets = new HashSet<string>(StringComparer.Ordinal);

    private static bool SafeUpdatePath(string path)
    {
        if (string.IsNullOrEmpty(path) || path.Length > 240 || Path.IsPathRooted(path) ||
            path.IndexOf((char)92) >= 0 || path.IndexOf(':') >= 0) return false;
        foreach (string part in path.Split('/'))
            if (part.Length == 0 || part == "." || part == "..") return false;
        foreach (char c in path)
            if (!(char.IsLetterOrDigit(c) || c == '_' || c == '-' || c == '.' || c == '/')) return false;
        return true;
    }

    private static string FindUpdatedAssetPath(string filename)
    {
        if (Application.platform != RuntimePlatform.Android || filename == null || !SafeUpdatePath(filename))
            return null;
        if (!updateIndexRead)
        {
            updateIndexRead = true;
            try
            {
                using (var unity = new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
                using (var activity = unity.GetStatic<AndroidJavaObject>("currentActivity"))
                using (var directory = activity.Call<AndroidJavaObject>("getFilesDir"))
                    updateAssetRoot = Path.Combine(directory.Call<string>("getAbsolutePath"), "moe-updates");
                string indexPath = Path.Combine(updateAssetRoot, "active.json");
                if (File.Exists(indexPath))
                    updateIndex = JsonConvert.DeserializeObject<OfflineUpdateIndex>(File.ReadAllText(indexPath));
                if (updateIndex != null && (updateIndex.schema_version != 1 ||
                    updateIndex.asset_format != "unity5-global-51" ||
                    updateIndex.files == null || updateIndex.hashes == null))
                    updateIndex = null;
            }
            catch (Exception error)
            {
                updateIndex = null;
                Debug.LogWarning("MOE update assets unavailable; using installed assets. " + error.Message);
            }
        }
        string relative, expected;
        if (updateIndex == null || !updateIndex.files.TryGetValue(filename, out relative) ||
            !updateIndex.hashes.TryGetValue(filename, out expected) || !SafeUpdatePath(relative) ||
            !relative.StartsWith("releases/", StringComparison.Ordinal) || rejectedUpdateAssets.Contains(filename))
            return null;
        string path = Path.Combine(updateAssetRoot, relative.Replace('/', Path.DirectorySeparatorChar));
        if (!File.Exists(path)) return null;
        if (!checkedUpdateAssets.Contains(filename))
        {
            try
            {
                string actual;
                using (var sha = SHA256.Create())
                using (var stream = File.OpenRead(path))
                    actual = BitConverter.ToString(sha.ComputeHash(stream)).Replace("-", "");
                if (!string.Equals(actual, expected, StringComparison.OrdinalIgnoreCase))
                {
                    rejectedUpdateAssets.Add(filename);
                    Debug.LogWarning("MOE update asset failed verification; using installed asset: " + filename);
                    return null;
                }
                checkedUpdateAssets.Add(filename);
                Debug.Log("MOE verified update asset: " + filename);
            }
            catch (Exception)
            {
                rejectedUpdateAssets.Add(filename);
                return null;
            }
        }
        return path;
    }
}




public static partial class OfflineCampaign
{
    private static void RecordOfflineClientBuild()
    {
        try {
            using(var unity=new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
            using(var activity=unity.GetStatic<AndroidJavaObject>("currentActivity"))
            using(var directory=activity.Call<AndroidJavaObject>("getFilesDir"))
                File.WriteAllText(Path.Combine(directory.Call<string>("getAbsolutePath"),"moe-client-build.json"),
                    JsonConvert.SerializeObject(new { client_sequence=OfflineClientSequence, updater_version=1, save_schema_version=1 }));
        } catch(Exception error) { Debug.LogWarning("MOE build record unavailable: "+error.Message); }
    }
}


public static partial class OfflineCampaign {
 private static void AttachPhoneUpdater() {
  if(Application.platform != RuntimePlatform.Android) return;
  try {
   using(var unity=new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
   using(var activity=unity.GetStatic<AndroidJavaObject>("currentActivity"))
   using(var updater=new AndroidJavaClass("com.moe.offline.PhoneUpdater")) updater.CallStatic("attach",activity);
  } catch(Exception e) { Debug.LogWarning("Optional phone updater unavailable: "+e.Message); }
 }
}

public static partial class OfflineCampaign {
 private static void SetPhoneUpdatesVisible(bool visible) {
  if(Application.platform != RuntimePlatform.Android)return;
  try {
   using(var unity=new AndroidJavaClass("com.unity3d.player.UnityPlayer"))
   using(var activity=unity.GetStatic<AndroidJavaObject>("currentActivity"))
   using(var updater=new AndroidJavaClass("com.moe.offline.PhoneUpdater"))updater.CallStatic("setVisible",activity,visible);
  } catch(Exception e) { Debug.LogWarning("Phone update visibility unavailable: "+e.Message); }
 }
}
