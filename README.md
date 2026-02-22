# Scor REMI (Remi Scorer)

Aplicație Android offline pentru ținerea scorului la Remi, pentru 2-4 jucători.

## Funcționalități implementate
- Acasă: Meci nou, Continuă meciul, Istoric, Statistici.
- Configurare meci: nume (manual sau random), număr jucători, nume jucători, scor țintă.
- Sesiune joc: clasament live, adăugare rundă, butoane rapide, istoric runde, anulare ultima rundă, informații meci.
- Detectare câștigător când un jucător depășește pragul + dialog de finalizare.
- Istoric meciuri cu detalii, câștigător și ștergere.
- Statistici agregate pe jucător.
- Persistență locală în `SharedPreferences` (JSON), inclusiv meci activ.

## Cum construiești aplicația (APK)

### Varianta recomandată (Android Studio)
1. Instalează Android Studio (ultimul stabil).
2. Deschide proiectul (`/workspace/remi` sau clone local).
3. Lasă Android Studio să instaleze automat Android SDK / Build-Tools cerute.
4. Din meniu: **Build > Build APK(s)**.
5. APK debug rezultat: `app/build/outputs/apk/debug/app-debug.apk`.

### Varianta CLI (terminal)
1. Asigură-te că ai JDK 17 și Android SDK instalate.
2. Setează variabilele de mediu:
   - `ANDROID_HOME` sau `ANDROID_SDK_ROOT`
   - `JAVA_HOME` (JDK 17)
3. Rulează:

```bash
./gradlew assembleDebug
```

APK-ul rezultat:
`app/build/outputs/apk/debug/app-debug.apk`
