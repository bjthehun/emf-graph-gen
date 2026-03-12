package ecore

class NodeNameGenerator(
    private var prefix: String = "a"
) {
    private var simpleNodeCount: Long = 0
    private var regionCount: Long = 0

    fun setNamePrefix(prefix: String) {
        this.prefix = prefix
        simpleNodeCount = 0
        regionCount = 0
    }

    fun generateSimpleNodeName(): String {
        val counter = simpleNodeCount++
        return "SN|$prefix|$counter"
    }

    fun generateRegionName(): String {
        val counter = regionCount++
        return "RE|$prefix|$counter"
    }
}