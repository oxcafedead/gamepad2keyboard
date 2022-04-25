javac -d build -cp "lib/*;" .\src\oxcafedead\g2k\*
cd build
jar cmf ..\Manifest.MF g2k.jar oxcafedead\g2k\*
jlink --output jre --compress=2 --no-header-files --no-man-pages --add-modules java.logging --add-modules java.desktop
cp -r ..\lib lib
cp ..\icon.png icon.png
cp ..\default.g2k default.g2k
echo java -Djava.library.path=lib -jar g2k.jar >> startup.bat

launch4jc.exe ..\exe-conf.xml

7z a -tzip gamepad2keyboard.zip jre lib app.exe default.g2k g2k.jar icon.png
7z a -tzip gamepad2keyboard-nojava.zip lib default.g2k g2k.jar icon.png startup.bat