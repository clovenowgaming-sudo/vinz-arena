import json, struct, os, glob
D = os.path.dirname(os.path.abspath(__file__))
out = open(os.path.join(D, "models_info.log"), "w")
for p in sorted(glob.glob(os.path.join(D, "android/src/main/assets/models/*.glb"))):
    b = open(p, "rb").read()
    l = struct.unpack("<I", b[12:16])[0]
    j = json.loads(b[20:20 + l])
    anims = [a.get("name") for a in j.get("animations", [])]
    joints = max([len(s["joints"]) for s in j.get("skins", [])] + [0])
    mins, maxs = [1e9] * 3, [-1e9] * 3
    for m in j["meshes"]:
        for pr in m["primitives"]:
            a = j["accessors"][pr["attributes"]["POSITION"]]
            for i in range(3):
                mins[i] = min(mins[i], a["min"][i]); maxs[i] = max(maxs[i], a["max"][i])
    out.write("%s anims=%s joints=%d bbox_min=%s bbox_max=%s asset=%s\n" % (
        os.path.basename(p), anims, joints, [round(x, 2) for x in mins], [round(x, 2) for x in maxs], j.get("asset", {}).get("extras", j.get("asset"))))
    out.write("  node scales: %s\n" % [(n.get("name"), n.get("scale")) for n in j["nodes"] if n.get("scale")][:5])
out.write("DONE\n")
