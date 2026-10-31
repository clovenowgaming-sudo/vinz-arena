import json, struct, os, glob
D = os.path.dirname(os.path.abspath(__file__))
out = open(os.path.join(D, "info2.log"), "w")
base = os.path.join(D, "android/src/main/assets/models")
def load(p):
    b = open(p, "rb").read()
    if p.endswith(".glb"):
        l = struct.unpack("<I", b[12:16])[0]
        return json.loads(b[20:20 + l])
    return json.loads(b)
def info(p):
    j = load(p)
    anims = [a.get("name") for a in j.get("animations", [])]
    joints = max([len(s["joints"]) for s in j.get("skins", [])] + [0])
    mins, maxs = [1e9] * 3, [-1e9] * 3
    for m in j["meshes"]:
        for pr in m["primitives"]:
            a = j["accessors"][pr["attributes"]["POSITION"]]
            for i in range(3):
                mins[i] = min(mins[i], a["min"][i]); maxs[i] = max(maxs[i], a["max"][i])
    sc = [(n.get("name"), n.get("scale")) for n in j["nodes"] if n.get("scale")][:3]
    tr = [(n.get("name"), n.get("rotation")) for n in j["nodes"] if n.get("rotation")][:2]
    out.write("%s\n  joints=%d anims=%s\n  bbox=%s..%s scales=%s rot=%s\n" % (
        os.path.relpath(p, base), joints, anims, [round(x, 2) for x in mins], [round(x, 2) for x in maxs], sc, tr))
for p in sorted(glob.glob(base + "/*.glb")):
    if os.path.basename(p) in ("knight.glb", "mage.glb", "rogue.glb", "barbarian.glb", "skel_minion.glb", "skel_warrior.glb"):
        info(p)
for p in sorted(glob.glob(base + "/hex/**/*.gltf", recursive=True)):
    info(p)
out.write("DONE\n")
