#!/bin/bash
# Script de migration one-shot — à lancer UNE SEULE FOIS sur le serveur
# Usage: bash setup-server.sh
#
# Ce script suppose que l'appli tourne déjà dans ~/chiron/ (ou adapter CURRENT_DIR)
# et migre vers la structure /opt/chiron/

set -e

CURRENT_DIR="${1:-$HOME/chiron}"   # Dossier actuel de l'appli
DEPLOY_DIR="/opt/chiron"
SERVICE_USER="$USER"

echo "=== Migration Chiron vers $DEPLOY_DIR ==="
echo "Source actuelle : $CURRENT_DIR"

# ── 1. Créer la structure cible ───────────────────────────────────────────────
sudo mkdir -p $DEPLOY_DIR/uploads/images
sudo mkdir -p $DEPLOY_DIR/frontend

# ── 2. Copier le .env (ne sera JAMAIS touché par le CI) ──────────────────────
if [ -f "$CURRENT_DIR/.env" ]; then
  sudo cp $CURRENT_DIR/.env $DEPLOY_DIR/.env
  echo "✓ .env copié"
fi

# ── 3. Migrer les icônes utilisateurs ─────────────────────────────────────────
# Cherche les images dans les endroits habituels
for uploads_candidate in \
    "$CURRENT_DIR/uploads/images" \
    "$CURRENT_DIR/uploads" \
    "$CURRENT_DIR/dist/uploads/images"; do
  if [ -d "$uploads_candidate" ] && [ "$(ls -A $uploads_candidate 2>/dev/null)" ]; then
    sudo cp -r $uploads_candidate/. $DEPLOY_DIR/uploads/images/
    echo "✓ Icônes copiées depuis $uploads_candidate"
    break
  fi
done

# ── 4. Copier le JAR actuel ───────────────────────────────────────────────────
if [ -f "$CURRENT_DIR/app.jar" ]; then
  sudo cp $CURRENT_DIR/app.jar $DEPLOY_DIR/app.jar
  echo "✓ JAR copié"
fi

# ── 5. Copier le frontend actuel ──────────────────────────────────────────────
for front_candidate in \
    "$CURRENT_DIR/frontend" \
    "$CURRENT_DIR/dist" \
    "$CURRENT_DIR/public"; do
  if [ -d "$front_candidate" ]; then
    sudo cp -r $front_candidate/. $DEPLOY_DIR/frontend/
    echo "✓ Frontend copié depuis $front_candidate"
    break
  fi
done

# ── 6. Permissions ───────────────────────────────────────────────────────────
sudo chown -R $SERVICE_USER:$SERVICE_USER $DEPLOY_DIR
sudo chown -R www-data:www-data $DEPLOY_DIR/frontend
echo "✓ Permissions OK"

# ── 7. Créer le service systemd ───────────────────────────────────────────────
sudo tee /etc/systemd/system/chiron-back.service > /dev/null << EOF
[Unit]
Description=Chiron Backend
After=network.target postgresql.service docker.service
Requires=docker.service

[Service]
Type=simple
User=$SERVICE_USER
WorkingDirectory=$DEPLOY_DIR
EnvironmentFile=$DEPLOY_DIR/.env
Environment="UPLOADS_DIR=$DEPLOY_DIR/uploads/images"
ExecStart=/usr/bin/java -jar $DEPLOY_DIR/app.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable chiron-back
echo "✓ Service systemd créé"

# ── 8. Config nginx ───────────────────────────────────────────────────────────
cat << 'EOF'

=== CONFIG NGINX À AJOUTER dans /etc/nginx/sites-available/chiron ===

server {
    listen 80;
    server_name 46.224.227.209;  # ou ton domaine

    # Frontend Angular
    root /opt/chiron/frontend;
    index index.html;

    # SPA fallback
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API backend
    location /api/ {
        proxy_pass http://127.0.0.1:9090;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }
}

=== PUIS : ===
sudo ln -s /etc/nginx/sites-available/chiron /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx

EOF

echo ""
echo "=== Migration terminée ==="
echo ""
echo "PROCHAINES ÉTAPES :"
echo "1. Arrête l'ancienne appli (docker-compose down si tu utilisais Docker pour le JAR)"
echo "2. Démarre le backend : sudo systemctl start chiron-back"
echo "3. Vérifie les logs  : sudo journalctl -u chiron-back -f"
echo "4. Configure nginx   : voir bloc ci-dessus"
echo "5. Le CI GitHub déploiera ensuite dans $DEPLOY_DIR automatiquement"
