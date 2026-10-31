import json, struct, os
D = os.path.dirname(os.path.abspath(__file__))
base = os.path.join(D, "android/src/main/assets/models")
out = open(os.path.join(D, "info3.log"), "w")
for n in ["knight.glb", "mage.glb", "rogue.glb", "barbarian.glb", "skel_minion.glb", "skel_warrior.glb"]:
    p = os.path.join(base, n)
    if not os.path.exists(p):
        continue
    b = open(p, "rb").read()
    l = struct.unpack("<I", b[12:16])[0]
    j = json.loads(b[20:20 + l])
    names = [(x.get("name"), x["mesh"]) for x in j["nodes"] if "mesh" in x]
    out.write("%s size=%d mesh-nodes=%s\n" % (n, len(b), [a for a, _ in names]))
    # per-mesh bbox
    for x in j["nodes"]:
        if "mesh" in x:
            m = j["meshes"][x["mesh"]]
            a = j["accessors"][m["primitives"][0]["attributes"]["POSITION"]]
            out.write("   %s y=%.2f..%.2f x=%.2f..%.2f\n" % (x.get("name"), a["min"][1], a["max"][1], a["min"][0], a["max"][0]))
out.write("DONE\n")
