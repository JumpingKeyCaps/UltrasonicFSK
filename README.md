# UltrasonicFSK 

> Proof-of-concept Android pour transmettre des données binaires entre deux téléphones via des **ultrasons**, sans Bluetooth, Wi-Fi ou appairage.

---

##  Présentation

POC Android permettant la transmission de données binaires via ultrasons entre deux téléphones sans synchronisation ni appairage, à l’aide d’une modulation FSK binaire.
Le projet gére les échos acoustiques naturellement présents dans l’environnement.

UltrasonicFSK démontre la transmission d’un message texte en le modulant en **ultrasons** à l’aide d’une modulation FSK (Frequency Shift Keying) simple :

- **bit 0** → 18 500 Hz  
- **bit 1** → 18 700 Hz  
- **durée d’un bit** → 100 ms  

Le téléphone récepteur détecte les fréquences dominantes en temps réel via FFT pour reconstruire le message.


---

##  Fonctionnalités

### Émetteur

- Conversion d’un message texte en ASCII → binaire
- Encodage FSK : chaque bit → fréquence (0 ou 1)
- Émission via AudioTrack : une fréquence jouée 100 ms par bit
- Optionnel : insertion d’un préambule (ex: 10101010) pour aider à la détection de début de message
- Optionnel : ajout d’un checksum (ex: simple XOR) pour vérification d’intégrité

### Récepteur

- Capture en continu via AudioRecord
- Analyse par FFT glissante (fenêtre de 100 ms, stride ajustable)
- Détection de la première fréquence dominante significative, pas nécessairement la plus forte (important en présence d’échos)
- Filtrage des fréquences non attendues
- Ignorer les pics secondaires (échos ou bruit)
- Reconstruction de la trame binaire
- Conversion de la trame en ASCII
- Affichage en temps réel dans l’UI Jetpack Compose


### Gestion des échos
Les signaux ultrasonores peuvent rebondir sur les murs/plafonds, créant des copies retardées du signal :

Solutions mises en place :

- Fenêtre temporelle fixe : 100 ms par bit
- Extraction du 1er pic spectral significatif, et non du plus fort
- Gap de silence facultatif (ex : 20 ms entre chaque bit) pour laisser les échos mourir avant le bit suivant
- Seuil minimum d’énergie pour éviter le bruit


### Protocole de transmission simplifié
- Préambule (ex: 10101010) – pour indiquer le début du message
- Message encodé (en ASCII → binaire)
- Checksum (XOR simple de tous les bits)
- Transmission par fréquence, 100 ms/bit
- Réception : FFT, détection du premier pic, reconstruction des bits
- Vérification via le checksum



---

## Stack technique


- Audio Output ->	AudioTrack (PCM 44.1kHz)
- Audio Input ->	AudioRecord
- FFT ->	JTransforms ou implémentation maison
- UI ->	Jetpack Compose
- Archi ->	MVVM (ViewModel + State)

---


###  Bonus 

- Bit de start / stop pour encadrer la trame
- Correction simple d’erreurs (redondance)
- Affichage du spectre audio en temps réel


---







