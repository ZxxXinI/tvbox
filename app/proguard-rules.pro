# Keep Retrofit service method signatures and annotations for runtime request creation.
-keepattributes Signature,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# App models are serialized through kotlinx.serialization generated serializers.
# Keep companion serializer lookup stable after R8 optimization.
-keepclassmembers class com.tvbox.app.data.** {
    public static ** Companion;
}
