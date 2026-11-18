# Thème d'accueil « voiture »

`config.json` est un exemple de configuration pour le lanceur : dock de 5 applis, PS5 en un clic, bouton ampoule
dans la barre du haut, applis inutiles masquées.

La tuile PS5 pointe sur l'entrée HDMI de la console par son appareil CEC
(`input:com.mediatek.tvinput/.hdmi.HDMIInputService/HDMI300004`) et pas sur la prise brute, parce que c'est
l'appareil CEC qui réveille la console. Le numéro est celui de ma TV, à adapter à la tienne.

Le fond d'écran n'est pas fourni. Mets ton image dans ce dossier sous le nom `wallpaper.jpg`.
