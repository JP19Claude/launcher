# Hearth Launcher

Ein schlanker Android-Launcher in Kotlin und Jetpack Compose, mit iOS-Ideen und Liquid Glass –
plus **Glimmer**, die Insel um die Frontkamera, und dem **Kontrollzentrum** als eigene Apps.


## Claude im System

Claude ist in Hearth eingebaut und erledigt Dinge direkt auf dem Handy, statt nur zu antworten.

- Kostenlos, ohne Schlüssel und offline: Hearth versteht viele Befehle selbst – Licht, Ton,
  Helligkeit, Lautstärke, Wecker („halb 7“, „7 Uhr abends“), Timer, Anrufe und Nachrichten an
  Kontakte („Ruf Mama an“, „Schreib Tom per WhatsApp: bin gleich da“), Navigation, Websuche,
  Wetter, Rechnen („17 mal 23“, „15 % von 80“), Apps, Kamera, Einstellungen, Akku, Uhrzeit,
  mehrere Befehle mit „und“. Alles andere geht an die Claude-App (dein Claude-Konto, keine
  API-Kosten).
- Andere KI-Anbieter, oft mit kostenlosem Kontingent: NVIDIA (build.nvidia.com), Groq,
  Google Gemini, OpenRouter (Gratis-Modelle mit „:free“), Mistral, Cerebras oder ein eigener
  OpenAI-kompatibler Dienst (https). Einstellungen → Claude-Assistent → KI-Anbieter, Schlüssel
  eintragen, Modellname änderbar (es muss Werkzeuge/Function Calling können) oder aus
  „Verfügbare Modelle laden“ antippen. Bei NVIDIA wählt Hearth das Modell selbst; gibt es ein
  Modell nicht mehr (z. B. Fehler 410 „end of life“), nimmt Hearth automatisch das nächste
  passende aus der Liste des Anbieters. Jeder Anbieter behält seinen eigenen Schlüssel.
- Mit eigenem API-Schlüssel (getrennt vom Claude-Abo abgerechnet) erledigt Claude auch
  Komplexes mit Werkzeugen. Sparmodus (Standard): was Hearth selbst versteht, kostet nichts;
  Standardmodell Haiku 4.5, wenig Denkaufwand.

- Was Claude kann:
  - Taschenlampe, Helligkeit, Lautstärke, Ton, Vibration, lautlos, Nicht stören, Auto-Drehen
  - Wecker und Timer, ohne Umweg über die Uhr-App
  - Termine (fertig ausgefüllt), Kontakte suchen, Anrufe, SMS, WhatsApp und E-Mails vorbereiten
  - Navigation, Karte, Websuche, Webseiten
  - Apps und ihre Einstellungen öffnen, jede Systemeinstellung öffnen
  - Musik steuern, Bildschirmfoto, Sperren, Zurück, Home, letzte Apps, geteilter Bildschirm
  - Zwischenablage, Zustand des Handys (Akku, Wecker, WLAN …), das Design von Hearth
  - mehrere Dinge in einem Satz
- So erreichst du Claude:
  - „Frag Claude“, die Suche, Now Brief und das Kontrollzentrum
  - langes Drücken auf Home, mit Hearth als digitalem Assistenten
  - die Kachel „Claude“ in den Schnelleinstellungen
  - Doppeltippen auf Glimmer
- Spracheingabe; Antworten werden vorgelesen, wenn du per Sprache fragst.
- Anrufe, Nachrichten und Termine bestätigst du immer selbst.
- Fragen nimmt Claude nur von den Hearth-Apps an (geschützt über die Signatur).
- Einstellungen → Claude-Assistent: Schlüssel, Modell (Haiku 4.5, Sonnet 5.5, Opus 5.5), Sparmodus, Vorlesen.

## Galaxy × Claude

Design-Vorlage „Galaxy × Claude“ (Einstellungen → Design-Vorlage): der Launcher im Stil von One UI
mit Liquid Glass, und Claude dort, wo bei Samsung Galaxy AI sitzt.

