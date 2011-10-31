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

The Makefile expects to find the unzipped Scala distribution directory
at /usr/local/scala-2.9.0.1.  You can override this with the
SCALA_HOME environment variable.

## Terms of use

Copyright 2011 by Uri Wilensky.

This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
