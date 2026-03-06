package deltamodel

/**
 * A [DeltaOperation] that may produce additional [DeltaOperation]s in order to correctly execute it.
 * These operations are created by the [flatten] method.
 */
abstract class MultiDeltaOperation(id: String):
    DeltaOperation(id) {

    var generated: Boolean = false

    /**
     * Computes an estimation of the required number of edit operations
     * _before_ other [DeltaOperation]s are generated.
     * This method must not be called when [generated] is true, as in that case, this operation
     * may already have been [apply]ed, and the estimation only holds before [apply].
     *
     * @return Int
     */
    abstract fun estimateOperations(): Int

    /**
     * For a [deltamodel.MultiDeltaOperation], we compute the atomic edit length with [estimateOperations]
     * before flattening, and with [flatten] after flattening.
     *
     * @return Int
     */
    override fun getAtomicLength(): Int {
        if (!generated) {
            return estimateOperations()
        }
        else {
            return flatten().size
        }
    }
}
