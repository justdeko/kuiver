package com.dk.kuiver.sample

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.dk.kuiver.model.Kuiver
import com.dk.kuiver.model.KuiverNode
import com.dk.kuiver.model.buildKuiver
import com.dk.kuiver.model.buildKuiverWithClassifiedEdges
import com.dk.kuiver.model.edge
import com.dk.kuiver.model.layout.LayoutConfig
import com.dk.kuiver.model.layout.LayoutDirection
import com.dk.kuiver.model.layout.LayoutProvider
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Who handles a step of the order. Sets the color and shape of the step icon.
 */
internal enum class StepKind { CUSTOMER, PAYMENT, WAREHOUSE, SHIPPING }

/**
 * State of a step while the order plays out.
 */
internal enum class StepStatus(val label: String) {
    WAITING("Waiting"),
    ACTIVE("In progress"),
    RETRYING("Retrying"),
    DONE("Done"),
    FAILED("Needs attention")
}

/**
 * Application data of one step, looked up by node id.
 *
 * @property facts short key and value pairs shown when the step is expanded
 * @property events tracking events shown when the step is expanded, as time and text
 */
internal data class StepSpec(
    val title: String,
    val subtitle: String,
    val kind: StepKind,
    val icon: ImageVector,
    val facts: List<Pair<String, String>>,
    val events: List<Pair<String, String>>
)

/**
 * Application data of one edge. Edges whose ends are not both in the graph are left out.
 *
 * @property loop whether the edge closes a cycle, routed over the nodes
 * @property labelAt where the label sits along the edge, 0 at the start and 1 at the end
 */
internal data class EdgeSpec(
    val from: String,
    val to: String,
    val label: String? = null,
    val id: String = "$from->$to",
    val loop: Boolean = false,
    val labelAt: Float = 0.5f
)

/**
 * A step added on demand, wired in between [after] and [before].
 */
internal data class ExtraStep(val spec: StepSpec, val after: String, val before: String)

/**
 * Layouts the showcase switches between.
 */
internal enum class ShowcaseLayout(val label: String) {
    FLOW("Flow"),
    STACK("Stack"),
    ORGANIC("Organic"),
    ORBIT("Orbit")
}

/**
 * Edge shapes the showcase switches between.
 */
internal enum class ShowcaseEdgeStyle(val label: String) {
    CURVES("Curves"),
    CIRCUIT("Circuit"),
    STRAIGHT("Straight")
}

/**
 * Steps of the order, in the order the tour adds them.
 */
internal val OrderSteps: Map<String, StepSpec> = linkedMapOf(
    "order" to StepSpec(
        "Order placed", "#4821 · 3 items", StepKind.CUSTOMER, Icons.Filled.ShoppingCart,
        listOf("Total" to "€84.90", "Items" to "3"),
        listOf("09:02" to "Order received", "09:02" to "Confirmation sent")
    ),
    "payment" to StepSpec(
        "Payment", "Visa ••42", StepKind.PAYMENT, Icons.Filled.CreditCard,
        listOf("Amount" to "€84.90", "Attempts" to "2"),
        listOf("09:03" to "Card declined", "09:05" to "Payment approved")
    ),
    "stock" to StepSpec(
        "Stock check", "Berlin · BER-1", StepKind.WAREHOUSE, Icons.Filled.Inventory,
        listOf("In stock" to "3 of 3", "Shelf" to "B-14"),
        listOf("09:03" to "Items reserved", "09:04" to "Pick list created")
    ),
    "receipt" to StepSpec(
        "Receipt", "PDF · Email", StepKind.CUSTOMER, Icons.AutoMirrored.Filled.ReceiptLong,
        listOf("To" to "anna@mail.de", "Format" to "PDF"),
        listOf("09:06" to "Receipt generated", "09:06" to "Email delivered")
    ),
    "pack" to StepSpec(
        "Pick & pack", "2 boxes · 1.8 kg", StepKind.WAREHOUSE, Icons.Filled.Inventory2,
        listOf("Boxes" to "2", "Weight" to "1.8 kg"),
        listOf("10:18" to "3 items picked", "10:31" to "Labels printed")
    ),
    "hub" to StepSpec(
        "Sorting hub", "Leipzig", StepKind.SHIPPING, Icons.Filled.Warehouse,
        listOf("Carrier" to "DHL", "Route" to "LE-07"),
        listOf("18:40" to "Arrived at hub", "22:15" to "Sorted for delivery")
    ),
    "notify" to StepSpec(
        "SMS updates", "ETA 14:00", StepKind.CUSTOMER, Icons.Filled.Sms,
        listOf("Phone" to "•••• 381", "Sent" to "3"),
        listOf("07:30" to "Arriving today", "13:40" to "Driver 5 stops away")
    ),
    "courier" to StepSpec(
        "Out for delivery", "Stop 12/40", StepKind.SHIPPING, Icons.Filled.LocalShipping,
        listOf("Driver" to "Mira", "Van" to "LE-7341"),
        listOf("14:12" to "Recipient not home", "Next day" to "Redelivery scheduled")
    ),
    "delivered" to StepSpec(
        "Delivered", "Front door", StepKind.CUSTOMER, Icons.Filled.Home,
        listOf("Signed" to "A. Weber", "Photo" to "Attached"),
        listOf("14:05" to "Handed over", "14:06" to "Rating requested")
    )
)

