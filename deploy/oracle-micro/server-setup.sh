#!/usr/bin/env bash
# One-time setup of a fresh Oracle Cloud Ubuntu 24.04 VM.Standard.E2.1.Micro (AMD, 1 GB RAM).
#   bash ~/pantea/stack/server-setup.sh
# Safe to run twice. Log out and back in afterwards so the docker group applies.
set -euo pipefail

echo "== Swap (2 GB)"
# 1 GB RAM runs one service fine, but the one-time Maven build inside docker needs more. Swap also absorbs
# the rare moment when a large photo and a Tesseract run coincide, instead of the kernel killing the JVM.
if ! swapon --show | grep -q /swapfile; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile
  sudo swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi
# Prefer RAM; touch swap only under real pressure.
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-pantea.conf >/dev/null
sudo sysctl -q --system

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
free -h
echo
echo "Done. Log out and back in (docker group), then:"
echo "  cd ~/pantea/stack && cp -n .env.example .env && nano .env"
echo "  docker compose up -d --build      # first build ~15-20 min on this small machine"
