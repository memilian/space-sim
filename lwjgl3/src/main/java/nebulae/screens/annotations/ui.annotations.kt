package nebulae.screens.annotations


@Target(AnnotationTarget.FIELD)
annotation class Range(val from: Float, val to: Float, val step: Float)

@Target(AnnotationTarget.FIELD)
annotation class Check()

@Target(AnnotationTarget.FIELD)
annotation class WithName(val name: String)

@Target(AnnotationTarget.FIELD)
annotation class WithDescription(val description: String)

@Target(AnnotationTarget.FIELD)
annotation class ColorPicker()

@Target(AnnotationTarget.FIELD)
annotation class Tab()

@Target(AnnotationTarget.FIELD)
annotation class EnumList<T>()
