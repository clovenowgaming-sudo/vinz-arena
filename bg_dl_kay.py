import os, subprocess, sys
D = os.path.dirname(os.path.abspath(__file__))
subprocess.Popen([sys.executable, os.path.join(D, "dl_kay.py")], start_new_session=True,
                 stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, cwd=D)
print("started")
