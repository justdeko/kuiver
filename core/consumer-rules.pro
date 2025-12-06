# Keep EdgeType enum for valueOf() in KuiverSaver
-keep enum com.dk.kuiver.model.EdgeType {
    *;
}

# Keep all model classes used in Compose Saver (prevents type casting issues)
-keep class com.dk.kuiver.model.** { *; }

# If using R8 full mode, also keep compose Saver implementations
-keep class com.dk.kuiver.** implements androidx.compose.runtime.saveable.Saver { *; }
