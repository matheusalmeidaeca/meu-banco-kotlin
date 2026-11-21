# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.meubanco.patrimonio.domain.**$$serializer { *; }
-keepclassmembers class com.meubanco.patrimonio.domain.** { *** Companion; }
-keepclasseswithmembers class com.meubanco.patrimonio.domain.** { kotlinx.serialization.KSerializer serializer(...); }
-keep class com.meubanco.patrimonio.domain.LInt { *; }
# WorkManager e widget são instanciados pelo sistema
-keep class com.meubanco.patrimonio.work.DailyWorker { *; }
-keep class com.meubanco.patrimonio.widget.BalanceWidget { *; }
-dontwarn javax.annotation.**