/**
 * Steps added on demand, in the order the add button uses them.
 */
internal val ExtraSteps: Map<String, ExtraStep> = linkedMapOf(
    "gift" to ExtraStep(
        StepSpec(
            "Gift wrap", "Red · Card", StepKind.WAREHOUSE, Icons.Filled.CardGiftcard,
            listOf("Paper" to "Red", "Card" to "Included"),
            listOf("10:20" to "Wrapped", "10:21" to "Card added")
        ),
        after = "pack", before = "hub"
    ),
    "quality" to ExtraStep(
        StepSpec(
            "Quality check", "Sample 1/3", StepKind.WAREHOUSE, Icons.AutoMirrored.Filled.FactCheck,
            listOf("Checked" to "1 of 3", "Result" to "Passed"),
            listOf("10:19" to "Sample taken", "10:20" to "Passed")
        ),
        after = "pack", before = "hub"
    ),
    "insurance" to ExtraStep(
        StepSpec(
            "Insurance", "Cover €500", StepKind.PAYMENT, Icons.Filled.Shield,
            listOf("Cover" to "€500", "Fee" to "€2.40"),
            listOf("10:32" to "Policy issued", "10:32" to "Added to parcel")
        ),
        after = "pack", before = "hub"
    ),
    "customs" to ExtraStep(
        StepSpec(
            "Customs", "CN22 · Non-EU", StepKind.SHIPPING, Icons.Filled.Public,
            listOf("Form" to "CN22", "Duty" to "€0.00"),
            listOf("10:33" to "CN22 filed", "16:10" to "Cleared")
        ),
        after = "pack", before = "hub"
    )
)

/**
 * Every step the showcase knows about.
 */
internal val AllSteps: Map<String, StepSpec> = OrderSteps + ExtraSteps.mapValues { it.value.spec }

/**
 * Edges of the order. The two packing to hub edges, one per box, fan out, payment retries itself and a failed
 * delivery goes back to the hub.
 */
internal val OrderEdges: List<EdgeSpec> = listOf(
    EdgeSpec("order", "payment"),
    EdgeSpec("order", "stock"),
    EdgeSpec("payment", "payment", "retry", loop = true),
    EdgeSpec("payment", "receipt"),
    EdgeSpec("payment", "pack", "paid"),
    EdgeSpec("stock", "pack", "reserved"),
    EdgeSpec("pack", "hub", "box 1", labelAt = 0.3f),
    EdgeSpec("pack", "hub", "box 2", id = "pack->hub#2", labelAt = 0.7f),
    EdgeSpec("hub", "notify", "ETA"),
    EdgeSpec("hub", "courier"),
    EdgeSpec("courier", "hub", "not home", loop = true),
    EdgeSpec("courier", "delivered", "signed")
)

/**
 * Edges of one extra step.
 */
