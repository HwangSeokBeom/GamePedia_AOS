# kotlinx-serialization: keep serializers for contract DTOs
-keepclassmembers class com.hwb.gamepedia.core.network.dto.** {
    *** Companion;
}
-keepclasseswithmembers class com.hwb.gamepedia.core.network.dto.** {
    kotlinx.serialization.KSerializer serializer(...);
}
