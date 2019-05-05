# Purpose

This application gets my phone to send notifications to my [vortex manipulator](https://github.com/RogerParkinson/vortex-manipulator)
The Vortex Manipulator is sort of a smart watch, it sits on my wrist and provides a touch screen and various apps. One of the apps
provides a list of incoming messages. This project runs on my phone and passes the notifications to the Bluetooth interface.

It has a fairly crude UI so I can identify the actual Bluetooth device.

# Use

Just build it using Android Studio and run it on your phone. I'm testing on a Galaxy S4 which is running API 21. 
More modern devices are likely compatible with that. At the other end the [Bluetooth device](https://www.aliexpress.com/item/AT-09-Android-IOS-BLE-4-0-Bluetooth-module-for-arduino-CC2540-CC2541-Serial-Wireless-Module/32820135156.html?spm=a2g0s.9042311.0.0.34734c4deRxuhY) 
is a CC2540 based device sometimes called an AT-05 or a BT-05. It is a BLE (low energy) device. But this code also handles non-BLE devices too.
I tested it with [this device](https://www.aliexpress.com/item/1pcs-HC-06-Bluetooth-serial-pass-through-module-wireless-serial-communication-from-machine-Wireless-HC06-for/32895745707.html?spm=a2g0s.9042311.0.0.27424c4dDP5zDZ)
which is often referred to as an HC-06. For the BLE device you don't need to pair first, but the non-BLE does need to be paired. This app doesn't help you pair, so just use your usual Bluetooth utilities.

Both devices, the BT-05 and the HC-06 present a serial interface to the Vortex Manipulator, which means the code at that end is fairly simple and does not change at all if you switch between them.
The only difference is that BLE devices don't like sending long messages so for those the messages are chunked into shorter messages with a terminator character on the end.

The application is in three parts. The UI part which is the most obvious. Then there are two Android services that run in the background. 
One is the Notification handler, which listens for all the notifications that arrive at the phone, and puts them in a queue for the bluetooth service.

There actually two bluetooth services, and we choose one of them depending on whether the selected device is BLE or not. The code in each is quite different though they need to achieve
the same thing. Either way the bluetooth service waits for something in the queue and sends it to the device. Because of the queue the services all run in the same memory space, so it
is really one service with two threads, which is why they both have the same id.

<img src="readme/screen1.png?resize=200%2C159" style="width: 200px;"/>
![](file:///readme/screen1.png | width=100)

This is what the initial screen looks like. The two menu icons connect and disconnect respectively. When you press the connect button you'll see a list of paired devices and scanned
BLE devices. It looks like this.

<img src="readme/screen2.png?resize=200%2C159" style="width: 200px;"/>
    
Pick one of those and look at the status message at the bottom which will, hopefully, tell you it is connected. At that point you can disconnect or you can send it a test message
using the text widget.

There is one gotcha in all this, you need to tell Android that the app is allowed to recieve notifications. Depending on your Android version it looks something like this:

Settings->My Device->Sounds and Notifications->Notification access

You'll see a list of apps including NotificationService with a check box beside it. Turn it on and OK past the popup and you are good to go.  