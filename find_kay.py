import json, os, urllib.request
D = os.path.dirname(os.path.abspath(__file__))
out = open(os.path.join(D, "kay.log"), "w")
def get(u):
    req = urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0"})
    return urllib.request.urlopen(req, timeout=60).read()
for repo in ["KayKit-Character-Pack-Adventures-1.0", "KayKit-Character-Pack-Adventures", "KayKit-Medieval-Hexagon-Pack-1.0", "KayKit-Character-Pack-Skeletons-1.0"]:
    for br in ["main", "master"]:
        try:
            j = json.loads(get("https://api.github.com/repos/KayKit-Game-Assets/%s/git/trees/%s?recursive=1" % (repo, br)))
            paths = [t["path"] for t in j["tree"] if t["path"].lower().endswith((".glb", ".gltf"))]
            out.write("REPO %s (%s): %d files\n" % (repo, br, len(paths)))
            for p in paths[:60]:
                out.write("  " + p + "\n")
            break
        except Exception as e:
            out.write("FAIL %s %s: %s\n" % (repo, br, e))
    out.flush()
out.write("DONE\n")
