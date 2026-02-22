# Scor REMI (Remi Scorer)

Aplicație Android offline pentru ținerea scorului la Remi, pentru 2-4 jucători.

## Funcționalități implementate
- Acasă: Meci nou, Continuă meciul, Istoric, Statistici.
- Configurare meci: nume (manual sau random), număr jucători, nume jucători, scor țintă.
- Sesiune joc: clasament live, adăugare rundă, butoane rapide, istoric runde, anulare ultima rundă.
- Detectare câștigător când un jucător depășește pragul + dialog de finalizare.
- Istoric meciuri cu detalii și ștergere.
- Statistici agregate pe jucător.
- Persistență locală în `SharedPreferences` (JSON), inclusiv meci activ.

## Build APK (local)
1. Instalează Android SDK + platforma `android-35` și build-tools.
2. Rulează:

```bash
./gradlew assembleDebug
```

APK-ul rezultat:
`app/build/outputs/apk/debug/app-debug.apk`
