package com.willian.cacadorprecos.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Gráfico das últimas leituras, com a linha tracejada do menor valor registrado. */
@Composable
fun PriceChart(values: List<Double>, lowest: Double?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val todos = values + listOfNotNull(lowest)
        val mn = todos.min()
        val mx = todos.max()
        val folga = if (mx - mn < 0.0001) 10.0 else (mx - mn) * 0.2
        val lo = mn - folga
        val hi = mx + folga
        val w = size.width
        val h = size.height
        val px = 10.dp.toPx()
        val py = 14.dp.toPx()

        // grade
        val grade = Color(0xFFEEF1F4)
        for (f in listOf(0f, 0.5f, 1f)) {
            val y = py + f * (h - 2 * py)
            drawLine(grade, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
        }

        fun xOf(i: Int): Float =
            if (values.size == 1) w / 2f else px + i * (w - 2 * px) / (values.size - 1)

        fun yOf(v: Double): Float = (py + (hi - v) / (hi - lo) * (h - 2 * py)).toFloat()

        // menor valor registrado
        if (lowest != null) {
            val y = yOf(lowest)
            drawLine(
                Pal.GoldLine, Offset(0f, y), Offset(w, y),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            )
        }

        // área e linha
        val line = Path()
        val area = Path()
        values.forEachIndexed { i, v ->
            val x = xOf(i)
            val y = yOf(v)
            if (i == 0) {
                line.moveTo(x, y)
                area.moveTo(x, h - py)
                area.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(xOf(values.size - 1), h - py)
        area.close()
        drawPath(area, Pal.Teal.copy(alpha = 0.12f))
        drawPath(
            line, Pal.Teal,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // ponto atual
        val cx = xOf(values.size - 1)
        val cy = yOf(values.last())
        drawCircle(Color.White, radius = 7.dp.toPx(), center = Offset(cx, cy))
        drawCircle(Pal.Teal, radius = 5.dp.toPx(), center = Offset(cx, cy))
    }
}
