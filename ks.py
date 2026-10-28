import os, subprocess
D = os.path.dirname(os.path.abspath(__file__))
os.chdir(D)
sh = ("rm -rf ks; gh run download 40586305681 -n keystore -D ks > ks.log 2>&1; "
      "[ -f ks/debug.keystore ] && cp ks/debug.keystore debug.keystore && echo KEYSTORE_OK >> ks.log; "
      "gh run view 40586305681 --log 2>/dev/null | grep -i 'SHA1' | head -3 >> ks.log; echo DONE >> ks.log")
subprocess.Popen(["bash", "-c", sh], start_new_session=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
print("started")
