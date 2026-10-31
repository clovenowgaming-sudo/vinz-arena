import os, subprocess, sys
D = os.path.dirname(os.path.abspath(__file__))
os.chdir(D)
sh = ("mkdir -p $HOME/ngrok && cd $HOME/ngrok && "
      "(curl -L -m 120 -o ngrok.tgz https://bin.equinox.io/c/bNyj1mQVY4c/ngrok-v3-stable-linux-arm64.tgz && tar xzf ngrok.tgz && chmod +x ngrok && ./ngrok version) > " + D + "/ngrok_dl.log 2>&1; echo DONE >> " + D + "/ngrok_dl.log")
subprocess.Popen(["bash", "-c", sh], start_new_session=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
print("ngrok download started")
