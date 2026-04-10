# LearnMart ProGuard Rules

# Keep Room entities
-keep class com.learnmart.app.data.local.entity.** { *; }

# Keep SQLDelight generated code
-keep class com.learnmart.app.db.** { *; }

# Keep Hilt generated code
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Keep enum values
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
