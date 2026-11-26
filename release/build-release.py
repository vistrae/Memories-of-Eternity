"""Reproduce signed B27 APKs and publish them without exposing private signing keys."""
import argparse,base64,hashlib,io,json,os,sys,urllib.request,urllib.parse,urllib.error,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
RELEASE=ROOT/"release"/"b27-gacha-rates"
REPO="vistrae/Memories-of-Eternity"
SOURCE_SHA="26d06a2c0825562e55548102445690c31684299bc1ddd347cb161c897e0b23ea"
HELPER_SHA="eeefb24140b40cb62191b5e230ae15867cb49d886ac77bdb58f6a492c3a3ceca"
SAI_CERT="a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc"

def sha(data):return hashlib.sha256(data).hexdigest()

def download_source():
 url="https://github.com/vistrae/Memories-of-Eternity/releases/download/b21-dormitory-codex/moe-base.apk"
 with urllib.request.urlopen(url,timeout=180) as response:data=response.read(100*1024*1024+1)
 if sha(data)!=SOURCE_SHA:raise RuntimeError("Published B21 bootstrap SHA256 mismatch.")
 return data

def checked(data,spec,label):
 if len(data)!=spec["size"] or sha(data)!=spec["sha256"]:
  raise RuntimeError(label+" failed signed-manifest size/SHA256 verification.")
 return data

def decode_patch(name):
 parts=sorted(RELEASE.glob(name.replace(".b64",".part*.b64")))
 encoded="".join(part.read_text().strip() for part in parts) if parts else (RELEASE/name).read_text().strip()
 return base64.b64decode(encoded,validate=True)

def same_game_content(canonical,variant):
 with zipfile.ZipFile(io.BytesIO(canonical)) as a,zipfile.ZipFile(io.BytesIO(variant)) as b:
  an=[n for n in a.namelist() if not n.upper().startswith("META-INF/")]
  bn=[n for n in b.namelist() if not n.upper().startswith("META-INF/")]
  if len(an)!=len(set(an)) or len(bn)!=len(set(bn)) or set(an)!=set(bn):
   raise RuntimeError("Signing variant changed the game-content entry list.")
  for name in an:
   if sha(a.read(name))!=sha(b.read(name)):
    raise RuntimeError("Signing variant changed game content: "+name)

def write_zip_entry(archive,name,data):
 info=zipfile.ZipInfo(name,(2026,11,25,0,0,0));info.create_system=3
 info.external_attr=0o100644<<16;info.compress_type=zipfile.ZIP_DEFLATED
 archive.writestr(info,data,compress_type=zipfile.ZIP_DEFLATED,compresslevel=1)

def verify_manifest(data):
 import hmac,xml.etree.ElementTree as ET
 config=json.loads((ROOT/"updater/windows/GitHub-Update-Config.json").read_text(encoding="utf-8-sig"))
 key=ET.fromstring(config["public_key_xml"])
 n=int.from_bytes(base64.b64decode(key.findtext("Modulus")),"big")
 e=int.from_bytes(base64.b64decode(key.findtext("Exponent")),"big")
 sig=base64.b64decode((RELEASE/"moe-update.json.sig").read_bytes(),validate=True)
 size=(n.bit_length()+7)//8
 if len(sig)!=size or int.from_bytes(sig,"big")>=n:raise RuntimeError("Invalid manifest signature size")
 digest=bytes.fromhex("3031300d060960864801650304020105000420")+hashlib.sha256(data).digest()
 expected=b"\x00\x01"+b"\xff"*(size-len(digest)-3)+b"\x00"+digest
 if not hmac.compare_digest(pow(int.from_bytes(sig,"big"),e,n).to_bytes(size,"big"),expected):raise RuntimeError("Manifest signature mismatch")

def build(source=None):
 import bsdiff4
 # Decode exact signed bytes; text newline conversion would invalidate the signature.
 manifest_bytes=base64.b64decode((RELEASE/"moe-update.json.b64").read_text().strip(),validate=True)
 verify_manifest(manifest_bytes)
 (RELEASE/"moe-update.json").write_bytes(manifest_bytes)
 manifest=json.loads(manifest_bytes.decode("utf-8-sig"))
 if manifest["release_id"]!="b27-gacha-rates" or manifest["sequence"]!=27 or manifest["required_client_sequence"]!=27:
  raise RuntimeError("Wrong B27 release manifest.")
 original=Path(source).read_bytes() if source else download_source()
 if sha(original)!=SOURCE_SHA:raise RuntimeError("Wrong bootstrap APK")
 apk=checked(bsdiff4.patch(original,decode_patch("moe-base.bsdiff.b64")),manifest["apk"],"Canonical APK")
 target=RELEASE/"moe-base.apk";target.write_bytes(apk)
 variants=manifest.get("apk_variants",[])
 if len(variants)!=1 or variants[0]["signing_certificate_sha256"]!=SAI_CERT:
  raise RuntimeError("Expected exactly the B27 standard-SAI signing variant.")
 variant_spec=variants[0]
 if Path(urllib.parse.urlparse(variant_spec["url"]).path).name!="moe-base-sai.apk":
  raise RuntimeError("Unexpected SAI variant asset name.")
 sai=checked(bsdiff4.patch(apk,decode_patch("moe-base-sai.bsdiff.b64")),variant_spec,"SAI-compatible APK")
 same_game_content(apk,sai)
 sai_target=RELEASE/"moe-base-sai.apk";sai_target.write_bytes(sai)
 bundle=RELEASE/"MOE-Windows-Updater-B27.zip"
 with zipfile.ZipFile(bundle,"w",zipfile.ZIP_DEFLATED,compresslevel=1) as z:
  for path in sorted((ROOT/"updater/windows").glob("*")):
   if path.is_file():write_zip_entry(z,path.name,path.read_bytes())
  write_zip_entry(z,"MOE-Offline-Campaign-Chapter13-Rewards.apk",target.read_bytes())
  write_zip_entry(z,"GitHub-Updater-Readme.txt",(ROOT/"README.md").read_bytes())
 helper=RELEASE/"MOE-Android-Update-Installer.apk"
 helper_bytes=decode_patch("phone-installer.apk.b64")
 if sha(helper_bytes)!=HELPER_SHA:raise RuntimeError("B27 phone installer checksum mismatch")
 helper.write_bytes(helper_bytes)
 print("Signed B27 canonical and SAI-compatible APKs reproduced and verified; game content is identical.")
 return manifest,[target,sai_target,RELEASE/"moe-update.json",RELEASE/"moe-update.json.sig",RELEASE/"CHANGELOG.txt",bundle,helper]

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
  release=api("/repos/"+REPO+"/releases","POST",{"tag_name":tag,"target_commitish":os.environ.get("GITHUB_SHA","main"),"name":"B27 Memories of Eternity offline playtest","body":manifest["changelog"],"draft":True,"prerelease":False})
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
