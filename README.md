# Galéria – egyszerű, reklámmentes galéria Androidra

Natív Android alkalmazás (Kotlin + Jetpack Compose, Material 3).
**Nincs benne internet-engedély**, így reklámot sem tud megjeleníteni, és adatot sem küld ki.

## Funkciók
- **Albumok a nyitóképernyőn:** Fényképek, Videók, Kedvencek, saját albumok, valamint a telefon mappái (Kamera, Képernyőképek, Letöltések, WhatsApp stb.).
- **Új album létrehozása**, átnevezése, törlése. A saját albumok *virtuálisak*: a fájlok a helyükön maradnak, nem foglalnak dupla helyet, és egy kép több albumban is lehet.
- **Dátum szerinti csoportosítás** albumon belül („Ma”, „Tegnap”, „Október 12., hétfő”).
- **Kijelölés:** hosszú nyomás egy képen, utána koppintással továbbiak; a dátumfejléc hosszú nyomásával egy egész nap; felül „összes kijelölése”.
- **Műveletek a kijelöltekre:** megosztás (e-mail melléklet, Google Drive és bármely más app), kedvencekhez adás, albumba rendezés, törlés, átnevezés, részletek, eltávolítás az albumból.
- **Megjelenítő:** lapozás, csippentéses és dupla koppintásos nagyítás, beépített videólejátszó, koppintásra teljes képernyő.
- **Lomtár:** a törölt elemek 30 napig visszaállíthatók (a rendszer lomtárát használja).
- Rácsméret állítása (3/4/5 oszlop), sötét mód, Android 12+ esetén a háttérkép színei.
- Android 14 „csak kiválasztott fotók” hozzáférés támogatása.

## APK készítése GitHubon (számítógépes fejlesztőeszköz nélkül)

### 1. Feltöltés
1. Hozz létre egy új repót a github.com-on (jobb felül **+ → New repository**). Javasolt a **Private** beállítás.
2. Az üres repó oldalán kattints az **uploading an existing file** linkre, és húzd be a kicsomagolt `Galeria` mappa **tartalmát** (nem magát a mappát).
   Fontos, hogy a rejtett `.github` mappa is felkerüljön. macOS-en a Finderben a `Cmd + Shift + .` mutatja a rejtett mappákat.
3. **Commit changes**.

A feltöltés után az **Actions** fülön automatikusan elindul az „APK készítése” folyamat (kb. 5–8 perc).

### 2. Az APK letöltése
- **Actions** fül → a legutóbbi zöld pipás futás → lent az **Artifacts** résznél `Galeria-apk` (ZIP, benne a `Galeria.apk`).
- Kényelmesebb: hozz létre egy **kiadást**. Jobb oldalt **Releases → Create a new release**, címkének írd be pl. `v1.0`, majd **Publish**.
  Pár perc múlva a kiadás oldalán közvetlenül ott lesz a `Galeria.apk`, ami telefonról is letölthető.

### 3. Telepítés a telefonra
Nyisd meg a letöltött APK-t, és engedélyezd a telepítést ismeretlen forrásból, amikor a telefon rákérdez.

### 4. Állandó aláíró kulcs (erősen ajánlott, egyszer kell)
Az Android csak akkor engedi frissíteni az appot, ha az új verzió ugyanazzal a kulccsal van aláírva.
Kulcs nélkül minden build más kulcsot kap, ezért frissítés előtt törölni kellene az appot, és ezzel elvesznének az albumaid és kedvenceid.

1. **Actions** fül → bal oldalt **Aláíró kulcs létrehozása (csak egyszer kell)** → **Run workflow**.
2. A futás végén töltsd le az `alairo-kulcs` artifactot. A benne lévő `secrets.txt` leírja a teendőket.
3. **Settings → Secrets and variables → Actions → New repository secret**, és vedd fel a 4 értéket:
   `KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
4. A `release.jks` fájlt tedd el biztonságos helyre, az artifact 1 nap után magától törlődik.

Ezután minden új build ugyanazzal a kulccsal készül, és felülírja a telefonon lévő verziót. A verziószám automatikusan nő.

### Módosítás után
Ha bármelyik fájlt szerkeszted a GitHubon (ceruza ikon → Commit), automatikusan új APK készül.
Ha egy build pirosra fut, a hibaüzenetet a futás „APK fordítása” lépésében találod.

## Fordítás Android Studióval (opcionális)
*File → Open* → a `Galeria` mappa → *Run ▶*. Minimum: **Android 11**.

## Tipp
A menüben a „Kevesebb megerősítő ablak” (Android 12+) megadja a rendszer *Médiakezelés* engedélyét,
utána törléskor és átnevezéskor nem kell minden alkalommal jóváhagyni a műveletet.

## Szerkezet
```
app/src/main/java/hu/egyszeru/galeria/
├── MainActivity.kt          navigáció, engedélyek, rendszer-párbeszédek
├── GalleryApp.kt            képbetöltő beállítása
├── data/
│   ├── MediaItem.kt         adatmodellek
│   ├── MediaRepository.kt   MediaStore: lekérdezés, lomtár, törlés, átnevezés
│   ├── AlbumStore.kt        saját albumok, kedvencek, beállítások
│   └── Thumbnails.kt        gyors rendszer-bélyegképek
├── ui/
│   ├── GalleryViewModel.kt  állapot és műveletek
│   ├── AlbumsScreen.kt      nyitóképernyő
│   ├── AlbumScreen.kt       album rács, kijelölés
│   ├── ViewerScreen.kt      teljes képernyős nézet, zoom, videó
│   ├── Components.kt        közös elemek, párbeszédablakok
│   └── PermissionScreen.kt
└── util/                    formázás, megosztás, engedélyek
```
