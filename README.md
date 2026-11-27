# Andromate

Suite d’outils modulaires pour Android, en Material You. Chaque module s’active à part
depuis le hub.

| Module | Ce qu’il fait |
|---|---|
| **Une Main +** | Poignées sur les bords de l’écran : gestes (retour, accueil, notifications, capture…) et menu d’outils avec bascules (Wi-Fi, Bluetooth, données, point d’accès, mode avion, Ne pas déranger, son), applications en plein écran ou en fenêtre, zone audio |
| **Caféine** | Garde l’écran allumé, depuis une tuile des réglages rapides |
| **Modes et routines** | Automatismes « si… alors » : Wi-Fi, Bluetooth, charge, niveau de batterie, ouverture d’une appli → volume, sonnerie, Ne pas déranger, luminosité, rotation, torche, son, notification… ; modes activables à la main |
| **Presse-papier** | Historique des copies, accessible depuis le menu d’outils |
| **DeepBluetooth** | Popup à la connexion d’un appareil Bluetooth, avec sa batterie |
| **Raccourcis** | Icônes d’écran d’accueil vers une page précise des paramètres (Batterie, Écran…) |
| **Touche Essential** | Reprogramme la touche Essential des Nothing Phone (3a) et suivants (appui court, long, double) |
| **Compatibilité** | Vérifie ce qui fonctionne sur le téléphone et produit un rapport |

L’interface est en français.

## Autorisations

Andromate ne se connecte à aucun serveur : il n’a pas l’autorisation Internet.

- **Service d’accessibilité** : poignées et gestes, menu d’outils, bascules par les tuiles du
  volet de réglages (Android interdit aux applis d’activer le Wi-Fi elles-mêmes), fenêtres réduites
  par l’écran Récents, captures d’écran, lecture de la touche Essential. Il ne lit ni ne transmet
  le contenu des applications.
- **Accès aux notifications** : titre et source de la musique en cours dans la zone audio.
- **Localisation** : Android ne donne le nom du réseau Wi-Fi qu’avec elle (routines limitées à un
  réseau). Elle n’est jamais enregistrée.
- **Appareils à proximité** : nom et batterie des appareils Bluetooth.
- **Accès « Ne pas déranger »**, **modifier les paramètres système** : actions des routines.

### Touche Essential (Nothing)

Android ne permet pas à une appli de désactiver Essential Space. Pour qu’Andromate soit seul sur
la touche, lancez une fois depuis un PC (débogage USB activé) :

```
adb shell pm disable-user --user 0 com.nothing.ntessentialrecorder
```

Pour retrouver Essential Space :

```
adb shell pm enable --user 0 com.nothing.ntessentialrecorder
```

## Compiler

JDK 21 et SDK Android (API 36).

```
./gradlew assembleRelease
```

L’APK release n’est signé que si une clé est fournie par les variables d’environnement
`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS` et `KEY_PASSWORD`.

## Licence

[GPL-3.0](LICENSE)