- One UI 9 in Liquid Glass: Samsungs Quick Panel als Kontrollzentrum (WLAN und Bluetooth als
  große Knöpfe, runde Schalter mit Namen, breite Regler, Smart View und Medienausgabe,
  Player), das App-Popup wie auf dem Galaxy (Shortcuts als Liste, Aktionen als Reihe runder
  Symbole mit Namen), Einstellungen mit One-UI-Titel und blauen Abschnittsnamen, runde
  Seitenpunkte
- App-Übersicht: „Vorgeschlagene Apps“ oben, ⋮-Menü mit Sortieren (A–Z, Neueste zuerst,
  Meistgenutzt), Vorschläge an/aus und Einstellungen
- One UI: Squircle-Icons, die große, fette One-UI-Uhr mit Datum und Akku, Galaxy-Blau,
  kühl getöntes Glas, einfaches Wischen zwischen den Seiten, links Mitteilungen und rechts
  Schnelleinstellungen; im Kontrollzentrum runde Schalter und breite Regler wie bei One UI
- Now Brief von Claude auf dem Startbildschirm: ein Satz zur Tageszeit, nächster Wecker und
  Akku auf einen Blick, Vorschläge zum Antippen („Plane meinen Tag“, „Schreib für mich“,
  „Übersetzen“ …), die Claude direkt damit öffnen
- Such-Pille „Frag Claude oder suche“: in der Suche steht Claude ganz oben, „Los“ ohne passende
  App fragt Claude, vor dem Tippen gibt es Claude-Vorschläge
- „Claude fragen“ im Menü jeder App (lange drücken): öffnet die Claude-App mit der Frage nach
  Tipps und versteckten Funktionen der App
- One-UI-App-Übersicht statt der App-Mediathek: auf dem Startbildschirm nach oben wischen, alle
  Apps A–Z in Seiten mit Punkten über dem verschwommenen Hintergrund, oben die Suche (mit
  Claude), nach unten wischen schließt
- Bearbeitungsmodus wie bei One UI: lange auf eine freie Stelle drücken, die Seiten treten zurück,
  unten erscheinen „Hintergrund und Stil“, „Widgets“, „Apps auswählen“ und „Einstellungen“;
  ⋮ in der App-Übersicht öffnet die Einstellungen; keine Such-Pille auf dem Startbildschirm
- Einzeln ein- und ausschaltbar: „Claude im System (Galaxy × Claude)“

## Drei Apps

- **Hearth** (`Hearth.apk`, `dev.hearth.launcher`): der Launcher mit Homescreen, Dock,
  App-Mediathek, Suche, Widgets und seinem Kontrollzentrum auf dem Homescreen.
- **Glimmer** (`Glimmer.apk`, `dev.hearth.glimmer`): die Insel (Dynamic Island) mit
  Live-Aktivitäten in jeder App, auf dem Sperrbildschirm, im Always-On-Display und mit
  Face-ID-Moment. Braucht ihre Bedienungshilfe und den Benachrichtigungszugriff.
- **Kontrollzentrum** (`Kontrollzentrum.apk`, `dev.hearth.controls`): das Glas-Kontrollzentrum
  über jeder App, auf Wunsch anstelle von One UIs. Braucht seine Bedienungshilfe.
- Glimmer und Kontrollzentrum laufen mit jedem Launcher. Zusammen mit Hearth: geschlossene
  Apps fliegen in Glimmer, Doppeltippen zum Sperren und die System-Knöpfe laufen über sie,
  Hearth reicht das Aussehen seines Kontrollzentrums weiter. Die Apps reden über eine
  Schnittstelle, die nur Apps mit derselben Signatur nutzen dürfen.

