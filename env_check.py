import os, subprocess, shutil, glob
D = os.path.dirname(os.path.abspath(__file__))
def sh(c):
    r = subprocess.run(c, shell=True, capture_output=True, text=True)
    return (r.stdout + r.stderr).strip()
out = []
out.append("node: " + (shutil.which("node") or "TIDAK ADA") + " " + sh("node -v"))
out.append("ngrok: " + (shutil.which("ngrok") or "TIDAK ADA"))
p = os.path.join(D, "dl.log")
out.append("dl.log: " + (open(p).read().strip() if os.path.exists(p) else "-"))
out.append("downloads: " + sh("ls -la ~/storage/downloads/ 2>&1 | head -15"))
out.append("out: " + sh("ls -la " + D + "/out 2>&1"))
out.append("procs: " + sh("ps aux 2>/dev/null | grep -E 'gh run|server.py' | grep -v grep | head"))
open(os.path.join(D, "env.log"), "w").write("\n".join(out))
print("\n".join(out))
