# nexradNow-android
This app downloads a Nexrad weather product for your local area and displays it on your Android device. Currently, it uses
the GPS location of your device to find several nearby Nexrad radar stations, downloads recent weather products directly from
the NWS radar operations center, and displays them on your device. A simple state map overlay is generated as a visual reference.

![Sample display of 200-nm range composite radar return](/screenshots/LocalNexradDisplay-p38.png?raw=true "Sample display of 200-nm range composite radar return")

The app continues to evolve - it originally served as a learning exercise for me, but I intend to add features and publish it
to Google Play when it is reasonably complete. Some of the features on the to-do list include:

* display of other selected weather products besides the 200-nm range composite scan
* select a location besides the device's current GPS location for display
* graphics improvements
* stability/logging improvements
* cache NWS download data more effectively to limit the amount of data transfer

Your suggestions welcome!
