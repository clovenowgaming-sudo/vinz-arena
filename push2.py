import os, subprocess
D = os.path.dirname(os.path.abspath(__file__))
os.chdir(D)
open(".gitignore", "a").write("\nks/\n*.log\ndl.log\nvinzmoba-data.json\n__pycache__/\n")
def sh(c):
    r = subprocess.run(c, shell=True, capture_output=True, text=True)
    print("$", c); print(r.stdout + r.stderr, flush=True)
sh("git rm -r --cached ks out *.log vinzmoba-data.json -q --ignore-unmatch")
sh("git add -A")
sh('git commit -m "Server-authoritative Python server, glTF models, minions, jungle, sfx, MLBB-style UI" ')
sh("git push origin main")
