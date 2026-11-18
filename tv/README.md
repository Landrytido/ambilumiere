# Lumières, version TV

L'appli (`com.gathin.tvlights`) qui pilote les lumières depuis la TV et leur envoie les couleurs de la pochette.

## Ce qu'elle fait

- 4 cartes (une par lumière) avec interrupteur, luminosité, couleur ou blanc.
- Des pilules : Tout allumer, Tout éteindre, Soirée, Cinéma, Synchroniser ou Désynchroniser.
- La synchro avec la pochette, réglable par lumière, et un réglage Doux, Normal ou Vif pour la force de la synchro.
- Un service discret en arrière-plan qui garde les lumières connectées.

Rien ne sort sur internet : tout passe en TCP sur le port 6668 du réseau local.

## Les couleurs de la pochette

Le lanceur envoie un message explicite à cette appli à chaque changement de pochette.

| | |
|---|---|
| Composant | `com.gathin.tvlights/.PaletteReceiver` |
| Action | `com.gathin.tvlights.PALETTE` |
| Extra | texte `colors` : `#RRGGBB,#RRGGBB,...`, ou `off` quand la musique s'arrête |

Pour essayer à la main depuis un PC :

```bash
adb shell am broadcast -n com.gathin.tvlights/.PaletteReceiver -a com.gathin.tvlights.PALETTE --es colors "#B36038,#20ADC6,#B33E47"
adb shell am broadcast -n com.gathin.tvlights/.PaletteReceiver -a com.gathin.tvlights.PALETTE --es colors off
```

Intensité : Doux 55 %, Normal 85 %, Vif 100 % de la luminosité de la couleur. Les valeurs sont dans `Intensity.java`.

## Configuration

Les adresses et les clés ne sont pas dans l'appli. Ouvre l'appli une fois pour créer son dossier, puis :

```bash
adb push lights.json /sdcard/Android/data/com.gathin.tvlights/files/lights.json
```

Le format est dans `../exemples/lights.exemple.json`. `kind` vaut `strip` pour le ruban à 10 zones, ou `bulb` pour
une ampoule. `version` vaut `3.5` ou `3.3` selon l'appareil. Le fichier est relu quand il change.

## Compiler

```bash
./gradlew testReleaseUnitTest assembleRelease
```

L'APK est dans `app/build/outputs/apk/release/`. Il est signé avec la clé de debug : il est fait pour être installé
en ADB, pas publié sur le Play Store.
