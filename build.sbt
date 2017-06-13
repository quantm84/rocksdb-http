lazy val root = Project("rocksdb-http", file("."))
  .enablePlugins(JavaAppPackaging)
  .settings(
    organization := "rocksdb-http",
    version := "1.0-SNAPSHOT",
    scalaVersion := "2.11.11",
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-http-core" % "10.0.7",
      "org.rocksdb" % "rocksdbjni" % "5.4.5"
    )
  )
