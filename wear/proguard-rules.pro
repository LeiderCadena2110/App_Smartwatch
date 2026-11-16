# Reglas de R8 para la versión de release.
# Compose, AndroidX, Wear (Tiles, ProtoLayout, complicaciones, Ongoing) y las corrutinas de Kotlin
# traen sus propias reglas dentro de las librerías; aquí solo va lo específico de HidraSmart.

# Conserva archivo y número de línea en los errores para poder leerlos con el mapping.txt
# (cada release de GitHub Actions lo guarda como artefacto).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Las rutas de navegación usan Enum.name y Enum.valueOf (ProfileField): se conservan los nombres
# de los enums de la app para que una ruta guardada en el estado de navegación siga siendo válida.
-keepclassmembers enum co.edu.ucc.hidrasmart.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    **[] $VALUES;
    public *;
}
