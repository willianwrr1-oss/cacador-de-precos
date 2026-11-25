package com.willian.cacadorprecos.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.willian.cacadorprecos.BuildConfig
import com.willian.cacadorprecos.data.AppState
import com.willian.cacadorprecos.data.Fmt
import com.willian.cacadorprecos.data.Repo
import com.willian.cacadorprecos.work.Scheduler

@Composable
fun SettingsScreen(state: AppState) {
    val ctx = LocalContext.current
    val s = state.settings
    var sobre by remember { mutableStateOf(false) }
    var limpar by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header(title = "Ajustes", subtitle = "Intervalo de verificação, alertas e histórico.")
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // ---- intervalo ----
            AppCard {
                Text("Intervalo de verificação", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "De quanto em quanto tempo o app procura preços. Agora: a cada " + Fmt.interval(s.intervalMin) + ".",
                    fontSize = 12.sp, color = Pal.Muted, lineHeight = 18.sp, modifier = Modifier.padding(top = 2.dp)
                )
                Spacer(Modifier.height(12.dp))
                Scheduler.INTERVALS.chunked(3).forEach { linha ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        linha.forEach { m ->
                            ChipBtn(
                                label = "A cada " + Fmt.interval(m),
                                selected = s.intervalMin == m,
                                onClick = {
                                    Repo.setSettings { it.copy(intervalMin = m) }
                                    Scheduler.schedule(ctx, m)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "O Android não permite verificar em menos de 15 minutos e pode atrasar a execução para poupar bateria, principalmente com o celular parado por muito tempo.",
                    fontSize = 12.sp, color = Pal.Muted, lineHeight = 18.sp
                )
            }

            // ---- alerta padrão ----
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Alerta padrão", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Para novos produtos", fontSize = 12.sp, color = Pal.Muted)
                    }
                    Stepper(
                        s.defaultPct.toString() + "%",
                        onMinus = { Repo.setSettings { it.copy(defaultPct = (it.defaultPct - 1).coerceAtLeast(1)) } },
                        onPlus = { Repo.setSettings { it.copy(defaultPct = (it.defaultPct + 1).coerceAtMost(50)) } }
                    )
                }
            }

            // ---- notificações ----
            AppCard(padding = 16.dp) {
                SwitchRow(
                    "Notificações", "Aviso na barra do celular com vibração de 3 segundos", s.notifications,
                    { on -> Repo.setSettings { it.copy(notifications = on) } }
                )
                Rule()
                SwitchRow(
                    "Silenciar à noite (opcional)", "Desligado por padrão. Ligado, não avisa das 22:00 às 07:00", s.quiet,
                    { on -> Repo.setSettings { it.copy(quiet = on) } }
                )
            }

            // ---- histórico ----
            AppCard {
                Text("Histórico guardado", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Cada produto guarda as leituras do período escolhido no cadastro (de 7 dias a 1 ano) e, à parte, o menor valor já verificado, com a loja e a data.",
                    fontSize = 13.sp, color = Pal.FlatFg, lineHeight = 19.sp, modifier = Modifier.padding(top = 6.dp)
                )
                TextButton(onClick = { limpar = true }) {
                    Text("Limpar histórico de todos os produtos", color = Pal.Danger, fontWeight = FontWeight.Bold)
                }
            }

            // ---- proprietário ----
            AppCard(onClick = { sobre = true }) {
                Text("Sobre e informações do desenvolvedor", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "Versão " + BuildConfig.VERSION_NAME + " · Willian R Rocha",
                    fontSize = 13.sp, color = Pal.Muted, modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }

    if (sobre) AboutDialog(onClose = { sobre = false })

    if (limpar) {
        AlertDialog(
            onDismissRequest = { limpar = false },
            title = { Text("Limpar histórico?") },
            text = { Text("As leituras, os menores valores e os alertas de todos os produtos serão apagados. Os produtos e os links continuam cadastrados.") },
            confirmButton = {
                TextButton(onClick = {
                    Repo.clearHistory()
                    limpar = false
                }) { Text("Limpar", color = Pal.Danger) }
            },
            dismissButton = { TextButton(onClick = { limpar = false }) { Text("Cancelar") } }
        )
    }
}
