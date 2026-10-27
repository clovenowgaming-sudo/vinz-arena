import os, subprocess, shutil, time, glob

D = os.path.dirname(os.path.abspath(__file__))
os.chdir(D)


def sh(c, check=True):
    print("$", c, flush=True)
    r = subprocess.run(c, shell=True, capture_output=True, text=True)
    print(r.stdout + r.stderr, flush=True)
    if check and r.returncode:
        raise SystemExit("GAGAL: " + c)
    return r.stdout.strip()


rid = ""
for _ in range(20):
    rid = sh("gh run list --limit 1 --json databaseId -q '.[0].databaseId'", check=False)
    if rid:
        break
    time.sleep(5)
if not rid:
    raise SystemExit("run workflow tidak ditemukan")
sh("gh run watch %s --exit-status" % rid)
shutil.rmtree("out", ignore_errors=True)
sh("gh run download %s -n VinzArena -D out" % rid)
apk = glob.glob("out/*.apk")[0]
for dst in [os.path.expanduser("~/storage/downloads"), "/sdcard/Download", "/storage/emulated/0/Download"]:
    if os.path.isdir(dst):
        shutil.copy(apk, os.path.join(dst, "VinzArena.apk"))
        print("APK disalin ke", dst)
        break
else:
    print("Folder Download tidak ketemu. Jalankan: termux-setup-storage, lalu: cp", apk, "~/storage/downloads/")