Download: [Hearth.apk](https://github.com/Vinted7777/launcher/releases/latest/download/Hearth.apk) ·
[Glimmer.apk](https://github.com/Vinted7777/launcher/releases/latest/download/Glimmer.apk) ·
[Kontrollzentrum.apk](https://github.com/Vinted7777/launcher/releases/latest/download/Kontrollzentrum.apk)

## Funktionen

- Look „ColorOS × Claude“ (Standard): ColorOS-Uhr, abgerundete Icons, Claude-Farben
  (Terrakotta, Elfenbein, Schiefer), Serifen-Akzente, animierter Funke und
  eine „Frag Claude“-Glaskarte; weitere Vorlagen: iOS Liquid Glass, Hearth Klassik
- Eigenes Kontrollzentrum aus Liquid Glass (nach unten wischen; links: Mitteilungen):
  - Helligkeit und Lautstärke als große Glas-Regler
  - Taschenlampe, Nicht stören, Vibration, Drehen, Auto-Helligkeit, Standort
  - WLAN, Mobile Daten, Bluetooth, Flugmodus (öffnen die System-Panels)
  - Medien-Tasten, Kamera, Wecker, Rechner, Claude
  - Helligkeit/Drehung brauchen „Systemeinstellungen ändern“, Nicht stören den
    Zugriff auf „Bitte nicht stören“
- „Hearth-Kontrollzentrum verwenden“ (Gesten & Kontrollzentrum): aus = überall das normale
  Kontrollzentrum von One UI (auch beim Herunterwischen auf dem Homescreen), Glimmer und
  der Rest laufen weiter
- Kontrollzentrum in allen Apps (Bedienungshilfe „Hearth Kontrollzentrum“ einschalten):
  ein unsichtbarer Streifen über der Statusleiste fängt das Wischen
  nach unten ab und öffnet das Glas-Kontrollzentrum über der App (ab Android 12 mit
  echter Unschärfe dahinter). Links öffnet weiter die normale Mitteilungsleiste.
  Bereich einstellbar: rechte Hälfte, rechtes Drittel, ganze Breite. Ganz entfernen
  lässt sich das System-Kontrollzentrum nur mit Root.
- Suche: „Claude fragen“ öffnet Hearths Claude-Assistenten (ausgeschaltet: Claude-App oder claude.ai)
- App-Mediathek wie bei iOS (hinter der letzten Seite): Glas-Ordner nach Kategorie,
  „Vorschläge“ aus deinen meistgenutzten Apps, „Neu hinzugefügt“; die kleinen Icons
  in der Ecke öffnen den ganzen Ordner
- Kontrollzentrum zeigt, was gerade läuft: Cover, Titel, Interpret, Fortschritt,
  Glas in der Farbe des Covers (braucht einmal „Benachrichtigungszugriff“)
- „System-Kontrollzentrum ersetzen“: die ganze Statusleiste öffnet dann Hearths
  Kontrollzentrum (Mitteilungen per Knopf darin). Geht das Panel von One UI/ColorOS
  doch auf, erkennt Hearth es sofort an den Schnelleinstellungen im Fenster der
  System-Oberfläche, schließt es und zeigt seins (braucht den Bedienungshilfe-Dienst)
- Kontrollzentrum im Stil von ColorOS 17: große WLAN- und Mobil-Kacheln, Medien-Karte,
  runde Schalter mit leuchtender Kontur, hohe Regler für Helligkeit und Lautstärke.
  Es folgt beim Herunterziehen dem Finger, die Reihen klappen nacheinander auf,
  die Unschärfe dahinter wächst mit; loslassen öffnet es ganz oder schiebt es zurück
- Mitteilungszentrale wie auf dem iPhone (im Kontrollzentrum nach rechts wischen): große
  Uhr oben, Mitteilungen pro App gestapelt (antippen fächert den Stapel auf, „Weniger
  anzeigen“, ✕ löscht die App), nach links wischen zeigt „Optionen“ und „Löschen“ (ganz
  durchwischen löscht), gedrückt halten zeigt den ganzen Text mit den Knöpfen, ✕ oben →
  „Alle löschen“. Mit „System-Kontrollzentrum ersetzen“ öffnet die linke Hälfte der
  Statusleiste direkt die Mitteilungen, die rechte die Schalter
- Stil von Kontrollzentrum und Mitteilungen: iOS 27 (Standard: klares Glas, iOS-Farben –
  blau für WLAN/Bluetooth, grün für Mobilfunk, orange für Flugmodus –, Verbindungs-Block
  neben der Medien-Karte, „Nicht stören“ und „Smart View“ als breite Knöpfe neben den
  hohen Reglern, darunter runde Glas-Knöpfe ohne Beschriftung) oder ColorOS 17
- Glas und Design des Kontrollzentrums einstellbar: Glas (klar, milchig, dunkel,
  Akzentfarbe, wie der Launcher), Deckkraft, Glanz an den Kanten, Lichtbrechung,
  Weichzeichnen und Abdunkeln des Hintergrunds, Größe der Schalter, Rundung der Flächen.
  In Hearth liegt das weichgezeichnete Hintergrundbild unter dem Kontrollzentrum, über
  anderen Apps die Unschärfe des Systems (ohne sie dunkelt Hearth stärker ab)
- Kontrollzentrum anpassen (Einstellungen → „Kontrollzentrum: Aussehen“): Farbe der
  Schalter (iOS, Akzent, bunt wie ColorOS 16, weiß), Form (rund/abgerundet), hohe oder
  breite Regler, leuchtende Kontur, Beschriftungen, große Kacheln, Medien-Karte, Uhr,
  Schnellstart und wie stark der Hintergrund abgedunkelt wird
- „Vom Startbildschirm entfernen“: App verschwindet vom Homescreen, bleibt aber
  in App-Mediathek und Suche (einzeln im Menü oder mehrere per „Auswählen“)
- Ordner: App auf eine andere ziehen erstellt einen Glas-Ordner, weitere Apps darauf
  ziehen fügt sie hinzu; Ordner lange drücken zum Umbenennen oder Auflösen
- Benachrichtigungs-Badges auf den Icons (Zahl, Punkt oder aus)
- App-Shortcuts im Menü beim langen Drücken (z. B. „Neuer Chat“)
- Doppeltippen auf eine freie Stelle sperrt den Bildschirm
- Suche mit Vorschlägen, Taschenrechner und Sprung in Einstellungen („WLAN“, „Akku“ …)
- Kontrollzentrum wie One UI: Lautstärke gedrückt halten öffnet das Audio-Fenster
  (Ausgabegerät, alle Lautstärken, Ton/Vibration/Lautlos, 3D-Audio, Kopfhörer-App für
  die Geräuschunterdrückung); dazu Screenshot, Sperren, Ein/Aus-Menü, Energiesparen,
  Hotspot, NFC, Smart View, Dunkelmodus; scrollbar, nach oben ziehen schließt
- Weniger Ruckler: Hintergrund wird einmal vorab weichgezeichnet statt pro Glasfläche
  und Bild, Linsen-Shader nur auf großen Flächen, ruhigerer Bewegungssensor, Icon-Glanz
  ohne Dauer-Neuzeichnen, Claude-Funke animiert nur kurz, Systemabfragen im Hintergrund
- Glimmer, Hearths „Dynamic Island“ um die Frontkamera, in jeder App: Musik mit Cover
  und tanzenden Balken, Anrufe und Timer mit laufender Zeit, Navigation, Downloads mit
  Ring, neue Nachrichten, Laden, Lautlos, niedriger Akku; zwei Aktivitäten gleichzeitig
  als Pille + Blase; Tippen klappt auf (mit den Knöpfen der App), lange drücken öffnet
  die App; Schwarz oder Liquid Glass (braucht Bedienungshilfe + Benachrichtigungszugriff)
  - Gesten: nach unten ziehen klappt auf, nach oben schließt, zur Seite wechselt zwischen
    zwei Aktivitäten, die kleine Blase holt die zweite nach vorn; wahlweise öffnet
    Antippen die App wie beim iPhone
  - Neues hüpft kurz auf und schimmert in seiner Farbe (Cover-Farbe, Grün für Anrufe…)
  - Kopfhörer verbunden/getrennt mit Gerätename, „Nicht stören“ an/aus, Akku voll
  - Musik im Stil der Hyper Island von Xiaomi (oder klassisch): klein mit Cover links und
    bunten Wellen rechts in den Farben des Covers, die von beiden Enden durch das Schwarz
    leuchten; aufgeklappt mit wanderndem Farblicht aus dem Cover, großem Cover mit Glow,
    Fortschritt in der Cover-Farbe und Steuerung
  - Musik: Fortschrittsbalken zum Spulen (ziehen oder tippen); pausierte Musik bleibt
    10 Minuten in Glimmer (mit Play-Zeichen), damit sie sich dort wieder starten lässt
  - Schließt du eine App, fließt sie wie bei HarmonyOS in Glimmer: ab Android 11 die App
    selbst (ihr letztes Bild, per Bedienungshilfe nur im Speicher gemerkt, solange eine über
    Hearth geöffnete App vorne ist; geschützte Apps wie Banking ergeben die Farbkarte), sonst
    eine Karte in der Hintergrundfarbe der App (aus ihrem Theme), ihr Icon verschwimmt, sie steigt
    zur Kamera, wird flach wie eine Kapsel und dunkel; die Insel streckt sich ihr entgegen,
    beide verschmelzen über eine flüssige Brücke (Android 12+), dann drückt sich die Insel
    kurz breit und federt zurück (für Apps, die über Hearth geöffnet wurden; abschaltbar)
  - Oder wie bei HyperOS (in Hearth unter „So fliegt sie hinein“): die App bleibt als Ganzes,
    hebt mit weichem Schatten ab, schrumpft in einem schnellen Bogen zur Kamera und wird zu ihrem
    Icon; kurz vor der Ankunft übernimmt Glimmer selbst: die Insel öffnet sich, das Icon sitzt
    einen Moment darin (wie Xiaomis Hyper Island), dann schließt sie sich wieder
  - Auf dem Sperrbildschirm ist Glimmer immer da: die Pille trägt ein Schloss, das beim
    Entsperren aufspringt; Aktivitäten erscheinen dort wie sonst, Antippen klappt auf statt eine
    App zu öffnen; die Mitteilungen dort bleiben beim System
  - Entsperren wie Face ID: wacht das Handy gesperrt auf, wird Glimmer zum abgerundeten
    Quadrat mit Face-ID-Symbol (oder Fingerabdruck), das atmet und sich umsieht; sobald das
    Handy dich erkennt (auch wenn es auf dem Sperrbildschirm bleibt), wird daraus ein grüner
    Haken (findet es niemanden, verschwindet das Symbol still); schlägt Fingerabdruck oder Face ID fehl (der Sperrbildschirm meldet „nicht
    erkannt“, „erneut versuchen“, falsche PIN …), wird das ganze Symbol rot und die Insel
    schüttelt sich wie beim iPhone, bei jedem Versuch neu
  - Always-On-Display: Musik und Aktivitäten bleiben sichtbar, gedimmt und ohne Bewegung
    (sofern das Handy Einblendungen im AOD zulässt)
  - Bewegung wie die Dynamic Island: beim Aufklappen erst in die Breite, dann mit leichtem
    Federn nach unten, beim Zuklappen schneller und ruhiger; Inhalte wachsen von oben heraus
    bzw. sinken in die Kamera zurück; unter dem Finger schwillt die Insel leicht an; wenn nichts
    mehr läuft, zieht sie sich in die Kamera zurück statt einfach zu verschwinden
  - Bleibt nach dem Standby da: Sperrbildschirm, AOD und Fenster werden nach dem Ein- und
    Ausschalten mehrmals neu geprüft, ein verlorenes Fenster wird neu angelegt
  - Zahlen rollen wie beim iPhone (Timer zählen nach unten, Stoppuhr und Anrufe nach oben),
    Icons neuer Aktivitäten springen mit leichtem Überschwingen hinein, Knöpfe geben unter dem
    Finger nach
  - Gestaltung: Leuchtfarbe (Aktivität, Akzent, Weiß, Regenbogen), Rand (dezent oder in Farbe),
    Bewegung (ruhig, normal, verspielt), Breite, Position (links/rechts, oben/unten) – alles
    wirkt sofort, ohne Neustart; „Größe & Position zurücksetzen“ und „Alle Glimmer-Einstellungen
    zurücksetzen“ (mit Bestätigung) bringen alles wieder auf Standard
  - Funktionen: Doppeltippen (Musik, Taschenlampe, Bildschirmfoto), Zuklappen nach 5–30 s oder
    nie, Wischen über Musik wechselt den Titel, Akku beim Laden dauerhaft zeigen, Vibration
  - Modus „Dynamic Island 1:1“: Glimmer wird zur Kopie der iPhone-Insel – reines Schwarz, ihre
    Proportionen (Pille 3,4 : 1, kompakte Aktivitäten 6,2 : 1, aufgeklappt fast bildschirmbreit
    mit 42er-Ecken), Cover als abgerundetes Quadrat, auf dem Sperrbildschirm nur die Pille,
    Antippen öffnet die App, gedrückt halten klappt auf, ohne Glimmers Extras; die eigenen
    Einstellungen bleiben gespeichert
  - Von anderen Inseln übernommen: Bestätigungscodes aus SMS und Apps sitzen in Glimmer, antippen
    kopiert (vivo, OPPO); neue Bildschirmfotos erscheinen als kleines Bild zum Öffnen oder Teilen
    (vivos Origin Island); Musikstil „Fluid Cloud“ mit drehendem Cover, Fortschrittsring und
    rückwärts rollender Restzeit, atmendes Leuchten (OPPO/OnePlus); durch alle Aktivitäten wischen,
    aufgeklappt mit Punkten (Samsungs Now Bar); Aussehen „Farbig getönt“ in der Farbe der Aktivität
  - Gesperrt zeigt Glimmer keine Codes und Nachrichten (ein frischer Code erscheint nach dem
    Entsperren); Antippen und Wischen reagieren auch, während Timer oder Musik laufen
  - Neue Meldungen: Ladekabel getrennt (mit Akkustand), neuer Wecker gestellt (Tag und
    Uhrzeit, der Wecker klingelt kurz)
  - „Kleiner (85 %)“: Glimmer in jedem Zustand 15 % schmaler; Höhe, Inhalt und Schrift bleiben
  - Nichts liegt unter der Frontkamera: Glimmer kennt Lage und Größe des Kameralochs, kompakt
    stehen Inhalte links und rechts davon, aufgeklappt beginnt der Inhalt darunter
  - Live-Vorschau oben in der Glimmer-App: zeigt Aussehen, Breite, Rand, Leuchten, Bewegung und
    Position sofort, wechselt durch ein paar Inhalte, antippen klappt auf; Einstellungen in
    Abschnitten (Gestaltung, Größe & Position, Bedienung, Was Glimmer zeigt, Sperrbildschirm & AOD)
  - Im Vollbild (Videos, Spiele ohne Statusleiste) tritt Glimmer zurück, nur Anruf, Wecker und
    Entsperren kommen durch (als Test, standardmäßig aus, da nicht jedes Handy das zuverlässig meldet)
  - Laden: beim Einstecken springt der Blitz mit einem Lichtblitz herein und ein grüner Streifen
    läuft einmal um die Insel; die Batterie füllt sich von leer bis zum Akkustand, ihre Kante wogt
    wie Flüssigkeit, ein Glanz läuft hindurch, der Blitz darüber pulsiert; schwacher Akku blinkt rot
  - Eigene Bewegung für jeden Hinweis: der Mond geht auf (Nicht stören), das Flugzeug fliegt ein
    (Flugmodus), Kopfhörer springen mit einem Ring auf, voller Akku funkelt, die Glocke schwingt,
    die Taschenlampe leuchtet atmend
  - Stoppuhr und Timer laufen in Glimmer weiter, auch bei der Samsung-Uhr und anderen Uhr-Apps
    (Zeit aus Chronometer oder Text, Stoppuhr mit Zehnteln, angehalten bleibt sie stehen)
  - Bildschirm- und Sprachaufnahmen mit rotem Punkt und laufender Zeit
  - Anrufe wie beim iPhone: Zeit in Grün links, grüne Stimm-Welle rechts; ein eingehender
    Anruf klappt Glimmer sofort auf, mit grünem „Annehmen“ und rotem „Ablehnen“
  - Klingelnder Wecker oder abgelaufener Timer klappt auf (Schlummern / Stopp)
  - Navigation zeigt die nächste Entfernung
  - Taschenlampe an: gelbe Live-Aktivität, antippen und „Ausschalten“ oder gedrückt halten
  - Energiesparmodus und Flugmodus an/aus
  - Live-Updates von Apps (Android 16, wie Live-Aktivitäten: Lieferung, Fahrt…) mit ihrem
    kurzen Text; aktiver Hotspot mit Zahl der Geräte; Kopfhörer-Akku nach dem Verbinden
    („Galaxy Buds 85 %“); laufende Zeiten mit gleich breiten Ziffern
  - Wie die Dynamic Island: Neues taucht weich aus einer Unschärfe auf, die Insel zieht sich
    beim Wechsel kurz zusammen und federt auf, aufgeklappt mit runden 44-dp-Ecken; bei
    „Lautlos“ und „Vibration“ wackelt die Glocke
  - Zweite Aktivität löst sich wie ein Tropfen von der Pille: die Blase gleitet heraus,
    verbunden durch einen flüssigen Hals, der dünner wird und reißt
- Foto-Widget: eigene Bilder als Diashow mit langsamem Zoom auf Glas, für Homescreen
  und Widget-Seite
- Apps frei anordnen: Icon lange drücken und ziehen (Menü verschwindet beim Ziehen),
  Glas-Feld zeigt den Zielplatz, belegte Plätze tauschen, am Rand blättert die Seite,
  ins Dock ziehen und aus dem Dock heraus; „Anordnung zurücksetzen“ in den Einstellungen
- Schneller nach dem Zurückkehren: Icons werden zwischengespeichert und brauchen weniger
  Speicher, damit Android den Launcher seltener schließt und er sofort wieder da ist
- Widgets direkt auf dem Startbildschirm: lange auf eine freie Stelle → „Widget hierher“;
  Widgets belegen Rasterfelder auf Glas, Apps fließen drumherum; Widget lange drücken
  für Größe/Seite/Entfernen, halten und ziehen zum Verschieben
- Animationen: Zoom beim Zurückkehren, Seitenwechsel „Tiefe“ oder „Würfel“, rollende
  Uhrziffern, gestaffelt erscheinende Karten und Ordner, gleitende Icons beim Umordnen,
  Seitenpunkte als Kapsel, Glas-Hinweise mit „Rückgängig“
- Mehr Glas: Glanz auf jedem Icon (folgt dem Kippen), Suchergebnisse auf einer Glasplatte
- Widgets: eigene Widget-Seite links neben dem Homescreen (wie die Heute-Ansicht),
  jedes Widget auf einer Glas-Karte; „Bearbeiten“ zum Verschieben, Vergrößern, Entfernen
- Mehrere Apps markieren: lange drücken → „Auswählen“ (Icons wackeln, Haken antippen),
  dann „Ausblenden“; oder in den Einstellungen „Apps auswählen …“ als Liste
- Schneller zurück in die nächste App: Rückkehr aus einer App scrollt nicht mehr zur
  ersten Seite (das hat den ersten Tipp verschluckt), Apps starten mit Zoom aus dem Icon
- Liquid Glass im Stil von Apple, fast überall: Icons, Dock, Uhr, Seitenpunkte,
  Such-Pille, Suche, Kontextmenüs und Einstellungen
  - Echte Linse (Android 13+): der Rand zieht sichtbar herein, was hinter der Kante liegt,
    die Mitte vergrößert leicht, Farbsäume, Fresnel-Glanz, dünne Lichtkante zum Licht hin,
    weichere auf der Gegenseite, leichter Schatten in der Wölbung
  - „Glas-Qualität“: Flüssig (auch Knöpfe und Schalter) oder Ausgewogen
  - Während Glas sich bewegt (Blättern, Animationen), pausiert die Linse und kommt im
    Stillstand zurück: kein Ruckeln beim Wischen
  - Ohne Hintergrundbild (über anderen Apps, Glimmer): gezeichnete dicke
    Kante mit Glanzpunkt und Schatten
  - Glanzlichter auf der Kante, die beim Kippen des Handys mitwandern
  - Glas wölbt sich unter dem Finger, zieht sich wie ein Tropfen mit und federt zurück
  - Unschärfe und verstärkte Farben des Hintergrundbilds
- Icon-Stile: Original, Glas (Symbol auf Glas), Klar (weiß auf Glas), Getönt
- Icon-Formen: Squircle, Kreis, abgerundetes Quadrat
- Icon-Packs im ADW/Nova-Format, mit Rahmen für Apps, die das Pack nicht abdeckt
- Einstellungen: lange auf eine freie Stelle drücken, oder Zahnrad in der Suche
  - Glas: Lichtbrechung, Unschärfe, Farbsäume, Glanz, Tönung, Glasfarbe, Voreinstellungen
  - Icons: Stil, Form, Farbe, Größe, Beschriftung, Icon-Pack
  - Homescreen: Spalten, Reihen, Uhr (Glas-Karte, groß, aus), Begrüßung, Akku,
    Abdunkeln, Such-Pille, Wischgeste
  - Dock: Anzahl Apps, zurücksetzen
  - Suchmaschine, Design der Suche, Vibration, ausgeblendete Apps
- Lange drücken auf ein Icon: Glas-Menü mit App-Info, Dock hinzufügen/entfernen/verschieben,
  Ausblenden, Deinstallieren
- Suche: nach oben oder unten wischen oder auf "Suchen" tippen
  - Enter startet den ersten Treffer, sonst Websuche
- Home-Taste schließt Suche, Menü und Einstellungen und springt zur ersten Seite

## Liquid Glass und die Berechtigung

Damit das Glas dein Hintergrundbild zeigen kann, muss der Launcher es lesen dürfen.
Ab Android 11 geht das nur über "Zugriff auf alle Dateien". Auf dem Homescreen
erscheint dafür ein Hinweis, ein Tipp öffnet die passende Einstellung.

Ohne Berechtigung oder mit Live-Hintergrund ist das Glas nur milchig getönt.

Effekt je nach Android-Version:
- Android 13+: Unschärfe, Lichtbrechung, Farbaufspaltung
- Android 12: Unschärfe
- Android 9 bis 11: weichgezeichnetes Hintergrundbild

## APK bauen

### Variante A: GitHub Actions (ohne Android Studio)

1. Neues GitHub-Repository anlegen und den Projektordner hochladen
2. GitHub baut die APK bei jedem Push automatisch (Tab "Actions")
3. Jeder erfolgreiche Build erscheint als Release (`build-<Nummer>`) mit `Hearth.apk`, `Glimmer.apk` und `Kontrollzentrum.apk`
   und zusätzlich jede App als eigenes Release ihrer Version: `hearth-v<Version>` („Hearth 5.0“),
   `glimmer-v<Version>` und `kontrollzentrum-v<Version>`, mit `<App>-<Version>.apk`. Alle früheren
   Versionen werden dabei einmalig nachgetragen (Hearth ab 1.0, Glimmer ab 4.0, Kontrollzentrum ab 4.1).
4. Neueste APKs direkt laden: `https://github.com/<Besitzer>/<Repo>/releases/latest/download/Hearth.apk`
   sowie `.../Glimmer.apk` und `.../Kontrollzentrum.apk`

### Variante B: Android Studio

1. Ordner öffnen (File > Open), Gradle-Sync abwarten
2. Build-Variante wählen (`hearthDebug`, `glimmerDebug` oder `controlsDebug`), dann Run oder Build > Build APK(s)

Danach Home-Taste drücken und "Hearth" als Standard-Launcher wählen,
oder: Einstellungen > Apps > Standard-Apps > Startbildschirm-App.
Glimmer und Kontrollzentrum öffnen und dort jeweils die Bedienungshilfe und den Benachrichtigungszugriff einschalten,
und „Akku: Nicht eingeschränkt“ erlauben, damit One UI sie im Standby nicht schlafen legt.

## Struktur

- `data/AppRepository.kt`: lädt Apps über `LauncherApps`, rendert Icons, startet Apps
- `data/WallpaperRepository.kt`: liest das Hintergrundbild für das Glas
- `LauncherViewModel.kt`: App-Liste, Dock, Hintergrund, Home-Events
- `ui/LiquidGlass.kt`: Glas-Effekt (AGSL-Shader für die Lichtbrechung)
- `ui/LauncherScreen.kt`: Homescreen, Seiten, Uhr, Such-Pille
- `ui/SearchOverlay.kt`: Suche
- `ui/AppIcon.kt`: Icon mit Squircle-Form und Long-Press-Menü
- `ui/Dock.kt`: Dock
- `ui/theme/Theme.kt`: Farben (Kalkstein, Walnuss, Ocker)
