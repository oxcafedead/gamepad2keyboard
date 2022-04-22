javac -d build -cp "lib/*;" .\src\oxcafedead\g2k\*
cd build
jar cmf ..\Manifest.MF g2k.jar oxcafedead\g2k\*
jlink --output jre --compress=2 --no-header-files --no-man-pages --add-modules java.logging --add-modules java.desktop
cp -r ..\lib lib
cp ..\default.g2k default.g2k
echo java -Djava.library.path=lib -jar g2k.jar >> startup.bat
