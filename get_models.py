import os, urllib.request
D = os.path.dirname(os.path.abspath(__file__))
dst = os.path.join(D, "android/src/main/assets/models")
os.makedirs(dst, exist_ok=True)
base = "https://raw.githubusercontent.com/mrdoob/three.js/dev/examples/models/gltf/"
files = {
    "robot.glb": base + "RobotExpressive/RobotExpressive.glb",
    "soldier.glb": base + "Soldier.glb",
    "xbot.glb": base + "Xbot.glb",
    "fox.glb": "https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Models/main/2.0/Fox/glTF-Binary/Fox.glb",
}
log = open(os.path.join(D, "models.log"), "w")
for name, url in files.items():
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})
        data = urllib.request.urlopen(req, timeout=60).read()
        open(os.path.join(dst, name), "wb").write(data)
        log.write("OK %s %d bytes %s\n" % (name, len(data), data[:4])); log.flush()
    except Exception as e:
        log.write("FAIL %s %s\n" % (name, e)); log.flush()
log.write("DONE\n")
