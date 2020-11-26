# Main Tasks: to build a practical WSAN that can detect sound events and control necessary actuators, starting with this base code, which is developped based on The Thingy app of Nordic Semiconductor.

![](https://github.com/levietduc/CPS_WSAN_2020/blob/master/CPSWSANProject.png)

In particular, the process of collecting environmental noise, detecting sound events, and sends event information and a control response are likely as the follows:

1. Setup Network Routing:  setup a network ad-hoc routing to send messages from any cluster heads to an end-user smartphone (sink node), as fast and reliable as possible.

2. Network clustering and Sensor Connection: The smartphones scan and select which sensors to connect to ensure evenly distributed, preferably 4 closest sensors.

3. Data collection: Audio data from sensors is streamed to their cluster head (smartphone).

4. Data processing: Audio data from all sensors inside the cluster are processed on smartphones. And then events are detected, given the audio sensors.

5. Actuation: The cluster head (smartphone) sends a control message to the sensor (Thingy) which is likely closest to where the event is, to control the LED as an indication of the detected event.

6. Event Distribution: messages about the detected events are sent to the end-user smartphone (the sink), using the already setup routing path in task 1, and the messages should be displayed on the end-user smartphone (the sink).

# Nordic Thingy
The Thingy app is designed to work with the Nordic Thingy:52™ devices.

Nordic Thingy:52 is a compact, power-optimized, multi-sensor device built around the nRF52832 Bluetooth® 5 SoC from Nordic Semiconductor.

Capture, view, and interact with the sensor data acquired from your Thingy, with no programming skills required. 
Thanks to its simple Bluetooth API and IFTTT™ integration, the possibilities are endless! 

Use this app to connect your mobile device with Thingy and gain access to the following functionalities:
* View the current temperature, humidity, air quality, and atmospheric pressure
* Use it as a compass, step counter, or detect taps on any of its axis
* Check the orientation of your Thingy and get live accelerometer, gyroscope, and magnetometer data at a high rate
* Measure the gravity vector
* Trigger programmable events by pressing the button on top of the device
* Control the color, brightness, and mode of the RGB LED
* Stream PCM audio to its speaker, or play predefined notification sounds when events occur
* Stream audio from Thingy to a mobile device using the built-in microphone
* Play programmable tunes in frequency mode
* Using the free Thingy app, update the firmware on your Thingy as soon as new firmware versions are released

###For firmware and mobile developers:
Thingy can be flashed with custom firmware, just like any other development kit from Nordic Semiconductor. 
The Thingy firmware package contains comprehensive documentation, so you can easily customize it to your own needs.
Additionally, we provide an Android Library on JCenter for your Android development needs. 
These libraries are made exclusively for Thingy, to ensure that installation and mobile development are as smooth as possible!
Learn more about Thingy at http://www.nordicsemi.com/thingy

If you're setting up the Nordic Thingy example app project from GitHub make sure to create your own 
project on the Google Developer Console and enable URLShortener API and use the API key in your project.

### Note:

* Android 7.0 or newer is required.

* Tested on Samsung S8, J7, HTC U11

* Location Services need to be enabled for scanning, runtime permission ACCESS_FINE_LOCATION is also required.
