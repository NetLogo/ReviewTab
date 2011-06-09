ifeq ($(origin NETLOGO), undefined)
  NETLOGO=../..
endif

ifeq ($(origin SCALA_HOME), undefined)
  SCALA_HOME=/usr/local/scala-2.9.0.1
endif

SRCS=$(wildcard src/*.scala)

ReviewTab.jar: $(SRCS) manifest.txt
	mkdir -p classes
	$(SCALA_HOME)/bin/scalac -deprecation -unchecked -encoding us-ascii -classpath $(NETLOGO)/NetLogo.jar -d classes $(SRCS)
	jar cmf manifest.txt ReviewTab.jar -C classes .

ReviewTab.zip: ReviewTab.jar
	rm -rf ReviewTab
	mkdir ReviewTab
	cp -rp ReviewTab.jar README.md Makefile manifest.txt src ReviewTab
	zip -rv ReviewTab.zip ReviewTab
	rm -rf ReviewTab
