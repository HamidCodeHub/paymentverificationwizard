#!/usr/bin/env bash
# One-time setup of a fresh Oracle Cloud Ubuntu 24.04 (Ampere A1 / ARM) machine.
#   bash ~/pantea/stack/server-setup.sh
# Safe to run twice. Log out and back in afterwards so the docker group applies.
set -euo pipefail

echo "== Docker"
sudo apt-get update -q
sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -q docker.io docker-compose-v2 iptables-persistent
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"

echo "== Firewall"
# Oracle's Ubuntu images ship iptables rules that REJECT everything except SSH. Opening ports in the cloud
# console's security list is not enough on its own; the machine has to allow them too.
for port in 80 443; do
  if ! sudo iptables -C INPUT -p tcp -m state --state NEW --dport "$port" -j ACCEPT 2>/dev/null; then
    sudo iptables -I INPUT 1 -p tcp -m state --state NEW --dport "$port" -j ACCEPT
  fi
done
sudo netfilter-persistent save

echo "== Data folders"
mkdir -p ~/pantea/data/models ~/pantea/data/themes ~/pantea/data/references
# The containers run as uid 1001 and write their embedding caches into models/.
chmod -R a+rwX ~/pantea/data/models

echo
echo "Done. Log out and back in (docker group), then:"
echo "  cd ~/pantea/stack && cp -n .env.example .env && nano .env"
echo "  docker compose up -d --build"
