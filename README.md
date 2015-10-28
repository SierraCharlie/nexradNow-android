# nexradNow-android

This app downloads a Nexrad weather product for your local area and displays it on your Android device. Currently, it uses
the GPS location of your device to find several nearby Nexrad radar stations, downloads recent weather products directly from
the NWS radar operations center, and displays them on your device. A simple state map overlay is generated as a visual reference.

The display is intended to show the products for a broad geographic area (several states), rather than for a single
very local area. It aggregates the data from the various Nexrad stations to achieve this coverage. It can search
for Nexrad stations within up to 500 miles of your current location.

The products currently supported include the base & composite reflectivities at 120 and 240 nm ranges.

You can choose your location from either the device's current GPS location, or you can center on an existing Nexrad site.
If you are feeling adventurous, you can enter a city/state or place name, and the app will use Android's geocoder to
figure out a physical location.

![Sample display of 200-nm range composite radar return](/screenshots/LocalNexradDisplay-p38.png?raw=true "Sample display of 200-nm range composite radar return")

The app continues to evolve - it originally served as a learning exercise for me, but I intend to add features and publish it
to Google Play when it is reasonably complete. Some of the features on the to-do list include:

* animation of weather products when possible

Your suggestions welcome!

[You can download the app from the Google Play "Beta" site] (https://play.google.com/apps/testing/com.nexradnow.android.app)
