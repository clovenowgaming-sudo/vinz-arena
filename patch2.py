import os, re, subprocess
D = os.path.dirname(os.path.abspath(__file__))
os.chdir(D)
p = "core/src/main/java/com/vinz/moba/MatchScreen.java"
s = open(p).read()

s = s.replace("ArrayList<Actor3D> pbrList = new ArrayList<Actor3D>();",
              "ArrayList<Actor3D> pbrList = new ArrayList<Actor3D>();\n        ArrayList<ModelInstance> pbrProps = new ArrayList<ModelInstance>();")
s = s.replace("if (e.kind >= 4) { drawStatic(e); continue; }", "if (e.kind >= 4) { drawStatic(e, pbrProps); continue; }")
s = s.replace("if (mbP != null && !pbrList.isEmpty()) {\n            mbP.begin(cam);\n            for (Actor3D a : pbrList) mbP.render(a.mi, envP);\n            mbP.end();",
              "if (mbP != null && (!pbrList.isEmpty() || !pbrProps.isEmpty())) {\n            mbP.begin(cam);\n            for (Actor3D a : pbrList) mbP.render(a.mi, envP);\n            for (ModelInstance pm : pbrProps) mbP.render(pm, envP);\n            mbP.end();")
s = s.replace("(e.kind == 4 ? 7.5f : 7f)", "(e.kind == 4 ? 9.8f : 17.5f)")

new_static = '''void drawStatic(G.Ent e, ArrayList<ModelInstance> pbrProps) {
        boolean tower = e.kind == 4;
        Color tc = e.team == 0 ? World.BLUE : World.RED;
        if (e.parts == null) {
            ModelInstance prop = Actor3D.prop(tower ? "tower" : "castle", e.team);
            if (tower) {
                if (prop != null) prop.transform.setToTranslation(e.x, 0, e.z).scale(3.1f, 3.1f, 3.1f);
                e.parts = new ModelInstance[]{prop, new ModelInstance(e.team == 0 ? world.tTop0 : world.tTop1), ringInst(tc, 0.6f),
                        new ModelInstance(world.tBase), new ModelInstance(world.tBody)};
                e.parts[3].transform.setToTranslation(e.x, 0.3f, e.z);
                e.parts[4].transform.setToTranslation(e.x, 2.8f, e.z);
            } else {
                if (prop != null) prop.transform.setToTranslation(e.x, 0, e.z).scale(3.4f, 3.4f, 3.4f);
                e.parts = new ModelInstance[]{prop, new ModelInstance(e.team == 0 ? world.cry0 : world.cry1), ringInst(tc, 0.5f)};
            }
        }
        boolean hasProp = e.parts[0] != null;
        float top = tower ? (hasProp ? 2.49f * 3.1f : 5.9f) : (hasProp ? 3.98f * 3.4f : 3.5f);
        float pulse = 1f + 0.12f * (float) Math.sin(t * 3 + e.id);
        float bob = (float) Math.sin(t * 2 + e.id) * (tower ? 0.25f : 0.5f);
        ModelInstance orb = e.parts[1];
        if (tower) orb.transform.setToTranslation(e.x, top + 1.2f + bob, e.z).scale(pulse, pulse, pulse);
        else orb.transform.setToTranslation(e.x, top + 3.4f + bob, e.z).rotate(Vector3.Y, t * 40).scale(pulse, pulse, pulse);
        float r = (tower ? 3.6f : 8.5f) * (1f + 0.04f * (float) Math.sin(t * 2 + e.id));
        e.parts[2].transform.setToTranslation(e.x, 0.1f, e.z).scale(r, 1, r);
        if (hasProp) pbrProps.add(e.parts[0]);
        else if (tower) { mbA.render(e.parts[3], env); mbA.render(e.parts[4], env); }
        mbA.render(orb, env);
        mbA.render(e.parts[2], env);
    }

    '''
a = s.index("void drawStatic(G.Ent e) {")
b = s.index("// ------------------------------------------------------------ HUD")
s = s[:a] + new_static + s[b:]
open(p, "w").write(s)

# drop the old, unused models from the APK
for n in ["soldier", "xbot", "robot", "fox"]:
    f = "android/src/main/assets/models/%s.glb" % n
    if os.path.exists(f):
        os.remove(f)
open("CREDITS.md", "w").write("""# Credits (all free assets)
- Characters (Knight, Mage, Rogue, Barbarian, Skeleton Minion, Skeleton Warrior): KayKit Character Packs by Kay Lousberg (CC0) - kaylousberg.com
- Buildings (towers, castle): KayKit Medieval Hexagon Pack by Kay Lousberg (CC0)
- glTF loading: gdx-gltf by mgsx-dev (Apache-2.0)
Sounds are synthesized in code.
""")
print("patched", "drawStatic(e, pbrProps)" in s, "pbrProps.isEmpty" in s)
def sh(c):
    r = subprocess.run(c, shell=True, capture_output=True, text=True)
    print("$", c); print((r.stdout + r.stderr)[-600:], flush=True)
sh("git add -A")
sh('git commit -m "KayKit characters + hexagon towers/castle, per-skill animations"')
sh("git push origin main")
