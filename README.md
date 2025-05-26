<p align="center">
  <img src="screenshots/logoClear.png" alt="Logo" width="433" height="333">
</p>


# UltrasonicFSK

> Android Proof-of-Concept to transmit binary data between two phones via **ultrasound**, without Bluetooth, Wi-Fi, or pairing.

---

## Overview

This Android POC demonstrates binary data transmission via ultrasound between two phones using basic binary FSK modulation, without any prior synchronization or pairing. The project handles environmental acoustic echoes.

UltrasonicFSK converts a text message into **ultrasonic** signals using simple Frequency Shift Keying (FSK):

- **bit 0** → 18,500 Hz  
- **bit 1** → 18,700 Hz  
- **bit duration** → 100 ms  

The receiving phone uses FFT (Fast Fourier Transform) to detect dominant frequencies in real-time and reconstruct the original message.
Performance: 2-FSK (10 bits/s), upgradable to 4-FSK, 8-FSK, ... N-FSK (within 13–20 kHz range) + possible multiplexing.

---

## Features

### Transmitter

- Converts ASCII text to binary
- FSK encoding: each bit → frequency (0 or 1)
- Playback using AudioTrack: each frequency is played for 100 ms per bit
- Optional: preamble insertion (e.g., 10101010) to aid message start detection
- Optional: checksum (e.g., XOR) for integrity verification

### Receiver

- Continuous capture with AudioRecord
- Sliding FFT analysis (100 ms window, adjustable stride)
- Detects the first significant dominant frequency (not necessarily the strongest – crucial with echoes)
- Filters unexpected frequencies
- Ignores secondary peaks (echoes/noise)
- Rebuilds binary frame
- Converts binary to ASCII text
- Displays the message in real-time using Jetpack Compose UI

### Echo Handling

Ultrasounds can reflect off walls/ceilings, producing delayed signal copies.

Implemented solutions:

- Fixed window: 100 ms per bit
- Extract first significant spectral peak, not the strongest
- Optional silence gap (e.g., 20 ms between bits) to let echoes decay
- Minimum energy threshold to filter noise

### Simplified Transmission Protocol

- Preamble (e.g., 10101010) – marks start of message
- Message encoded (ASCII → binary)
- Checksum (simple XOR)
- Frequency transmission, 100 ms per bit
- Reception via FFT → frequency → bit → text
- Long-range audible mode at 3000 Hz (8–10 meters)
- Short-range ultrasonic mode at 18,500 Hz (1–3 meters)
- Performance: 2-FSK 10 bits/s (upgradable to 4-FSK, 8-FSK, etc.)
- Micro-protocol designed to maximize data efficiency within bandwidth limits

Frame structure example:

```
[STX] [TYPE] [PAYLOAD] [CHECKSUM] [ETX]
```

- Start marker (STX) / Message type / Compressed payload / End marker (ETX)
- Optionally uses a dictionary to encode frequent words or commands by index
- Greatly reduces data size, improves latency and reliability
- Simple, error-tolerant protocol for noisy environments

---



## Tech Stack

| Composant         | Implementation                |
|-------------------|-------------------------------|
| Audio Output      | `AudioTrack` (PCM 44.1 kHz)   |
| Audio Input       | `AudioRecord`                 |
| Traitement        | FFT custom (maison)           |
| UI                | Jetpack Compose               |
| Architecture      | MVVM (ViewModel + StateFlow)  |



---



## Extras

- Start/Stop bits for framing
- Simple error correction (redundancy)
- Real-time audio spectrum visualizer

---




## Badges

![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blue?logo=kotlin)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-UI-orange?logo=android)
![MVVM](https://img.shields.io/badge/Architecture-MVVM-green)
![Audio](https://img.shields.io/badge/AudioTrack%2FAudioRecord-PCM%2044.1kHz-yellow)
![Custom FFT](https://img.shields.io/badge/FFT-Custom-lightgrey)






