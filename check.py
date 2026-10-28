import os, subprocess
os.chdir(os.path.dirname(os.path.abspath(__file__)))
def sh(c):
    r = subprocess.run(c, shell=True, capture_output=True, text=True)
    return r.stdout + r.stderr
print(sh("gh run list --limit 1"))
rid = sh("gh run list --limit 1 --json databaseId -q '.[0].databaseId'").strip()
st = sh("gh run list --limit 1 --json status,conclusion -q '.[0].status+\" \"+.[0].conclusion'").strip()
print(st)
if st.startswith("completed") and "failure" in st:
    out = sh("gh run view %s --log-failed" % rid)
    lines = [l for l in out.splitlines() if ("error:" in l.lower() or "What went wrong" in l or "FAILED" in l)]
    print("\n".join(lines[:40]) if lines else out[-2000:])