internal fun extraStepEdges(stepId: String): List<EdgeSpec> {
    val extra = ExtraSteps.getValue(stepId)
    return listOf(EdgeSpec(extra.after, stepId), EdgeSpec(stepId, extra.before))
}

/**
 * Builds the graph for the visible steps, with ports matching the flow of the layout.
 *
 * Forward edges leave the node on the side facing the flow, loops use the top (or right) side,
 * layouts without a flow direction use no anchors.
 */
internal fun buildShowcaseGraph(visibleIds: List<String>, layout: ShowcaseLayout): Kuiver {
    val visible = visibleIds.toSet()
    // Loops go in last so the edge classification agrees with them
    val specs = (OrderEdges + visibleIds.filter { it in ExtraSteps }.flatMap(::extraStepEdges))
        .filter { it.from in visible && it.to in visible }
        .sortedBy { it.loop }

    return buildKuiver {
        visibleIds.forEach { addNode(KuiverNode(it)) }
        specs.forEach { spec ->
            val (fromAnchor, toAnchor) = when {
                spec.from == spec.to -> null to null
                layout == ShowcaseLayout.FLOW && spec.loop -> "top" to "top"
                layout == ShowcaseLayout.FLOW -> "right" to "left"
                layout == ShowcaseLayout.STACK && spec.loop -> "right" to "right"
                layout == ShowcaseLayout.STACK -> "bottom" to "top"
                else -> null to null
            }
            edge(spec.from, spec.to, fromAnchor = fromAnchor, toAnchor = toAnchor, id = spec.id)
        }
    }
}

/**
 * Every edge the showcase knows about, by edge id.
 */
internal val EdgeSpecs: Map<String, EdgeSpec> =
    (OrderEdges + ExtraSteps.keys.flatMap(::extraStepEdges)).associateBy { it.id }

/**
 * Circular layout: every node on one ring, in breadth-first order from the roots so that
 * neighbours sit next to each other.
 */
internal val CircularLayout: LayoutProvider = { kuiver, config ->
    val ids = kuiver.nodes.keys.toList()
    val roots = ids.filter { id -> kuiver.edges.none { it.toId == id && it.fromId != id } }
        .ifEmpty { ids.take(1) }

    val order = roots.toMutableList()
    var index = 0
    while (index < order.size) {
        val id = order[index++]
        kuiver.edges.filter { it.fromId == id && it.toId !in order }.forEach { order += it.toId }
    }
    order += ids.filter { it !in order }

    val radius = maxOf(MinOrbitRadius, NodeArc * order.size / (2f * PI.toFloat()))
    val center = DpOffset(config.width / 2, config.height / 2)
    val positioned = order.mapIndexed { position, id ->
        val angle = 2f * PI.toFloat() * position / order.size - PI.toFloat()
        kuiver.nodes.getValue(id).copy(
            position = DpOffset(center.x + radius * cos(angle), center.y + radius * sin(angle) * 0.8f)
        )
    }

    buildKuiverWithClassifiedEdges(positioned, kuiver.edges)
}

/**
 * Smallest ring radius of [CircularLayout].
 */
private val MinOrbitRadius = 260.dp

/**
 * Room one node takes up along the ring of [CircularLayout].
 */
private val NodeArc = 290.dp

/**
 * Layout config of a showcase layout.
 */
internal fun ShowcaseLayout.config(): LayoutConfig = when (this) {
    ShowcaseLayout.FLOW -> LayoutConfig.Hierarchical(
        direction = LayoutDirection.HORIZONTAL,
        levelSpacing = 270.dp,
        nodeSpacing = 120.dp
    )

    ShowcaseLayout.STACK -> LayoutConfig.Hierarchical(
        direction = LayoutDirection.VERTICAL,
        levelSpacing = 140.dp,
        nodeSpacing = 240.dp
    )

    ShowcaseLayout.ORGANIC -> LayoutConfig.ForceDirected(
        iterations = 300,
        repulsionStrength = 900f
    )

    ShowcaseLayout.ORBIT -> LayoutConfig.Custom(provider = CircularLayout)
}
