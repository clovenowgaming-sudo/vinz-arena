import zipfile, os
p = os.path.expanduser("~/storage/downloads/VinzArena.apk")
print(p, os.path.getsize(p))
z = zipfile.ZipFile(p)
print("zip test:", z.testzip())
print([n for n in z.namelist() if n.endswith(".so")])
