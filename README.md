# Virtual Prestaging for Hubitat

Prestaging is a handy capability -- you can set the level for a light to use
before it's turned on. Unfortunately, support for level prestaging is
inconsistent across drivers. Worse, color temperature and RGB prestaging is
still undefined (though promised in the future).

This app attempts to patch that hole. Select a primary (likely virtual) device
that reflects the preset values, along with a set of secondary devices you want
to follow it. When the primary device changes, all the secondary devices which
are on will be updated to follow it. When a secondary device turns on, the
selected properties from the primary device will be sent to it immediately.

Of course, if the target device supports the new LevelPreset capability,
levels will be preset to it immediately, regardless of switch state. As more
prestaging capabilities are defined, the app will be updated to use them.

# Change Log

* [1/7/2023]   Initial release
