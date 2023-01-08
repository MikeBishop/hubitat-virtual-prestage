# Virtual Prestaging for Hubitat

Prestaging is a handy capability -- you can set the level for a light to use
before it's turned on. Unfortunately, level prestaging is not a feature of
Zigbee and there is not currently a path toward native support in Hubitat
drivers. The situation is even bleaker for CT and RGB capabilities.

The generally recommended solution is a RM rule for each target device to apply
the desired configuration when it turns on, but if you have many devices, this
is both tedious to configure and troublesome to update.

This app attempts to patch that hole. Select a primary (likely virtual) device
that has the desired values, along with a set of secondary devices you want to
follow it. When the primary device changes, all the secondary devices which are
on will be updated to follow it. When a secondary device turns on, the selected
properties from the primary device will be sent to it immediately.

If the target device supports the new LevelPreset capability, levels will be
preset to it immediately, regardless of switch state. If more prestaging
capabilities are defined, the app will be updated to use them.

# Change Log

* [1/7/2023]   Initial release
