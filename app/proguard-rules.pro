# Hishab Ledger does not use reflection or serialization libraries (data is read and written with org.json),
# so the default Android rules are enough. Keep line numbers so crash reports stay readable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
