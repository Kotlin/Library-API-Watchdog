// RUN_PIPELINE_TILL: FRONTEND
// EXPLICIT_API_MODE: WARNING
// DIAGNOSTICS: -STATEFUL_CLASS_WITHOUT_EQUALS -STATEFUL_CLASS_WITHOUT_HASH_CODE -STATEFUL_CLASS_WITHOUT_TO_STRING -UNDOCUMENTED_PUBLIC_API -OPEN_API_WITHOUT_SUBCLASS_OPT_IN -TOP_LEVEL_API_WITHOUT_JVM_NAME

package foo.bar

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
public annotation class TreeDsl

public open class Tag

// An explicit accessor takes the type of the property. The marker is reported one time.

public var current: <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> Tag
    get() = Tag()
    set(value) {}

public val computed: <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> Tag
    get() = Tag()

public class Holder {
    public var member: <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> Tag
        get() = Tag()
        set(value) {}
}

// A marker that an accessor declares is reported at the accessor.

public var explicitGetter: Tag
    get(): <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> Tag = Tag()
    set(value: <!DSL_MARKER_NOOP_TYPE_POSITION!>@TreeDsl<!> Tag) {}
