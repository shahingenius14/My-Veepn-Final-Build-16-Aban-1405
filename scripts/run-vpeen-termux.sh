#!/data/data/com.termux/files/usr/bin/bash
set -e
echo "===================================================="
echo "  My VeePN v2.0.1 Ultimate - Termux Anti-Censorship"
echo "===================================================="
pkg update -y && pkg install -y python git
if [ ! -d "$HOME/VPeeN" ]; then
    git clone https://github.com/SirBNL/VPeeN.git "$HOME/VPeeN"
else
    cd "$HOME/VPeeN" && git pull
fi
cd "$HOME/VPeeN"
python -m vpeen.cli run --socks-port 1080 --http-port 8080
