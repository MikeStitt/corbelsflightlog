# Vendored WPILib log reader (tests only)

`DataLogReader.java`, `DataLogRecord.java` and `DataLogIterator.java` are
copied **unmodified** from WPILib release **v2026.2.2**
(`allwpilib/wpiutil/src/main/java/edu/wpi/first/util/datalog/`), under WPILib's
BSD license in `LICENSE-WPILIB.md`.

They let the logging tests read our `.wpilog` files back with WPILib's own
reader, so the tests check our files against WPILib's definition of the format
rather than our own understanding of it.

Why copied instead of a Gradle dependency: WPILib publishes these in a jar on
its own Maven server, alongside native libraries an FTC build doesn't need.
These three classes use only the Java standard library, so copying them keeps
the test build simple and offline. They live under `src/test`, so they are
never installed on the robot.

To update: replace the three files from a newer WPILib release tag. (WPILib's
`main` branch has moved them to an `org.wpilib` package for 2027.)
