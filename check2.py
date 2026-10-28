import os, subprocess
os.chdir(os.path.dirname(os.path.abspath(__file__)))
rid = subprocess.run("gh run list --limit 1 --json databaseId -q '.[0].databaseId'", shell=True, capture_output=True, text=True).stdout.strip()
r = subprocess.run("gh run view %s --log-failed" % rid, shell=True, capture_output=True, text=True)
lines = r.stdout.splitlines()
out = []
for l in lines:
    t = l.split("Z ", 1)[-1]
    if ".java" in t or "What went wrong" in t or "Could not" in t or "symbol:" in t or "location:" in t:
        out.append(t[:230])
open("err.log", "w").write("\n".join(out[:70]))
print("run", rid, "lines", len(lines))
print("\n".join(out[:70]))
