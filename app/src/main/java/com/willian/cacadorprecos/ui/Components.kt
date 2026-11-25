package com.willian.cacadorprecos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willian.cacadorprecos.data.Fmt

/** Cores de uma variação de preço: queda (bom), alta (atenção) ou estável. */
data class Tone(val bg: Color, val fg: Color, val line: Color)

fun toneFor(pct: Double?): Tone = when {
    pct == null -> Tone(Pal.FlatBg, Pal.FlatFg, Pal.Muted)
    pct < -0.05 -> Tone(Pal.GoodBg, Pal.GoodFg, Pal.Teal)
    pct > 0.05 -> Tone(Pal.UpBg, Pal.UpFg, Pal.UpLine)
    else -> Tone(Pal.FlatBg, Pal.FlatFg, Pal.Muted)
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    background: Color = Color.White,
    borderColor: Color = Pal.Line,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val click = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Surface(
        modifier = modifier.fillMaxWidth().clip(shape).then(click),
        shape = shape,
        color = background,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(bg, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

/** Faixa azul-marinho do topo de cada tela. */
@Composable
fun Header(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    extra: (@Composable ColumnScope.() -> Unit)? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Pal.Navy)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.size(8.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                if (subtitle != null) {
                    Text(subtitle, color = Pal.Soft, fontSize = 13.sp)
                }
            }
            if (trailing != null) trailing()
        }
        if (extra != null) extra()
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = Pal.Muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp
    )
}

@Composable
fun Rule() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Pal.Line)
    )
}

@Composable
fun StepBtn(label: String, desc: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .size(44.dp)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Pal.Border, shape)
            .clickable(onClickLabel = desc, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StepBtn("−", "Diminuir", onMinus)
        Text(
            value,
            modifier = Modifier.padding(horizontal = 4.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        StepBtn("+", "Aumentar", onPlus)
    }
}

@Composable
fun ChipBtn(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) Pal.Navy else Color.White)
            .border(1.dp, if (selected) Pal.Navy else Pal.Border, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else Pal.Navy,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

/** Escolha do período em que o histórico de preços fica guardado. */
@Composable
fun KeepPicker(selected: Int, onSelect: (Int) -> Unit) {
    Fmt.KEEP.chunked(3).forEach { linha ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            linha.forEach { d ->
                ChipBtn(Fmt.keep(d), selected == d, { onSelect(d) }, Modifier.weight(1f))
            }
            repeat(3 - linha.size) { Spacer(Modifier.weight(1f)) }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = Pal.Muted, lineHeight = 17.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Pal.Teal,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Pal.OffTrack,
                uncheckedBorderColor = Pal.OffTrack
            )
        )
    }
}

/** Mini gráfico da lista de produtos. */
@Composable
fun Sparkline(values: List<Double>, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val mn = values.min()
        val mx = values.max()
        val span = if (mx - mn < 0.0001) 1.0 else mx - mn
        val px = 4.dp.toPx()
        val py = 5.dp.toPx()
        val w = size.width
        val h = size.height
        val path = Path()
        var lx = 0f
        var ly = 0f
        values.forEachIndexed { i, v ->
            val x = px + i * (w - 2 * px) / (values.size - 1)
            val y = (py + (mx - v) / span * (h - 2 * py)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            lx = x
            ly = y
        }
        drawPath(
            path, color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        drawCircle(color, radius = 3.dp.toPx(), center = Offset(lx, ly))
    }
}
