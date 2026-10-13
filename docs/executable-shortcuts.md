# Raccourcis d’exécutables Steam

Dans les options d’un jeu Steam installé possédant déjà un conteneur, ouvrir
**Créer un raccourci d’exécutable**. Sélectionner un `.exe` dans le lecteur du
jeu, saisir le nom, choisir éventuellement une pochette et enregistrer.
Chaque entrée apparaît avec son nom et sa pochette dans ce gestionnaire. Elle
dispose des actions Jouer, Créer un raccourci Android, Modifier, Copier le lien
et Supprimer. Les entrées apparaissent aussi avec leur propre identité et pochette
dans la bibliothèque Steam et dans Tous. Elles peuvent être recherchées et
ajoutées aux favoris. Leur fiche ouvre le gestionnaire en plaçant l’entrée choisie
en premier ; les paramètres Wine et la gestion de l’installation restent sur le
jeu parent.

Exemples à sélectionner pour l’installation Kingdom Hearts fournie :

| Nom de l’entrée | Exécutable |
| --- | --- |
| Kingdom Hearts Final Mix | `KINGDOM HEARTS FINAL MIX.exe` |
| Kingdom Hearts II Final Mix | `KINGDOM HEARTS II FINAL MIX.exe` |
| Birth by Sleep Final Mix | `KINGDOM HEARTS Birth by Sleep FINAL MIX.exe` |
| Re:Chain of Memories | `KINGDOM HEARTS Re_Chain of Memories.exe` |
| Theater | `KINGDOM HEARTS Theater.exe` |

Les noms et chemins restent libres ; aucune règle propre à Kingdom Hearts n’est
ajoutée au lancement.
L’interface ajoutée est traduite en français et en anglais. Les autres langues
utilisent les textes anglais par défaut.

## Architecture et conservation des données

- `ExecutableShortcut` possède un UUID, un `parentAppId` Steam, le `containerId`
  existant, un chemin relatif, un nom et une pochette indépendante.
- `ExecutableShortcutStore` stocke uniquement ces métadonnées dans les préférences
  privées. Les pochettes choisies via le sélecteur Android sont copiées dans
  `files/executable_shortcut_art/`. Aucune copie de l’installation n’est créée.
- Les intents existants (`app_id`, `game_source`) restent compatibles. Une entrée
  utilise `executable_shortcut_id` ou `gamenative://run?shortcut=<UUID>`.
  L’application résout l’UUID à partir de ses propres métadonnées ; elle n’accepte
  pas un chemin d’exécutable fourni par le lanceur externe.
- L’UUID traverse les lancements à froid, les événements à chaud et la file
  différée liée à la connexion Steam. Le jeu parent reste l’identité utilisée
  pour Steam, le conteneur, le suivi de session et les sauvegardes cloud.
- Le chemin est transmis à `XServerScreen` pour cette session. Le getter du
  conteneur lit la cible de session ; sa sauvegarde sérialise toujours le chemin
  parent. Les affectations du chemin pendant la session ne changent pas le parent.
  La cible est retirée à la fermeture de l’écran et remise à zéro lors du prochain
  lancement normal.
- L’existence du jeu, du conteneur et du fichier est vérifiée au lancement.
  Les chemins absolus, traversées et liens symboliques sortant de l’installation
  sont refusés. La sélection réutilise le navigateur d’exécutables du lecteur A:.
  Cette version cible les exécutables de l’installation Steam, pas les utilitaires
  situés ailleurs dans le préfixe Wine.
- La création, la modification et la suppression des entrées ne touchent ni les
  fichiers ni les sauvegardes des jeux. Le lancement réutilise le pipeline normal
  de GameNative, y compris ses opérations Steam/DRM et la synchronisation des
  sauvegardes selon la configuration existante.

Le mode Steam avec interface graphique démarre normalement Steam avec
`-applaunch`, ce qui laisse Steam sélectionner son exécutable. Il est donc refusé
pour ces entrées. Le lancement direct, Steam Bionic et Steam sans interface
utilisent la cible sélectionnée. Un jeu déjà en cours reste la session active :
il faut le quitter avant de lancer un autre exécutable du même jeu.

## Cocoon et iiSU

**Cocoon :** son [annonce officielle des raccourcis Android](https://cocoon-shell.com/news/cocoon-2-1-beta/)
indique que les raccourcis GameNative peuvent être ajoutés lorsque Cocoon est le
lanceur par défaut. Chaque nouvelle entrée utilise un ID Android
`executable_<UUID>` et un intent distinct. Cette approche devrait donc conserver
les exécutables indépendants si Cocoon conserve l’intent complet. C’est une
compatibilité attendue d’après son API documentée, non un test sur une Ayn Thor.
Les [tags Steam de Cocoon](https://www.cocoon-shell.com/wiki/tags/) identifient
seulement le Steam App ID et ne distinguent pas les entrées.

**iiSU :** sa [documentation GameNative](https://iisu.network/wiki/adding-steam-games-to-iisu)
décrit des fichiers `.steam` contenant uniquement un Steam App ID. Plusieurs
fichiers avec cet ID lanceront le même jeu parent. Il faudrait une intégration
iiSU transmettant `executable_shortcut_id` ou le lien avec UUID pour distinguer
les entrées. Le lien peut être copié depuis GameNative ; cette version ne prétend
pas que le profil GameNative fourni par iiSU prend déjà en charge ce paramètre.
L’export `.steam` existant conserve son format pour les jeux ordinaires.

## Signature et application officielle

Android exige une signature compatible pour installer une mise à jour portant
le même identifiant d’application, conformément à la
[documentation de signature Android](https://developer.android.com/studio/publish/app-signing).
Un APK compilé et signé avec une clé personnelle/debug ne peut donc pas remplacer
l’application officielle tout en conservant ses données privées, sauf si sa
signature est compatible avec celle de l’installation existante.

Un autre `applicationId` permet de coinstaller une version personnelle, mais lui
donne un autre sandbox : elle n’accède pas aux préférences, préfixes Wine ou jeux
stockés dans le stockage privé de l’application officielle. Voir le
[sandbox Android](https://source.android.com/docs/security/app-sandbox).
Le code de cette fonctionnalité ne contourne pas cette isolation et ne nécessite
pas de root. Pour utiliser les données privées officielles sans migration, il
faut une mise à jour officielle compatible. Pour une version coinstallée,
utiliser les mécanismes d’export/import pris en charge par l’application et des
fichiers dans un stockage accessible. Adapter aussi les chemins et intents du
projet liés au nom de package avant de construire une variante coinstallée.
Ne pas désinstaller l’application officielle
pour tenter de contourner la signature : cela peut supprimer ses données privées.

## Vérification

Tests ajoutés : identités indépendantes, aller-retour JSON, validation des chemins,
lecture sans modification des fichiers, intents avec UUID et compatibilité des
anciens intents, absence de sauvegarde de la cible de session.

```powershell
.\gradlew.bat :app:compileLegacyDebugKotlin :app:testLegacyDebugUnitTest --console=plain
.\gradlew.bat :app:assembleLegacyDebug --console=plain
```

Sur l’appareil : créer deux entrées KH avec des `.exe` différents, les épingler
dans Cocoon puis lancer chacune à froid et à chaud après fermeture de la session
précédente. Vérifier que le lancement normal du parent conserve son exécutable,
que les pochettes persistent après redémarrage, et qu’une entrée supprimée ou
dont l’exécutable manque ne lance pas le parent par erreur.
