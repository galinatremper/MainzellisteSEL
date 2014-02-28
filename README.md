# Mainzelliste

Die Mainzelliste ist ein webbasierter Pseudonymisierungsdienst erster Stufe. Sie erlaubt die Erzeugung von Personenidentifikatoren (PID) aus identifizierenden Attributen (IDAT), dank Record-Linkage-Funktionalität auch bei schlechter Qualität identifizierender Daten. Ihre Funktionen werden über eine REST-Schnittstelle bereitgestellt.

Weitere Informationen und Dokumentation zur Mainzelliste finden Sie auf der [Projektseite der Universitätsmedizin Mainz](http://www.mainzelliste.de).

Um immer auf dem aktuellen Stand zu bleiben, registrieren Sie sich auf unserer [Mailingliste](https://lists.uni-mainz.de/sympa/subscribe/mainzelliste).

## Releaseinformationen
Detailliertes Changelog siehe Datei NEWS im Quellcode.

### 1.2
- Sessions verfallen nach einer konfigurierbaren Zeit.
- Erlaubt Felder, die nur gespeichert werden, ohne zum Matching beizutragen.
- Bugfixes (Beiträge von Daniel Volk, Dirk Langner).

Beim Update ist zu beachten, dass in Version 1.2 Sessions standardmäßig nach 10 Minuten Inaktivität gelöscht werden. Falls dies nicht erwünscht ist, ist der Konfigurationsparameter "sessionTimeout" anzupassen.

Das Update nimmt keine Datenbankänderungen vor.

### 1.1
- Umstellung von Abhängigkeitsmanagement und Build auf Maven sowie diverse kleinere Korrekturen.

Allen Benutzern wird das Update auf Version 1.1 empfohlen. Beim ersten Start werden Änderungen an der Datenbank zur Anpassung an die neue Version vorgenommen. Zur Sicherheit empfehlen wir, vor dem Update die Datenbank zu sichern.

Entwickler benötigen Maven-Unterstützung, um das Projekt zu öffnen. Hinweise dazu finden sich in der aktualisierten Entwicklerdokumentation.  

### 1.0
- Erste Veröffentlichung

## Beiträge
Als Communityprojekt lebt die Mainzelliste von den Beiträgen der Forschergemeinschaft. Wir bedanken uns für die Codebeiträge der folgenden Kollegen:

- Maximilian Ataian, Universitätsmedizin Mainz
- Benjamin Gathmann, Universitätsklinikum Freiburg
- Jens Schwanke, Universitätsmedizin Göttingen
- Daniel Volk, Universitätsmedizin Mainz
- Dirk Langner, Universitätsmedizin Greifswald