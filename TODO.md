Test every thing in game Every feature 

Regel: Jedes Feature 100% selber testen ohne mich zu fragen erst testen (Funktion + alle Settings), dann das naechste anfangen, nur relevantes testen.

1. [x] NoRender erweitert (14 neue Schalter): Boss Bars, Subtitles, Action Bar, Sleep Overlay, Health Bar, Food Bar, Vehicle Health, Saving Icon, Water Overlay, Totem Animation, Hurt Cam, View Bobbing, Weather, Particles. Gametest gruen.

35. [x] Account-Wechsler im Hauptmenue (Button Accounts): Microsoft-Login per Geraetecode (Azure-Client-ID im Screen eintragen), mehrere Accounts speichern (Refresh-Token verschluesselt, config/newbridge-accounts.json), per Klick wechseln ohne Neustart. Gametest gruen (Login selbst nur mit echter ID testbar).36. [x] HighJump repariert (Boost beim Absprung, nicht mehr nur auf dem Boden)
37. [x] Keybind-Fenster: Tab Controls (Vanilla- und Mod-Tasten binden), CheckMeter erweitert (Ground-Check toleranter, Klickrate)
38. [x] Crosshair (frueher CustomCrosshair): Pixel-Editor 15x15 mit Farben, Alpha, Presets (inkl. Mittelpixel), 4 Speicherplaetze, Invert-Modus, Opacity; ersetzt das Vanilla-Crosshair
39. [x] Ignore Friends neben jedem Ignore Teammates (KillAura, AimAssist, TriggerBot, Reach, AutoCrit, ShieldDisable, Fullbox, SwordBot, AutoLog, PlayerAlert)
40. [x] Scaffold: Modus Legit / Rage; Nametags: Distance Scaling (Groesse ~1/Entfernung)
41. [x] Neues Modul Targets (Client): gemeinsames Ignore Teammates / Ignore Friends fuer alle Module; aus = jedes Modul nutzt seine eigenen Schalter
42. [x] Basic/Advanced pro Modul im ClickGui (Zeile "Advanced (N)"), zentrale Tabelle in UI/components/Basics.java, Standard: erste 4 Einstellungen sichtbar
43. [x] AirPlace: Vorschau-Box (Farbe, Fill) wo der Block gesetzt wird
44. [x] Macros sind standardmaessig aus
45. [x] CustomCape spielt animierte GIFs ab (Frames, Delays, Disposal); Gruppen: CustomCape und NoRender jetzt in Visual > Rendering
46. [x] ClickGui: Settings View (Basic/Advanced fuer Einstellungen) und Module View (Essential = nur Alltags-Module in der Liste, All = alle; Suche, Favoriten und eingeschaltete Module bleiben sichtbar; Troll/Dupe/Donut immer komplett); Liste in UI/components/ModuleFilter.java
47. [x] Footer-Zeile unter dem letzten Mod![img.png](img.png)l jeder Kategorie (Panels und Vape): "Show all modules (N more)" / "Show essential modules only" schaltet Module View um
48. [x] Standard-Zustand: alle Module aus (auch ArrayList, Waypoints, PacketSanitizer); nur gespeicherte Config schaltet ein (Profiles bleibt als Hintergrundmodul an)
49. [x] Ignore Teammates / Ignore Friends nur noch im Modul Targets (Client); die Schalter in den einzelnen Modulen und "Count As Teammate" (Friends) sind entfernt; Targets ist immer an
50. [x] Keybind-Fenster: gebundene Module und Controls stehen oben in der Liste (die der gewaehlten Taste ganz oben)
51. [x] TriggerBot: Schalter 1.9 Cooldown (schlaegt bei vollem Cooldown, CPS/Randomize dann ausgeblendet)
52. [x] Abhaengige Einstellungen werden ausgeblendet, wenn sie nichts bewirken (z.B. Static Height bei Random Height, Tracer Width ohne Tracers, WorldColors-Farben ohne Effekt); Regeln in UI/components/Deps.java

53. [DONE] Exclusive: enabling a module switches colliding ones off (Flight/ElytraFly/BoatFly/Glide, BHop/Speed, KillAura/ComboBot, Freecam/Freelook). Groups in UI/components/Exclusive.java.

54. [ ] AntiVanish (Misc, neues Modul + Receive-Hook): findet versteckte Spieler. Tab Tracker (Verlust aus der Tab-Liste ohne Leave-Nachricht, Staff-Rang erhoeht den Score, Leave-Nachrichten werden gelernt), Tab Probe (/msg-Completion gegen Tab-Liste, schaltet sich bei netzwerkweiten Namenslisten ab), Sensors (Sounds, Block-Break/Place, Container, Swings, Dig-Pakete unsichtbarer Entities, ohne eigene Aktionen), Score per noisy-OR mit Zerfall, Zuordnung zum zuletzt verlorenen Spieler, .vanish list|probe|clear|reset. Neu: event/PacketReceiveEvent + mixin/Connection/ConnectionReceiveMixin (channelRead0), EventBus.hasListeners. Unit-Tests (Utils/vanish) und Gametest testAntiVanish sind geschrieben, aber noch NICHT ausgefuehrt - erst ./gradlew test und runClientGameTest, dann [x].
