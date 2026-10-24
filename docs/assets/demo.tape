# Records docs/assets/demo.gif. Run from the repository root after ./mvnw package:
#   vhs docs/assets/demo.tape
Output docs/assets/demo.gif
Require java
Set Shell bash
Set FontSize 15
Set Width 1100
Set Height 860
Set Padding 24
Set TypingSpeed 35ms
Set Theme { "name": "VetGreen", "black": "#1B4332", "red": "#F4A6A6", "green": "#A8D5BA", "yellow": "#F6E3A1", "blue": "#9CC9E6", "magenta": "#D7B8E8", "cyan": "#A6E3D7", "white": "#D8F3DC", "brightBlack": "#52796F", "brightRed": "#F7B9B9", "brightGreen": "#B7E4C7", "brightYellow": "#F9EBB8", "brightBlue": "#B3D7EE", "brightMagenta": "#E2CCEF", "brightCyan": "#BDEBE2", "brightWhite": "#FFFFFF", "background": "#1B4332", "foreground": "#D8F3DC", "selection": "#2D6A4F", "cursor": "#A8D5BA" }

Hide
Type "export PS1='$ ' VETCARE_DATA_FILE=/tmp/vetcare-demo.json && rm -f /tmp/vetcare-demo.json*" Enter
Type "printf '17\nDr. Ana\n1\nMarina Costa\n+1 202-555-0142\n\n2\n1\nThor\n1\n2020-03-10\n\n2\n1\nLuna\n2\n2022-07-01\n\n3\n2\n1\n2026-11-03 10:00\n30\n1\n\n\n0\n' | java -jar target/vetcare-scheduler.jar > /dev/null && clear" Enter
Sleep 2s
Show

Type "java -jar target/vetcare-scheduler.jar" Sleep 400ms Enter
Sleep 1.5s
# Luna at 10:15 overlaps Thor's 10:00-10:30 slot with Dr. Ana
Type "3" Enter Sleep 600ms
Type "1" Enter Sleep 400ms
Type "1" Enter Sleep 300ms
Type "2026-11-03 10:15" Enter Sleep 300ms
Type "30" Enter Sleep 300ms
Type "2" Enter Sleep 300ms
Type "1" Enter Sleep 300ms
Enter
Sleep 3s
# Same booking at 10:30 is accepted, and breathing difficulty makes it an emergency
Type "3" Enter Sleep 600ms
Type "1" Enter Sleep 400ms
Type "1" Enter Sleep 300ms
Type "2026-11-03 10:30" Enter Sleep 300ms
Type "30" Enter Sleep 300ms
Type "2" Enter Sleep 300ms
Type "1" Enter Sleep 300ms
Enter
Sleep 3s
# Triage queue: the emergency comes first even though it starts later
Type "15" Enter Sleep 500ms
Type "2026-11-03" Enter
Sleep 4.5s
Type "0" Enter
Sleep 1.5s
