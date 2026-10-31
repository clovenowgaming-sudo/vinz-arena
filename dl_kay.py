import json, os, urllib.request, posixpath
D = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(D, "android/src/main/assets/models")
log = open(os.path.join(D, "kay_dl.log"), "w")
def L(*a):
    log.write(" ".join(str(x) for x in a) + "\n"); log.flush()
def get(u):
    req = urllib.request.Request(u, headers={"User-Agent": "Mozilla/5.0"})
    return urllib.request.urlopen(req, timeout=90).read()
def save(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    open(path, "wb").write(data)

AD = "KayKit-Game-Assets/KayKit-Character-Pack-Adventures-1.0/main/addons/kaykit_character_pack_adventures/Characters/gltf/"
SK = "KayKit-Game-Assets/KayKit-Character-Pack-Skeletons-1.0/main/addons/kaykit_character_pack_skeletons/Characters/gltf/"
chars = {"knight.glb": AD + "Knight.glb", "mage.glb": AD + "Mage.glb", "rogue.glb": AD + "Rogue.glb",
         "barbarian.glb": AD + "Barbarian.glb", "skel_minion.glb": SK + "Skeleton_Minion.glb",
         "skel_warrior.glb": SK + "Skeleton_Warrior.glb"}
for name, p in chars.items():
    try:
        data = get("https://raw.githubusercontent.com/" + p)
        save(os.path.join(ASSETS, name), data)
        L("OK", name, len(data), data[:4])
    except Exception as e:
        L("FAIL", name, e)

HEX = "KayKit-Game-Assets/KayKit-Medieval-Hexagon-Pack-1.0/main/"
HB = "addons/kaykit_medieval_hexagon_pack/Assets/gltf/buildings/"
bld = ["blue/building_tower_A_blue", "red/building_tower_A_red", "blue/building_tower_B_blue", "red/building_tower_B_red",
       "blue/building_castle_blue", "red/building_castle_red", "blue/building_tower_base_blue", "red/building_tower_base_red"]
for b in bld:
    try:
        rel = HB + b + ".gltf"
        data = get("https://raw.githubusercontent.com/" + HEX + rel)
        save(os.path.join(ASSETS, "hex", rel), data)
        j = json.loads(data)
        uris = [x["uri"] for x in j.get("buffers", []) + j.get("images", []) if "uri" in x and not x["uri"].startswith("data:")]
        for u in uris:
            r2 = posixpath.normpath(posixpath.join(posixpath.dirname(rel), u))
            d2 = get("https://raw.githubusercontent.com/" + HEX + r2)
            save(os.path.join(ASSETS, "hex", r2), d2)
            L("  dep", r2, len(d2))
        L("OK", b, len(data))
    except Exception as e:
        L("FAIL", b, e)
L("DONE")
