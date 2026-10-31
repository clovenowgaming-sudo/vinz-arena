import os, subprocess
os.chdir(os.path.dirname(os.path.abspath(__file__)))
def sh(c): return subprocess.run(c, shell=True, capture_output=True, text=True).stdout
print(sh("gh run list --limit 1 | head -1"))
rid = sh("gh run list --limit 1 --json databaseId -q '.[0].databaseId'").strip()
st = sh("gh run list --limit 1 --json status,conclusion -q '.[0].status+\" \"+.[0].conclusion'").strip()
print(st)
if st.startswith("completed") and "failure" in st:
    out = []
    lines = sh("gh run view %s --log-failed" % rid).splitlines()
    for i, l in enumerate(lines):
        t = l.split("Z ", 1)[-1]
        if t.startswith("\tat ") or t.startswith("Caused by"):
            continue
        if ".java:" in t or "What went wrong" in t or "Could not" in t or "symbol:" in t or "location:" in t or "error:" in t:
            out.append(t[:240])
    print("\n".join(out[:40]))
