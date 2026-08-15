Set WshShell = CreateObject("WScript.Shell") 
WshShell.CurrentDirectory = "C:\Users\abhin\Downloads\FileDashboard\" 
WshShell.Run """C:\Program Files\Eclipse Adoptium\jdk-25.0.4.7-hotspot\bin\javaw.exe"" -jar ""C:\Users\abhin\Downloads\FileDashboard\FileDashboard.jar""", 0, False 
