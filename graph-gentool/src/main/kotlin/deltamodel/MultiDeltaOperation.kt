package deltamodel

abstract class MultiDeltaOperation(id: String):
    DeltaOperation(id) {

    var generated: Boolean = false
}
