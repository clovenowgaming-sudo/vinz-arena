import os
D = os.path.dirname(os.path.abspath(__file__))
p = os.path.join(D, "core/src/main/java/com/vinz/moba/LoginScreen.java")
s = open(p).read()
s = s.replace('pf.getString("host", "127.0.0.1:7777")', 'pf.getString("server", "127.0.0.1:7777")')
s = s.replace('pf.putString("host", hs);', 'pf.putString("server", hs);')
s = s.replace('"5 v 5  MOBA"', '"5 v 5  MOBA   -   Mod By Vinz :3"')
open(p, "w").write(s)
open(os.path.join(D, "core/build.gradle"), "w").write("""plugins { id 'java-library' }
java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
dependencies {
    api 'com.badlogicgames.gdx:gdx:1.12.1'
    api 'io.github.mgsx-dev.gdx-gltf:gltf:2.2.1'
}
""")
open(os.path.join(D, "CREDITS.md"), "w").write("""# Credits
3D models (free):
- RobotExpressive by Tomas Laulhe (CC0), via three.js examples
- Soldier and Xbot (Mixamo characters), via three.js examples
- Fox by PixelMannen and @tomkranis (CC-BY 4.0), Khronos glTF-Sample-Models
glTF loading: gdx-gltf by mgsx-dev (Apache-2.0)
""")
print("patched", "server" in s)
