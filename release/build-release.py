"""Reproduce the already signed B13 APK and publish it without exposing signing keys."""
import argparse,base64,hashlib,http.cookiejar,json,os,sys,urllib.request,urllib.parse,urllib.error,zipfile
from html.parser import HTMLParser
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
RELEASE=ROOT/"release"/"b13-android-updates"
REPO="vistrae/Memories-of-Eternity"
SOURCE_ID="1_yzhBArNNLWsym-bDcIbzmEhvlA17wBM"
SOURCE_SHA="af25878c7e58d64929169db68675b4f4c3dbba5227bb96cba173515ca0de1caa"
def sha(data):return hashlib.sha256(data).hexdigest()
class DownloadForm(HTMLParser):
 def __init__(self):super().__init__();self.action=None;self.fields={};self.active=False
 def handle_starttag(self,tag,attrs):
  a=dict(attrs)
  if tag=="form" and a.get("id")=="download-form":self.action=a.get("action");self.active=True
  elif tag=="input" and self.active and "name" in a:self.fields[a["name"]]=a.get("value","")
 def handle_endtag(self,tag):
  if tag=="form":self.active=False
def download_source():
 opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
 url="https://drive.google.com/uc?export=download&id="+SOURCE_ID
 with opener.open(url,timeout=90) as response:
  first=response.read(16384)
  if first.startswith(b"PK"):data=first+response.read()
  else:
   parser=DownloadForm();parser.feed(first.decode("utf-8","replace"))
   if not parser.action or urllib.parse.urlsplit(parser.action).hostname not in ("drive.usercontent.google.com","drive.google.com"):
    raise RuntimeError("The public bootstrap source is unavailable; no release was published.")
   url=parser.action+"?"+urllib.parse.urlencode(parser.fields)
   with opener.open(url,timeout=180) as response:data=response.read(100*1024*1024+1)
 if sha(data)!=SOURCE_SHA:raise RuntimeError("Bootstrap source SHA256 mismatch.")
 return data
def build(source=None):
 import bsdiff4
 (RELEASE/"moe-update.json").write_bytes(base64.b64decode((RELEASE/"moe-update.json.b64").read_text(),validate=True))
 manifest=json.loads((RELEASE/"moe-update.json").read_text(encoding="utf-8-sig"))
 original=Path(source).read_bytes() if source else download_source()
 if sha(original)!=SOURCE_SHA:raise RuntimeError("Wrong bootstrap APK")
 patch=base64.b64decode((RELEASE/"moe-base.bsdiff.b64").read_text(),validate=False)
 apk=bsdiff4.patch(original,patch)
 if len(apk)!=manifest["apk"]["size"] or sha(apk)!=manifest["apk"]["sha256"]:
  raise RuntimeError("Reconstructed signed APK failed verification.")
 target=RELEASE/"moe-base.apk";target.write_bytes(apk)
 bundle=RELEASE/"MOE-Windows-Updater-B13.zip"
 with zipfile.ZipFile(bundle,"w",zipfile.ZIP_DEFLATED,compresslevel=1) as z:
  for p in sorted((ROOT/"updater/windows").glob("*")):
   if p.is_file():z.write(p,p.name)
  z.write(target,"MOE-Offline-Campaign-Chapter13-Rewards.apk")
  z.write(ROOT/"README.md","GitHub-Updater-Readme.txt")
 print("Signed B13 APK reproduced and verified.")
 helper=RELEASE/"MOE-Android-Update-Installer.apk"
 helper.write_bytes(base64.b64decode((RELEASE/"phone-installer.apk.b64").read_text(),validate=True))
 if sha(helper.read_bytes())!="b549bf3cd2cc6134ca2500313db3c96a1f7ec14b8365469dbf11c94b25ff7ff2":raise RuntimeError("Phone installer checksum mismatch")
 return manifest,[target,RELEASE/"moe-update.json",RELEASE/"moe-update.json.sig",RELEASE/"CHANGELOG.txt",bundle,helper]
def publish(manifest,files):
 token=os.environ.get("GITHUB_TOKEN")
 if not token:raise RuntimeError("Publishing requires the GitHub Actions token.")
 def api(path,method="GET",value=None):
  url=path if path.startswith("https://") else "https://api.github.com"+path
  headers={"Authorization":"Bearer "+token,"Accept":"application/vnd.github+json","User-Agent":"MOE-Release-Builder"}
  payload=json.dumps(value).encode() if value is not None else None
  if payload is not None:headers["Content-Type"]="application/json"
  with urllib.request.urlopen(urllib.request.Request(url,data=payload,headers=headers,method=method),timeout=180) as r:
   return None if r.status==204 else json.load(r)
 tag=manifest["release_id"]
 try:release=api("/repos/"+REPO+"/releases/tags/"+tag)
 except urllib.error.HTTPError as e:
  if e.code!=404:raise
  release=api("/repos/"+REPO+"/releases","POST",{"tag_name":tag,"target_commitish":os.environ.get("GITHUB_SHA","main"),"name":"B13 GitHub updater","body":manifest["changelog"],"draft":True,"prerelease":False})
 if not release["draft"]:
  print("Release already published; immutable release left unchanged: "+release["html_url"]);return
 existing={a["name"]:a for a in api("/repos/"+REPO+"/releases/"+str(release["id"])+"/assets")}
 for p in files:
  if p.name in existing:api("/repos/"+REPO+"/releases/assets/"+str(existing[p.name]["id"]),"DELETE")
  url=release["upload_url"].split("{",1)[0]+"?name="+urllib.parse.quote(p.name)
  headers={"Authorization":"Bearer "+token,"Accept":"application/vnd.github+json","User-Agent":"MOE-Release-Builder","Content-Type":"application/octet-stream"}
  with urllib.request.urlopen(urllib.request.Request(url,data=p.read_bytes(),headers=headers,method="POST"),timeout=300) as r:
   meta=json.load(r)
  if meta["size"]!=p.stat().st_size or meta.get("digest", "sha256:"+sha(p.read_bytes()))!="sha256:"+sha(p.read_bytes()):
   raise RuntimeError("Uploaded release asset verification failed: "+p.name)
 published=api("/repos/"+REPO+"/releases/"+str(release["id"]),"PATCH",{"draft":False,"make_latest":"true"})
 print("Published and verified release: "+published["html_url"])
if __name__=="__main__":
 parser=argparse.ArgumentParser();parser.add_argument("--source");parser.add_argument("--publish",action="store_true")
 args=parser.parse_args();m,f=build(args.source)
 if args.publish:publish(m,f)
