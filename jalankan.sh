#!/data/data/com.termux/files/usr/bin/bash
# Jalankan server Vinz Arena (Node.js) + ngrok TCP.
# Sekali saja sebelumnya:  ~/ngrok/ngrok config add-authtoken TOKEN_NGROK_KAMU
cd "$(dirname "$0")"
pkill -f "node server.js" 2>/dev/null
termux-wake-lock 2>/dev/null
node server.js 7777 > server.log 2>&1 &
sleep 1
echo "Server jalan di port 7777. Menyalakan ngrok..."
echo "Salin alamat 'tcp://HOST:PORT' -> tulis HOST:PORT di kolom server aplikasi."
~/ngrok/ngrok tcp 7777
