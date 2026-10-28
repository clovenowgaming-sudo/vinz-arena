import os, subprocess
D = os.path.dirname(os.path.abspath(__file__))
os.chdir(D)
sh = ("rm -rf out; gh run download 40586305681 -n VinzArena -D out > dl.log 2>&1; "
      "for d in $HOME/storage/downloads /sdcard/Download /storage/emulated/0/Download; do "
      "if [ -d \"$d\" ]; then cp out/VinzArena.apk \"$d/VinzArena.apk\" && echo \"COPIED $d\" >> dl.log && break; fi; done; echo DONE >> dl.log")
subprocess.Popen(["bash", "-c", sh], start_new_session=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
print("download dimulai di background")
