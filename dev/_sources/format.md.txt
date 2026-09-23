# The file format

WPILOG 1.0, as specified in WPILib's
[`datalog.adoc`](https://github.com/wpilibsuite/allwpilib/blob/main/datalog/doc/datalog.adoc):
a 12-byte header, then records whose first byte gives the widths of the entry
ID, payload size and timestamp that follow. Everything is little-endian, and
every field uses as few bytes as its value needs.

Given the same records, `WpiLogWriter` produces **byte-for-byte the same file**
as WPILib's own native writer. `tools/logcheck` checks that on every CI run.

## Poses

Poses are written as WPILib `Pose2d` structs — three little-endian doubles, x,
y and rotation — with the schema entries AdvantageScope needs
(`/.schema/struct:Pose2d` and its parts), exactly as WPILib's `addStructSchema`
writes them.

## A WPILib bug worth knowing

WPILib's **Java** reader, `DataLogIterator.hasNext()`, only reports another
record when 16 or more bytes remain, but a record can be as small as 5 bytes.
A plain `for (DataLogRecord r : reader)` loop therefore drops the last records
of a log. `forEachRemaining()` is correct. This affects anyone reading logs in
Java; AdvantageScope has its own decoder and is unaffected.
