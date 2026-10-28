# AllInOne

Root phone toolkit (Samsung A05): Info, build.prop, Backup, Network, Boot/QMG, UI, Fonts/Sounds, Hidden, Modules, Scan, Terminal.

Build: push to GitHub -> Actions -> Artifacts -> AllInOne-debug (APK).

Termux:
  unzip AllInOne.zip && cd AllInOne
  git init && git add . && git commit -m "AllInOne v0.2"
  git branch -M main
  git remote add origin https://github.com/namalxhero/AllInOne.git
  git push -u origin main

Files go in /sdcard/AllInOne/ :
  qmg/*.qmg                -> custom boot animation (install from Boot/QMG tab)
  up_param_custom.img      -> custom up_param (flash from Boot/QMG tab, BACKUP first)
  fonts/*.ttf              -> custom fonts (same file names as /system/fonts)
  sounds/<folder>/*        -> custom sounds (ringtones/notifications/ui/alarms)

Intro music: a built-in tune plays on app start (speaker icon on Home toggles mute).
To use your own song, add it as app/src/main/res/raw/intro.mp3 (lowercase name) and push.
