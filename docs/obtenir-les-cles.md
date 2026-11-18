# Récupérer les clés locales de tes lumières

Pour parler à une lumière Tuya sur le réseau local, il faut trois choses : son adresse IP, son identifiant
(`device_id`) et sa clé locale (`local_key`). La clé donne le contrôle de la lumière. Ne la mets jamais dans un
dépôt, une capture d'écran ou un message.

## Avec tinytuya

1. Crée un compte sur https://iot.tuya.com et un projet Cloud lié à ton appli Tuya ou Smart Life
   (scanne le QR code de l'appli pour lier ton compte).
2. Installe l'outil et lance l'assistant :

```bash
pip install tinytuya
python -m tinytuya wizard
```

3. L'assistant te demande l'identifiant et le secret du projet Cloud, puis écrit `devices.json` avec les clés.
4. Pour les adresses IP, lance `python -m tinytuya scan` et note celle de chaque lumière. Il vaut mieux leur donner
   une adresse fixe dans la box.
5. Recopie le tout dans `lights.json`, sur le modèle de `../exemples/lights.exemple.json`.

Les ampoules récentes parlent en version 3.5 et les plus anciennes en 3.3. Si une lumière ne répond pas, essaie
l'autre version.
