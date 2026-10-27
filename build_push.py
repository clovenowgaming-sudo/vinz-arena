import os, subprocess, sys

D = os.path.dirname(os.path.abspath(__file__))
os.chdir(D)


def sh(c, check=True):
    print("$", c, flush=True)
    r = subprocess.run(c, shell=True, capture_output=True, text=True)
    print(r.stdout + r.stderr, flush=True)
    if check and r.returncode:
        raise SystemExit("GAGAL: " + c)
    return r.stdout.strip()


if not os.path.isdir(".git"):
    sh("git init")
if not sh("git config user.email", check=False):
    sh("git config user.email vinz@local")
    sh("git config user.name VinzBrother")
sh("git add -A")
sh('git commit -m "Vinz Arena 5v5 MOBA" || true', check=False)
sh("git branch -M main")
if "origin" not in sh("git remote", check=False):
    sh("gh repo create vinz-arena --public --source=. --remote=origin --push")
else:
    sh("git push -u origin main")
print("PUSH OK. Jalankan fetch_apk.py untuk tunggu build & copy APK ke Download.")
