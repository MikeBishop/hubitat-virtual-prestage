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

If true prestaging capabilities are defined for Hubitat in the future, the app
will be updated to use them.

# Contained Motion Zones

It's a common problem in home automation that people are in a room, even if
still. One frequent solution, particularly in rooms that are normally closed, is
to leverage contact sensors. If motion happened in a room and has stopped, but
the doors are still closed, the room is still occupied.

Another common workaround is contact sensors that trigger from pressure,
enabling detection of a person in a bed or chair.

This app takes a few basic inputs -- motion sensors, contact sensors that define
a boundary, and contact sensors that reflect presence -- to power a virtual
motion sensor. While the boundary contact sensors remain closed, any indication
of presence causes the output to show activity. Only when the boundary opens and
there's no continued indication of presence does the output go inactive.

# Change Log

* [1/7/2023]   Initial release of Virtual Prestaging
* [4/13/2023]  Assorted improvements to Virtual Prestaging
* [6/26/2023]  Initial release of Contained Motion
