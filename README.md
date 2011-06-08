# NetLogo plugin: Review tab

Adds a fourth tab, "Review", for recording and replaying model runs.

Requires NetLogo 5.0beta4 (coming soon).

## Building

Move the ReviewTab directory to your 5.0beta4 plugins directory.

Change to the ReviewTab directory and run `make`.

If compilation succeeds, `ReviewTab.jar` will be created.

## Using

After building, restart NetLogo and the new tab should appear.

## About the source code

BehaviorSpace is written in the Scala programming language. Scala code
compiles to Java byte code and is fully interoperable with Java and
other JVM languages.

Make sure you are using Scala 2.9.0.1.

The build script expects to find the unzipped Scala distribution
directory at /usr/local/scala-2.9.0.1.  If you have it in another
location, edit the script to point the location you are using.

## Terms of Use

All contents © 2011 Uri Wilensky.

ReviewTab is free and open source software. You can redistribute
it and/or modify it under the terms of the GNU Lesser General Public
License (LGPL) as published by the Free Software Foundation, either
version 3 of the License, or (at your option) any later version.

A copy of the LGPL is included in the NetLogo distribution. See also
http://www.gnu.org/licenses/ .
