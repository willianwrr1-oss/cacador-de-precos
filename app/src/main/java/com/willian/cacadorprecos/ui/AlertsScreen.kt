package com.willian.cacadorprecos.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willian.cacadorprecos.data.AppState
import com.willian.cacadorprecos.data.Fmt
import com.willian.cacadorprecos.data.Repo

@Composable
fun AlertsScreen(state: AppState, onOpen: (String) -> Unit) {
    val naoLidos = state.alerts.count { it.unread }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(
            title = "Alertas",
            subtitle = "Queda de preço, preço-alvo e menor preço registrado.",
            trailing = {
                if (naoLidos > 0) {
                    TextButton(onClick = { Repo.markAlertsRead() }) {
                        Text("Marcar como lidos", color = Pal.Soft, fontWeight = FontWeight.Bold)
                    }
                }
            }
        )
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (state.alerts.isEmpty()) {
                AppCard {
                    Text("Nenhum alerta por enquanto", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Quando um preço cair acima do percentual definido, atingir o preço-alvo ou chegar ao menor valor já visto, o aviso aparece aqui e no celular.",
                        fontSize = 13.sp, color = Pal.Muted, lineHeight = 19.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            state.alerts.forEach { a ->
                val bg = when (a.kind) {
                    "queda" -> Pal.GoodBg
                    "alvo" -> Pal.BlueBg
                    else -> Pal.GoldPill
                }
                val fg = when (a.kind) {
                    "queda" -> Pal.GoodFg
                    "alvo" -> Pal.BlueFg
                    else -> Pal.GoldFg
                }
                AppCard(onClick = { onOpen(a.productId) }, padding = 14.dp) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier.size(40.dp).background(bg, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Notifications, contentDescription = null, tint = fg)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(a.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, lineHeight = 19.sp)
                            Text(a.text, fontSize = 12.sp, color = Pal.Muted, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
                            Text(Fmt.time(a.time), fontSize = 12.sp, color = Pal.Muted, modifier = Modifier.padding(top = 6.dp))
                        }
                        if (a.unread) {
                            Box(Modifier.padding(top = 5.dp).size(10.dp).background(Pal.Teal, CircleShape))
                        }
                    }
                }
            }
        }
    }
}
