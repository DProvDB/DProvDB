version := "0.1.0-SNAPSHOT"

scalaVersion := "2.12.2"

//lazy val chorus = ProjectRef(uri("chorus"))
resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"


libraryDependencies +="uvm-plaid" % "chorus" % "0.1.3-SNAPSHOT"
libraryDependencies += "org.apache.commons" % "commons-math3" % "3.6.1"
libraryDependencies += "org.postgresql" % "postgresql" % "42.2.16"

libraryDependencies += "com.google.guava" % "guava" % "28.0-jre"

//libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.10"
//libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.4"

// unit testing with JUnit
libraryDependencies += "com.novocode" % "junit-interface" % "0.8" % "test->default"


libraryDependencies  ++= Seq(
  // Last stable release
  "org.scalanlp" %% "breeze" % "2.0.1-RC1",

  // The visualization library is distributed separately as well.
  // It depends on LGPL code
  "org.scalanlp" %% "breeze-viz" % "2.0.1-RC1"
)

logLevel := Level.Warn

