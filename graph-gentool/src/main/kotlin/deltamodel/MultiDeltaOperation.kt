package deltamodel

/**
 * A [DeltaOperation] that may produce additional [DeltaOperation]s in order to correctly execute it.
 */
abstract class MultiDeltaOperation(id: String):
    DeltaOperation(id) {

    var generated: Boolean = false
}
